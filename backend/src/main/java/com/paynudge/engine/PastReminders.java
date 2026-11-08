package com.paynudge.engine;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

import com.paynudge.common.PublicLinks;
import com.paynudge.dues.DueStatus;
import com.paynudge.dues.SendWindow;
import com.paynudge.dues.StepStatus;
import com.paynudge.engine.Reminders.Composed;
import com.paynudge.engine.Reminders.DueFacts;
import com.paynudge.policy.PolicyDefinition;
import com.paynudge.policy.PolicyDefinition.Step;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a due's past as if PayNudge had been running all along, for demo data only. Every step planned before
 * {@code until} is marked sent at its planned time, with the message the engine would have written then (pay and
 * unsubscribe links included, so they work). Later steps wait for their planned time, so the engine carries on from
 * there as usual.
 */
@Component
public class PastReminders {

	private final JdbcTemplate jdbc;

	private final PublicLinks links;

	private final JsonMapper json;

	PastReminders(JdbcTemplate jdbc, PublicLinks links, JsonMapper json) {
		this.jdbc = jdbc;
		this.links = links;
		this.json = json;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void backfill(long dueId, Instant until) {
		Past due = load(dueId);
		boolean emailed = false;
		boolean ownerNotified = false;
		for (Step step : due.policy().steps()) {
			Instant planned = due.facts()
				.window()
				.nextAllowed(due.facts()
					.dueDate()
					.plusDays(step.offsetDays())
					.atTime(SendWindow.SEND_TIME)
					.atZone(due.facts().window().zone())
					.toInstant());
			if (!planned.isBefore(until)) {
				this.jdbc.update("""
						UPDATE due_steps SET status = ?, run_at = ?, error = NULL, note = NULL
						WHERE due_id = ? AND step_key = ? AND status IN (?, ?)""", StepStatus.PENDING,
						planned.atOffset(ZoneOffset.UTC), dueId, step.key(), StepStatus.PENDING, StepStatus.SKIPPED);
				continue;
			}
			Composed message = step.emailsCustomer() ? Reminders.customerEmail(step, due.facts(), this.links)
					: Reminders.ownerNotice(due.facts(), this.links);
			this.jdbc.update("""
					INSERT INTO messages (due_id, step_key, channel, subject, body, sent_at) VALUES (?, ?, ?, ?, ?, ?)
					ON CONFLICT (due_id, step_key, channel) DO NOTHING""", dueId, step.key(),
					step.emailsCustomer() ? "EMAIL" : StepExecutor.OWNER_CHANNEL, message.subject(), message.text(),
					planned.atOffset(ZoneOffset.UTC));
			this.jdbc.update("""
					UPDATE due_steps SET status = ?, run_at = ?, attempt = 1, error = NULL, note = NULL
					WHERE due_id = ? AND step_key = ?""", StepStatus.DONE, planned.atOffset(ZoneOffset.UTC), dueId,
					step.key());
			emailed |= step.emailsCustomer();
			ownerNotified |= !step.emailsCustomer();
		}
		if (ownerNotified) {
			this.jdbc.update("UPDATE dues SET status = ? WHERE id = ?", DueStatus.ESCALATED, dueId);
		}
		else if (emailed) {
			this.jdbc.update("UPDATE dues SET status = ? WHERE id = ?", DueStatus.ACTIVE, dueId);
		}
	}

	private Past load(long dueId) {
		return this.jdbc.queryForObject("""
				SELECT d.id, d.status, d.amount_paise, d.due_date, d.pay_token, d.policy_snapshot::text AS policy,
				       c.id AS customer_id, c.name AS customer_name, c.email AS customer_email, c.opted_out,
				       c.unsub_token, b.name AS business_name, b.timezone, b.quiet_start, b.quiet_end,
				       u.email AS owner_email
				FROM dues d
				JOIN customers c ON c.id = d.customer_id
				JOIN businesses b ON b.id = d.business_id
				JOIN users u ON u.id = b.owner_id
				WHERE d.id = ?""", (rs, row) -> new Past(new DueFacts(rs.getLong("id"), rs.getString("status"),
				rs.getLong("amount_paise"), rs.getObject("due_date", LocalDate.class), rs.getString("pay_token"),
				rs.getLong("customer_id"), rs.getString("customer_name"), rs.getString("customer_email"),
				rs.getBoolean("opted_out"), rs.getString("unsub_token"), rs.getString("business_name"),
				rs.getString("owner_email"),
				SendWindow.of(rs.getString("timezone"), rs.getObject("quiet_start", LocalTime.class),
						rs.getObject("quiet_end", LocalTime.class))),
				this.json.readValue(rs.getString("policy"), PolicyDefinition.class)), dueId);
	}

	private record Past(DueFacts facts, PolicyDefinition policy) {
	}

}
