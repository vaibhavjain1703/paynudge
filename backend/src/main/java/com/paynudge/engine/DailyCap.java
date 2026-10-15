package com.paynudge.engine;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.paynudge.dues.SendWindow;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Each business may email at most {@code daily_send_cap} customers per day (its own calendar day). A worker takes a
 * slot before sending and gives it back if nothing went out. Taking a slot is one atomic statement, committed on its
 * own, so workers sending for the same business never wait on each other and can never pass the cap together. A
 * worker that dies holding a slot leaves the day's count one high, which errs on the side of sending less.
 */
@Component
class DailyCap {

	private final JdbcTemplate jdbc;

	private final Clock clock;

	DailyCap(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** One of a business's sends for one day. */
	record Slot(long businessId, LocalDate day) {
	}

	/** A slot for the due's business today, or empty if today's sends are used up (or the due is gone). */
	Optional<Slot> take(long dueId) {
		List<Slot> taken = this.jdbc.query("""
				INSERT INTO daily_sends AS s (business_id, day, sent)
				SELECT b.id, (?::timestamptz AT TIME ZONE b.timezone)::date, 1
				FROM dues d JOIN businesses b ON b.id = d.business_id
				WHERE d.id = ?
				ON CONFLICT (business_id, day) DO UPDATE SET sent = s.sent + 1
				WHERE s.sent < (SELECT daily_send_cap FROM businesses WHERE id = s.business_id)
				RETURNING business_id, day""",
				(rs, row) -> new Slot(rs.getLong("business_id"), rs.getObject("day", LocalDate.class)),
				this.clock.instant().atOffset(ZoneOffset.UTC), dueId);
		return taken.stream().findFirst();
	}

	void giveBack(Slot slot) {
		this.jdbc.update("UPDATE daily_sends SET sent = sent - 1 WHERE business_id = ? AND day = ? AND sent > 0",
				slot.businessId(), slot.day());
	}

	/** When the due's business may send again after using up today: tomorrow's send time, outside quiet hours. */
	Instant nextDay(long dueId) {
		Instant now = this.clock.instant();
		List<Instant> next = this.jdbc.query("""
				SELECT b.timezone, b.quiet_start, b.quiet_end
				FROM dues d JOIN businesses b ON b.id = d.business_id
				WHERE d.id = ?""", (rs, row) -> SendWindow.of(rs.getString("timezone"),
				rs.getObject("quiet_start", LocalTime.class), rs.getObject("quiet_end", LocalTime.class))
			.nextDay(now), dueId);
		return next.stream().findFirst().orElse(now);
	}

}
