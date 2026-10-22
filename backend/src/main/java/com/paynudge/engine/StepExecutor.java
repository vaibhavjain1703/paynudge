package com.paynudge.engine;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.paynudge.channel.Channel;
import com.paynudge.channel.Channel.ChannelException;
import com.paynudge.channel.EmailDeliveryException;
import com.paynudge.channel.EmailMessage;
import com.paynudge.channel.EmailSender;
import com.paynudge.common.DueEvents.DueChanged;
import com.paynudge.common.PublicLinks;
import com.paynudge.dues.DueStatus;
import com.paynudge.dues.SendWindow;
import com.paynudge.dues.StepStatus;
import com.paynudge.engine.Reminders.Composed;
import com.paynudge.engine.Reminders.DueFacts;
import com.paynudge.engine.StepQueue.ClaimedStep;
import com.paynudge.policy.PolicyDefinition;
import com.paynudge.policy.PolicyDefinition.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs one claimed step in one transaction that holds three row locks until it commits:
 * <ul>
 * <li>the step's own, so while this worker lives no one else can take the step over, however slow the send (the
 * reaper skips locked rows);</li>
 * <li>the due's, which a payment or the owner's cancel also takes: either it lands first and the step is cancelled
 * here, or it waits until the reminder has gone;</li>
 * <li>the customer's, which unsubscribing takes, and which keeps two of the customer's dues from emailing them in
 * the same moment.</li>
 * </ul>
 * Then: closed or opted out, cancel; already sent (by a try that died before finishing), just finish; inside quiet
 * hours or too soon after the customer's last email, postpone. Otherwise record the message, send it and finish. If
 * the send fails the whole transaction rolls back, message row included, and the worker decides about a retry.
 */
@Component
class StepExecutor {

	private static final Logger log = LoggerFactory.getLogger(StepExecutor.class);

	static final String OWNER_CHANNEL = "OWNER_EMAIL";

	private final JdbcTemplate jdbc;

	private final StepQueue queue;

	private final Channel channel;

	private final EmailSender ownerMail;

	private final PublicLinks links;

	private final JsonMapper json;

	private final Clock clock;

	private final EngineProperties properties;

	private final ApplicationEventPublisher events;

	StepExecutor(JdbcTemplate jdbc, StepQueue queue, Channel channel, EmailSender ownerMail, PublicLinks links,
			JsonMapper json, Clock clock, EngineProperties properties, ApplicationEventPublisher events) {
		this.jdbc = jdbc;
		this.queue = queue;
		this.channel = channel;
		this.ownerMail = ownerMail;
		this.links = links;
		this.json = json;
		this.clock = clock;
		this.properties = properties;
		this.events = events;
	}

	/** @return true if an email went to the customer (and so used one of the business's sends for the day) */
	@Transactional
	boolean execute(ClaimedStep step) {
		if (!holdStep(step)) {
			// The lease ran out before this worker got going and the step is someone else's now (or the due was
			// deleted, steps and all).
			log.warn("Lease lost before running dueId={} stepKey={} attempt={}", step.dueId(), step.stepKey(),
					step.attempt());
			return false;
		}
		Optional<Locked> locked = lockDue(step.dueId());
		if (locked.isEmpty()) {
			return false;
		}
		DueFacts due = locked.get().facts();
		if (DueStatus.CLOSED.contains(due.status())) {
			finish(step, StepStatus.CANCELLED, "Due is " + due.status().toLowerCase());
			return false;
		}
		if (due.customerOptedOut()) {
			finish(step, StepStatus.CANCELLED, "Customer unsubscribed");
			return false;
		}
		Step definition = locked.get()
			.policy()
			.steps()
			.stream()
			.filter((candidate) -> candidate.key().equals(step.stepKey()))
			.findFirst()
			.orElse(null);
		if (definition == null) {
			finish(step, StepStatus.FAILED, "Step is not in the due's policy");
			return false;
		}
		return switch (definition.action()) {
			case SEND_EMAIL -> remindCustomer(step, definition, due);
			case NOTIFY_OWNER -> notifyOwner(step, due);
		};
	}

	private boolean remindCustomer(ClaimedStep step, Step definition, DueFacts due) {
		if (due.customerEmail() == null) {
			finish(step, StepStatus.SKIPPED, "No email address for this customer");
			return false;
		}
		if (alreadySent(step, this.channel.name())) {
			finish(step, StepStatus.DONE, null); // sent by an earlier try that died before finishing
			return false;
		}
		Instant now = this.clock.instant();
		Instant allowed = due.window().nextAllowed(now);
		if (allowed.isAfter(now)) {
			this.queue.postpone(step, allowed, "Moved out of quiet hours");
			return false;
		}
		Instant lastEmail = lastEmailTo(due.customerId());
		if (lastEmail != null && now.isBefore(lastEmail.plus(this.properties.minGap()))) {
			this.queue.postpone(step, due.window().nextAllowed(lastEmail.plus(this.properties.minGap())),
					"Spaced out from the last email to this customer");
			return false;
		}
		Composed message = Reminders.customerEmail(definition, due, this.links);
		long recorded = record(step, this.channel.name(), message, now);
		String providerId = this.channel.send(new Channel.OutboundMessage(due.customerEmail(), message.subject(),
				message.text(), reference(step), this.links.oneClickUnsubscribe(due.unsubscribeToken())));
		this.jdbc.update("UPDATE messages SET provider_id = ? WHERE id = ?", providerId, recorded);
		finish(step, StepStatus.DONE, null);
		this.jdbc.update("UPDATE dues SET status = ? WHERE id = ? AND status = ?", DueStatus.ACTIVE, step.dueId(),
				DueStatus.SCHEDULED);
		log.info("Reminder sent dueId={} stepKey={} attempt={}", step.dueId(), step.stepKey(), step.attempt());
		return true;
	}

