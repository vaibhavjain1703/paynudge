package com.paynudge.dues;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.paynudge.common.PageResponse;
import com.paynudge.dues.DueDtos.CustomerRef;
import com.paynudge.dues.DueDtos.DueDetail;
import com.paynudge.dues.DueDtos.DueSummary;
import com.paynudge.dues.DueDtos.MessageView;
import com.paynudge.dues.DueDtos.NextStep;
import com.paynudge.dues.DueDtos.StepView;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Read side of dues. Every query is scoped by business id. */
@Component
class DueQueries {

	private static final String SUMMARY_COLUMNS = """
			d.id, d.amount_paise, d.due_date, d.status, d.created_at,
			c.id AS customer_id, c.name AS customer_name, c.email AS customer_email, c.phone AS customer_phone,
			n.step_key AS next_key, n.action AS next_action, n.run_at AS next_run_at
			FROM dues d
			JOIN customers c ON c.id = d.customer_id
			LEFT JOIN LATERAL (
			    SELECT s.step_key, s.action, s.run_at FROM due_steps s
			    WHERE s.due_id = d.id AND s.status IN ('PENDING', 'RUNNING')
			    ORDER BY s.run_at, s.id LIMIT 1
			) n ON true
			""";

	private final JdbcTemplate jdbc;

	DueQueries(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Soonest due date first. */
	PageResponse<DueSummary> list(long businessId, int page, int size) {
		long total = this.jdbc.queryForObject("SELECT count(*) FROM dues WHERE business_id = ?", Long.class,
				businessId);
		List<DueSummary> items = this.jdbc.query(
				"SELECT " + SUMMARY_COLUMNS + " WHERE d.business_id = ? ORDER BY d.due_date, d.id LIMIT ? OFFSET ?",
				(rs, row) -> summary(rs), businessId, size, (long) page * size);
		return new PageResponse<>(items, page, size, total, (int) ((total + size - 1) / size));
	}

	Optional<DueDetail> detail(long businessId, long id) {
		List<DueDetail> found = this.jdbc.query("""
				SELECT d.id, d.amount_paise, d.due_date, d.status, d.policy_id, d.paid_at, d.created_at,
				       p.name AS policy_name,
				       c.id AS customer_id, c.name AS customer_name, c.email AS customer_email, c.phone AS customer_phone
				FROM dues d
				JOIN customers c ON c.id = d.customer_id
				LEFT JOIN policies p ON p.id = d.policy_id
				WHERE d.id = ? AND d.business_id = ?""", (rs, row) -> new DueDetail(rs.getLong("id"), customer(rs),
				rs.getLong("amount_paise"), rs.getObject("due_date", LocalDate.class), rs.getString("status"),
				(Long) rs.getObject("policy_id"), rs.getString("policy_name"), instant(rs, "paid_at"),
				instant(rs, "created_at"), steps(id), messages(id)), id, businessId);
		return found.stream().findFirst();
	}

	private List<StepView> steps(long dueId) {
		return this.jdbc.query("""
				SELECT step_key, action, status, run_at, attempt, error FROM due_steps
				WHERE due_id = ? ORDER BY run_at, id""",
				(rs, row) -> new StepView(rs.getString("step_key"), rs.getString("action"), rs.getString("status"),
						instant(rs, "run_at"), rs.getInt("attempt"), rs.getString("error")),
				dueId);
	}

	private List<MessageView> messages(long dueId) {
		return this.jdbc.query("""
				SELECT step_key, channel, subject, sent_at FROM messages WHERE due_id = ? ORDER BY sent_at, id""",
				(rs, row) -> new MessageView(rs.getString("step_key"), rs.getString("channel"),
						rs.getString("subject"), instant(rs, "sent_at")),
				dueId);
	}

	private static DueSummary summary(ResultSet rs) throws SQLException {
		String nextKey = rs.getString("next_key");
		NextStep next = (nextKey == null) ? null
				: new NextStep(nextKey, rs.getString("next_action"), instant(rs, "next_run_at"));
		return new DueSummary(rs.getLong("id"), customer(rs), rs.getLong("amount_paise"),
				rs.getObject("due_date", LocalDate.class), rs.getString("status"), next, instant(rs, "created_at"));
	}

	private static CustomerRef customer(ResultSet rs) throws SQLException {
		return new CustomerRef(rs.getLong("customer_id"), rs.getString("customer_name"),
				rs.getString("customer_email"), rs.getString("customer_phone"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return (value == null) ? null : value.toInstant();
	}

}
