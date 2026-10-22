package com.paynudge.customer;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import com.paynudge.common.ApiException;
import com.paynudge.common.DueEvents.DuesChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A customer stops all reminders from one business with the link in any reminder (or their mail client's own
 * unsubscribe button). Only the link's token is needed: no account, and nothing about the customer is shown.
 */
@Service
class Unsubscribes {

	private static final Logger log = LoggerFactory.getLogger(Unsubscribes.class);

	private final JdbcTemplate jdbc;

	private final Clock clock;

	private final ApplicationEventPublisher events;

	Unsubscribes(JdbcTemplate jdbc, Clock clock, ApplicationEventPublisher events) {
		this.jdbc = jdbc;
		this.clock = clock;
		this.events = events;
	}

	/** What the unsubscribe page shows: whose reminders, and whether they have already stopped. */
	record UnsubscribeStatus(String businessName, boolean optedOut) {
	}

	UnsubscribeStatus status(String token) {
		return this.jdbc
			.query("""
					SELECT b.name, c.opted_out FROM customers c JOIN businesses b ON b.id = c.business_id
					WHERE c.unsub_token = ?""",
					(rs, row) -> new UnsubscribeStatus(rs.getString("name"), rs.getBoolean("opted_out")), token)
			.stream()
			.findFirst()
			.orElseThrow(Unsubscribes::unknownLink);
	}

	/**
	 * Marks the customer opted out and cancels every waiting step of their dues. Updating the customer row first
	 * takes its lock, which a worker also holds while sending: a reminder going out right now finishes, and none
	 * starts afterwards. Repeating it changes nothing.
	 */
	@Transactional
	UnsubscribeStatus unsubscribe(String token) {
		List<long[]> updated = this.jdbc.query("""
				UPDATE customers SET opted_out = true, opted_out_at = coalesce(opted_out_at, ?)
				WHERE unsub_token = ?
				RETURNING id, business_id""", (rs, row) -> new long[] { rs.getLong("id"), rs.getLong("business_id") },
				this.clock.instant().atOffset(ZoneOffset.UTC), token);
		if (updated.isEmpty()) {
			throw unknownLink();
		}
		long customer = updated.get(0)[0];
		int cancelled = this.jdbc.update("""
				UPDATE due_steps SET status = 'CANCELLED', error = 'Customer unsubscribed', note = NULL
				WHERE status = 'PENDING' AND due_id IN (SELECT id FROM dues WHERE customer_id = ?)""", customer);
		log.info("Customer unsubscribed customerId={} cancelledSteps={}", customer, cancelled);
		this.events.publishEvent(new DuesChanged(updated.get(0)[1]));
		return status(token);
	}

	private static ApiException unknownLink() {
		return ApiException.notFound("This unsubscribe link is not valid");
	}

}
