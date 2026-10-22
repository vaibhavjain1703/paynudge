package com.paynudge.dues;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.paynudge.common.Text;
import com.paynudge.dues.DueDtos.CustomerRef;
import com.paynudge.dues.DueDtos.DueCounts;
import com.paynudge.dues.DueDtos.DueDetail;
import com.paynudge.dues.DueDtos.DuePage;
import com.paynudge.dues.DueDtos.DueSummary;
import com.paynudge.dues.DueDtos.MessageView;
import com.paynudge.dues.DueDtos.NextStep;
import com.paynudge.dues.DueDtos.PaymentView;
import com.paynudge.dues.DueDtos.StepView;
import com.paynudge.policy.PolicyDefinition;
import com.paynudge.policy.PolicyDefinition.Step;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Read side of dues. Every query is scoped by business id. */
@Component
class DueQueries {

	private static final String SUMMARY_COLUMNS = """
			d.id, d.amount_paise, d.due_date, d.status, d.created_at,
			c.id AS customer_id, c.name AS customer_name, c.email AS customer_email, c.phone AS customer_phone,
			c.opted_out AS customer_opted_out,
			n.step_key AS next_key, n.action AS next_action, n.run_at AS next_run_at
			FROM dues d
			JOIN customers c ON c.id = d.customer_id
			LEFT JOIN LATERAL (
			    SELECT s.step_key, s.action, s.run_at FROM due_steps s
			    WHERE s.due_id = d.id AND s.status IN ('PENDING', 'RUNNING')
			    ORDER BY s.run_at, s.id LIMIT 1
			) n ON true
			""";

	/** The business's dues, narrowed by the search (customer name, email or phone) when there is one. */
	private static final String SCOPE = """
			d.business_id = :business
			AND (CAST(:pattern AS text) IS NULL OR lower(c.name) LIKE :pattern ESCAPE '\\'
			     OR c.email LIKE :pattern ESCAPE '\\' OR c.phone LIKE :pattern ESCAPE '\\')""";

	private final JdbcTemplate jdbc;

	private final NamedParameterJdbcTemplate named;

	private final JsonMapper json;

	DueQueries(JdbcTemplate jdbc, NamedParameterJdbcTemplate named, JsonMapper json) {
		this.jdbc = jdbc;
		this.named = named;
		this.json = json;
	}

	/** Soonest due date first. {@code today} is the business's own date, for "overdue". */
	DuePage list(long businessId, DueFilter filter, String search, LocalDate today, int page, int size) {
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("business", businessId)
			.addValue("pattern", (search == null) ? null : Text.containsPattern(search))
			.addValue("today", today)
			.addValue("limit", size)
			.addValue("offset", (long) page * size);
		DueCounts counts = this.named.queryForObject("SELECT count(*) AS all_dues, "
				+ String.join(", ",
						count(DueFilter.OPEN, "open"), count(DueFilter.NEEDS_YOU, "needs_you"),
						count(DueFilter.OVERDUE, "overdue"), count(DueFilter.PAID, "paid"),
						count(DueFilter.CANCELLED, "cancelled"))
				+ " FROM dues d JOIN customers c ON c.id = d.customer_id WHERE " + SCOPE, params,
				(rs, row) -> new DueCounts(rs.getLong("all_dues"), rs.getLong("open"), rs.getLong("needs_you"),
						rs.getLong("overdue"), rs.getLong("paid"), rs.getLong("cancelled")));
		List<DueSummary> items = this.named.query("SELECT " + SUMMARY_COLUMNS + " WHERE " + SCOPE + " AND ("
				+ filter.condition() + ") ORDER BY d.due_date, d.id LIMIT :limit OFFSET :offset", params,
				(rs, row) -> summary(rs));
		long total = switch (filter) {
			case ALL -> counts.all();
			case OPEN -> counts.open();
			case NEEDS_YOU -> counts.needsYou();
			case OVERDUE -> counts.overdue();
			case PAID -> counts.paid();
			case CANCELLED -> counts.cancelled();
		};
		return new DuePage(items, page, size, total, (int) ((total + size - 1) / size), counts);
	}

	Optional<DueDetail> detail(long businessId, long id) {
		List<DueDetail> found = this.jdbc.query("""
				SELECT d.id, d.amount_paise, d.due_date, d.status, d.policy_id, d.paid_at, d.created_at,
				       d.policy_snapshot::text AS policy, p.name AS policy_name,
				       c.id AS customer_id, c.name AS customer_name, c.email AS customer_email, c.phone AS customer_phone,
				       c.opted_out AS customer_opted_out
				FROM dues d
				JOIN customers c ON c.id = d.customer_id
				LEFT JOIN policies p ON p.id = d.policy_id
				WHERE d.id = ? AND d.business_id = ?""", (rs, row) -> new DueDetail(rs.getLong("id"), customer(rs),
				rs.getLong("amount_paise"), rs.getObject("due_date", LocalDate.class), rs.getString("status"),
				(Long) rs.getObject("policy_id"), rs.getString("policy_name"), instant(rs, "paid_at"),
				instant(rs, "created_at"), steps(id, this.json.readValue(rs.getString("policy"), PolicyDefinition.class)),
				messages(id), payments(id)), id, businessId);
		return found.stream().findFirst();
	}

	/** In run order, each with what the due's copy of the policy says about it (its day and tone). */
	private List<StepView> steps(long dueId, PolicyDefinition policy) {
		Map<String, Step> byKey = policy.steps().stream().collect(Collectors.toMap(Step::key, Function.identity()));
		return this.jdbc.query("""
				SELECT step_key, action, status, run_at, attempt, error, note FROM due_steps
				WHERE due_id = ? ORDER BY run_at, id""", (rs, row) -> {
			Step step = byKey.get(rs.getString("step_key"));
			return new StepView(rs.getString("step_key"), rs.getString("action"), rs.getString("status"),
					instant(rs, "run_at"), rs.getInt("attempt"), (step != null) ? step.offsetDays() : null,
					(step != null && step.emailsCustomer()) ? step.tone() : null, rs.getString("error"),
					rs.getString("note"));
		}, dueId);
	}

	private List<MessageView> messages(long dueId) {
		return this.jdbc.query("""
				SELECT step_key, channel, subject, sent_at FROM messages WHERE due_id = ? ORDER BY sent_at, id""",
				(rs, row) -> new MessageView(rs.getString("step_key"), rs.getString("channel"),
						rs.getString("subject"), instant(rs, "sent_at")),
				dueId);
	}

	private List<PaymentView> payments(long dueId) {
		return this.jdbc.query("""
				SELECT provider_payment_id, amount_paise, method, paid_at FROM payments
				WHERE due_id = ? ORDER BY paid_at, id""",
				(rs, row) -> new PaymentView(rs.getString("provider_payment_id"), rs.getLong("amount_paise"),
						rs.getString("method"), instant(rs, "paid_at")),
				dueId);
	}

	private static String count(DueFilter filter, String alias) {
		return "count(*) FILTER (WHERE " + filter.condition() + ") AS " + alias;
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
				rs.getString("customer_email"), rs.getString("customer_phone"), rs.getBoolean("customer_opted_out"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return (value == null) ? null : value.toInstant();
	}

}
