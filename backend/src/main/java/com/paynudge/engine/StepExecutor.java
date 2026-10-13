package com.paynudge.engine;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.paynudge.channel.Channel;
import com.paynudge.channel.EmailMessage;
import com.paynudge.channel.EmailSender;
import com.paynudge.common.PublicLinks;
import com.paynudge.dues.DueStatus;
import com.paynudge.dues.StepStatus;
import com.paynudge.engine.Reminders.Composed;
import com.paynudge.engine.Reminders.DueFacts;
import com.paynudge.engine.StepQueue.ClaimedStep;
import com.paynudge.policy.PolicyDefinition;
import com.paynudge.policy.PolicyDefinition.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs one claimed step, in one transaction that holds the due's row lock, so a payment (which takes the same lock)
 * either lands before the step and stops it, or after the step has finished.
 * <ol>
 * <li>Lock the due and re-read it. Paid, cancelled or opted out: the step is cancelled; nothing is sent.</li>
 * <li>Record the message. {@code UNIQUE (due_id, step_key, channel)} refuses a second one, so a step that already
 * sent (and crashed before finishing) is only marked done.</li>
 * <li>Send, then mark the step done, but only if this worker still holds it.</li>
 * </ol>
 * If the send fails the whole transaction rolls back, message row included, and the worker schedules a retry.
 */
@Component
class StepExecutor {

	private static final Logger log = LoggerFactory.getLogger(StepExecutor.class);

	static final String OWNER_CHANNEL = "OWNER_EMAIL";

	private final JdbcTemplate jdbc;

	private final Channel channel;

	private final EmailSender ownerMail;

	private final PublicLinks links;

	private final JsonMapper json;

	StepExecutor(JdbcTemplate jdbc, Channel channel, EmailSender ownerMail, PublicLinks links, JsonMapper json) {
		this.jdbc = jdbc;
		this.channel = channel;
		this.ownerMail = ownerMail;
		this.links = links;
		this.json = json;
	}

	@Transactional
	void execute(ClaimedStep step) {
		Optional<Locked> locked = lockDue(step.dueId());
		if (locked.isEmpty()) {
			return; // the due was deleted (with its customer) after the claim; its steps went with it
		}
		DueFacts due = locked.get().facts();
		if (DueStatus.CLOSED.contains(due.status())) {
			finish(step, StepStatus.CANCELLED, "Due is " + due.status().toLowerCase());
			return;
		}
		if (due.customerOptedOut()) {
			finish(step, StepStatus.CANCELLED, "Customer opted out");
			return;
		}
		Step definition = locked.get().policy()
			.steps()
			.stream()
			.filter((candidate) -> candidate.key().equals(step.stepKey()))
			.findFirst()
			.orElse(null);
		if (definition == null) {
			finish(step, StepStatus.FAILED, "Step is not in the due's policy");
			return;
		}
		switch (definition.action()) {
			case SEND_EMAIL -> remindCustomer(step, definition, due);
			case NOTIFY_OWNER -> notifyOwner(step, due);
		}
	}

	private void remindCustomer(ClaimedStep step, Step definition, DueFacts due) {
		if (due.customerEmail() == null) {
			finish(step, StepStatus.SKIPPED, "No email address for this customer");
			return;
		}
		Composed message = Reminders.customerEmail(definition, due, this.links);
		Optional<Long> recorded = record(step, this.channel.name(), message);
		if (recorded.isEmpty()) {
			finish(step, StepStatus.DONE, null); // sent by an earlier try that crashed before finishing
			return;
		}
		String providerId = this.channel
			.send(new Channel.OutboundMessage(due.customerEmail(), message.subject(), message.text()));
		this.jdbc.update("UPDATE messages SET provider_id = ? WHERE id = ?", providerId, recorded.get());
		finish(step, StepStatus.DONE, null);
		this.jdbc.update("UPDATE dues SET status = ? WHERE id = ? AND status = ?", DueStatus.ACTIVE, step.dueId(),
				DueStatus.SCHEDULED);
		log.info("Reminder sent dueId={} stepKey={} attempt={}", step.dueId(), step.stepKey(), step.attempt());
	}

	private void notifyOwner(ClaimedStep step, DueFacts due) {
		Composed notice = Reminders.ownerNotice(due, this.links);
		Optional<Long> recorded = record(step, OWNER_CHANNEL, notice);
		if (recorded.isPresent()) {
			String providerId = this.ownerMail
				.send(new EmailMessage(due.ownerEmail(), notice.subject(), notice.text(), null));
			this.jdbc.update("UPDATE messages SET provider_id = ? WHERE id = ?", providerId, recorded.get());
		}
		finish(step, StepStatus.DONE, null);
		this.jdbc.update("UPDATE dues SET status = ? WHERE id = ? AND status IN (?, ?)", DueStatus.ESCALATED,
				step.dueId(), DueStatus.SCHEDULED, DueStatus.ACTIVE);
		log.info("Owner notified dueId={} stepKey={} attempt={}", step.dueId(), step.stepKey(), step.attempt());
	}

	/** The new message's id, or empty if this step already has one on this channel. */
	private Optional<Long> record(ClaimedStep step, String channel, Composed message) {
		List<Long> ids = this.jdbc.queryForList("""
				INSERT INTO messages (due_id, step_key, channel, subject, body) VALUES (?, ?, ?, ?, ?)
				ON CONFLICT (due_id, step_key, channel) DO NOTHING
				RETURNING id""", Long.class, step.dueId(), step.stepKey(), channel, message.subject(), message.text());
		return ids.stream().findFirst();
	}

	/** Ends the step. If this worker no longer holds it (its lease was taken over), everything is rolled back. */
	private void finish(ClaimedStep step, String status, String error) {
		int updated = this.jdbc.update("""
				UPDATE due_steps SET status = ?, lease_until = NULL, error = ?
				WHERE id = ? AND status = 'RUNNING' AND attempt = ?""", status, error, step.id(), step.attempt());
		if (updated == 0) {
			throw new IllegalStateException("Lost the lease on step " + step.id());
		}
	}

	private Optional<Locked> lockDue(long dueId) {
		List<Locked> rows = this.jdbc.query("""
				SELECT d.id, d.status, d.amount_paise, d.due_date, d.pay_token, d.policy_snapshot::text AS policy,
				       c.name AS customer_name, c.email AS customer_email, c.opted_out, c.unsub_token,
				       b.name AS business_name, u.email AS owner_email
				FROM dues d
				JOIN customers c ON c.id = d.customer_id
				JOIN businesses b ON b.id = d.business_id
				JOIN users u ON u.id = b.owner_id
				WHERE d.id = ?
				FOR UPDATE OF d""", (rs, row) -> new Locked(new DueFacts(rs.getLong("id"), rs.getString("status"),
				rs.getLong("amount_paise"), rs.getObject("due_date", LocalDate.class), rs.getString("pay_token"),
				rs.getString("customer_name"), rs.getString("customer_email"), rs.getBoolean("opted_out"),
				rs.getString("unsub_token"), rs.getString("business_name"), rs.getString("owner_email")),
				this.json.readValue(rs.getString("policy"), PolicyDefinition.class)), dueId);
		return rows.stream().findFirst();
	}

	private record Locked(DueFacts facts, PolicyDefinition policy) {
	}

}