	private boolean notifyOwner(ClaimedStep step, DueFacts due) {
		if (!alreadySent(step, OWNER_CHANNEL)) {
			Composed notice = Reminders.ownerNotice(due, this.links);
			long recorded = record(step, OWNER_CHANNEL, notice, this.clock.instant());
			try {
				String providerId = this.ownerMail
					.send(new EmailMessage(due.ownerEmail(), notice.subject(), notice.text(), null));
				this.jdbc.update("UPDATE messages SET provider_id = ? WHERE id = ?", providerId, recorded);
			}
			catch (EmailDeliveryException ex) {
				throw new ChannelException(ex.getMessage(), ex, ex.permanent());
			}
		}
		finish(step, StepStatus.DONE, null);
		this.jdbc.update("UPDATE dues SET status = ? WHERE id = ? AND status IN (?, ?)", DueStatus.ESCALATED,
				step.dueId(), DueStatus.SCHEDULED, DueStatus.ACTIVE);
		log.info("Owner notified dueId={} stepKey={} attempt={}", step.dueId(), step.stepKey(), step.attempt());
		return false;
	}

	/** Locks the step if this worker's lease on it still holds. */
	private boolean holdStep(ClaimedStep step) {
		return !this.jdbc
			.queryForList("SELECT id FROM due_steps WHERE id = ? AND lease_token = ? FOR UPDATE", Long.class,
					step.id(), step.leaseToken())
			.isEmpty();
	}

	private boolean alreadySent(ClaimedStep step, String channel) {
		return !this.jdbc
			.queryForList("SELECT id FROM messages WHERE due_id = ? AND step_key = ? AND channel = ?", Long.class,
					step.dueId(), step.stepKey(), channel)
			.isEmpty();
	}

	/** When the customer was last emailed about any of their dues, or null if never. */
	private Instant lastEmailTo(long customerId) {
		OffsetDateTime last = this.jdbc.queryForObject("""
				SELECT max(m.sent_at) FROM messages m JOIN dues d ON d.id = m.due_id
				WHERE d.customer_id = ? AND m.channel = ?""", OffsetDateTime.class, customerId, this.channel.name());
		return (last == null) ? null : last.toInstant();
	}

	/**
	 * Inserts the message row. {@code UNIQUE (due_id, step_key, channel)} is the last line of defence: a second
	 * message for the step fails the transaction before anything is sent.
	 */
	private long record(ClaimedStep step, String channel, Composed message, Instant sentAt) {
		return this.jdbc.queryForObject("""
				INSERT INTO messages (due_id, step_key, channel, subject, body, sent_at) VALUES (?, ?, ?, ?, ?, ?)
				RETURNING id""", Long.class, step.dueId(), step.stepKey(), channel, message.subject(), message.text(),
				sentAt.atOffset(ZoneOffset.UTC));
	}

	/**
	 * Ends the step. The step's row is locked by this transaction, so the lease token still matches. Open pages hear
	 * about it once the transaction commits.
	 */
	private void finish(ClaimedStep step, String status, String error) {
		this.jdbc.update("""
				UPDATE due_steps SET status = ?, lease_until = NULL, lease_token = NULL, error = ?, note = NULL
				WHERE id = ? AND lease_token = ?""", status, error, step.id(), step.leaseToken());
		this.events.publishEvent(new DueChanged(step.dueId()));
	}

	/** The same for every try of a step: a resent email carries the same Message-ID. */
	private static String reference(ClaimedStep step) {
		return "due-" + step.dueId() + "." + step.stepKey();
	}

	private Optional<Locked> lockDue(long dueId) {
		List<Locked> rows = this.jdbc.query("""
				SELECT d.id, d.status, d.amount_paise, d.due_date, d.pay_token, d.policy_snapshot::text AS policy,
				       c.id AS customer_id, c.name AS customer_name, c.email AS customer_email, c.opted_out,
				       c.unsub_token, b.name AS business_name, b.timezone, b.quiet_start, b.quiet_end,
				       u.email AS owner_email
				FROM dues d
				JOIN customers c ON c.id = d.customer_id
				JOIN businesses b ON b.id = d.business_id
				JOIN users u ON u.id = b.owner_id
				WHERE d.id = ?
				FOR UPDATE OF d, c""", (rs, row) -> new Locked(new DueFacts(rs.getLong("id"), rs.getString("status"),
				rs.getLong("amount_paise"), rs.getObject("due_date", LocalDate.class), rs.getString("pay_token"),
				rs.getLong("customer_id"), rs.getString("customer_name"), rs.getString("customer_email"),
				rs.getBoolean("opted_out"), rs.getString("unsub_token"), rs.getString("business_name"),
				rs.getString("owner_email"),
				SendWindow.of(rs.getString("timezone"), rs.getObject("quiet_start", LocalTime.class),
						rs.getObject("quiet_end", LocalTime.class))),
				this.json.readValue(rs.getString("policy"), PolicyDefinition.class)), dueId);
		return rows.stream().findFirst();
	}

	private record Locked(DueFacts facts, PolicyDefinition policy) {
	}

}
