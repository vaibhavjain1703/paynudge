package com.paynudge.dues;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The owner's dashboard in one read. Days, weeks and months are the business's own (its timezone), so "this month"
 * and "overdue" mean what the owner means.
 */
@Component
class DashboardQueries {

	/** Weeks in the collections chart, this one included. */
	static final int WEEKS = 12;

	static final int UPCOMING = 5;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	DashboardQueries(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** A sum of money and how many dues make it up. */
	record Total(long paise, long dues) {
	}

	/** Paid in the week starting on {@code weekStart} (a Monday). */
	record Week(LocalDate weekStart, long paise) {
	}

	record Upcoming(long dueId, String customerName, long amountPaise, String stepKey, String action, Instant runAt) {
	}

	/**
	 * @param collectedThisMonth dues paid since the 1st of this month
	 * @param outstanding dues not yet paid or cancelled
	 * @param overdue outstanding dues whose due date has passed
	 * @param needsYou dues whose policy has run out or whose reminder failed
	 * @param collections paid per week, oldest first, the last being this week
	 * @param upcoming the next reminders and owner notices, soonest first
	 */
	record Dashboard(Total collectedThisMonth, Total outstanding, Total overdue, long needsYou, List<Week> collections,
			List<Upcoming> upcoming) {
	}

	Dashboard load(long businessId) {
		ZoneId zone = ZoneId.of(this.jdbc.queryForObject("SELECT timezone FROM businesses WHERE id = ?", String.class,
				businessId));
		LocalDate today = this.clock.instant().atZone(zone).toLocalDate();
		LocalDate firstWeek = today.with(DayOfWeek.MONDAY).minusWeeks(WEEKS - 1);

		Map<String, Object> totals = this.jdbc.queryForMap("""
				SELECT coalesce(sum(amount_paise) FILTER (WHERE status = 'PAID' AND paid_at >= ?), 0) AS collected,
				       count(*) FILTER (WHERE status = 'PAID' AND paid_at >= ?) AS collected_dues,
				       coalesce(sum(amount_paise) FILTER (WHERE status IN ('SCHEDULED', 'ACTIVE', 'ESCALATED')), 0)
				           AS outstanding,
				       count(*) FILTER (WHERE status IN ('SCHEDULED', 'ACTIVE', 'ESCALATED')) AS outstanding_dues,
				       coalesce(sum(amount_paise) FILTER (WHERE status IN ('SCHEDULED', 'ACTIVE', 'ESCALATED')
				                                         AND due_date < ?), 0) AS overdue,
				       count(*) FILTER (WHERE status IN ('SCHEDULED', 'ACTIVE', 'ESCALATED') AND due_date < ?)
				           AS overdue_dues,
				       count(*) FILTER (WHERE status = 'ESCALATED') AS needs_you
				FROM dues WHERE business_id = ?""", startOf(today.withDayOfMonth(1), zone),
				startOf(today.withDayOfMonth(1), zone), today, today, businessId);

		Map<LocalDate, Long> paidByWeek = new HashMap<>();
		this.jdbc.query("""
				SELECT date_trunc('week', paid_at AT TIME ZONE ?)::date AS week, sum(amount_paise) AS paise
				FROM dues WHERE business_id = ? AND status = 'PAID' AND paid_at >= ?
				GROUP BY 1""", (rs) -> {
			paidByWeek.put(rs.getObject("week", LocalDate.class), rs.getLong("paise"));
		}, zone.getId(), businessId, startOf(firstWeek, zone));
		List<Week> collections = new ArrayList<>();
		for (int i = 0; i < WEEKS; i++) {
			LocalDate week = firstWeek.plusWeeks(i);
			collections.add(new Week(week, paidByWeek.getOrDefault(week, 0L)));
		}

		List<Upcoming> upcoming = this.jdbc.query("""
				SELECT s.due_id, c.name, d.amount_paise, s.step_key, s.action, s.run_at
				FROM due_steps s
				JOIN dues d ON d.id = s.due_id
				JOIN customers c ON c.id = d.customer_id
				WHERE d.business_id = ? AND s.status = 'PENDING'
				ORDER BY s.run_at, s.id
				LIMIT ?""", (rs, row) -> new Upcoming(rs.getLong("due_id"), rs.getString("name"),
				rs.getLong("amount_paise"), rs.getString("step_key"), rs.getString("action"),
				rs.getObject("run_at", OffsetDateTime.class).toInstant()), businessId, UPCOMING);

		return new Dashboard(total(totals, "collected"), total(totals, "outstanding"), total(totals, "overdue"),
				((Number) totals.get("needs_you")).longValue(), collections, upcoming);
	}

	private static Total total(Map<String, Object> row, String name) {
		return new Total(((Number) row.get(name)).longValue(), ((Number) row.get(name + "_dues")).longValue());
	}

	private static OffsetDateTime startOf(LocalDate day, ZoneId zone) {
		return day.atStartOfDay(zone).toInstant().atOffset(ZoneOffset.UTC);
	}

}
