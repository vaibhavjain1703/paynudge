package com.paynudge.dues;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.paynudge.business.Business;
import com.paynudge.business.BusinessRepository;
import com.paynudge.common.ApiException;
import com.paynudge.common.Tokens;
import com.paynudge.dues.DueSchedule.PlannedStep;
import com.paynudge.policy.PolicyService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates dues and every one of their steps in the caller's transaction, so a due never exists without its schedule.
 * Ids come from the sequence up front, which lets thousands of dues and steps go in as a few batched statements.
 */
@Component
class DueWriter {

	private static final int BATCH_SIZE = 500;

	private final JdbcTemplate jdbc;

	private final BusinessRepository businesses;

	private final Clock clock;

	DueWriter(JdbcTemplate jdbc, BusinessRepository businesses, Clock clock) {
		this.jdbc = jdbc;
		this.businesses = businesses;
		this.clock = clock;
	}

	record NewDue(long customerId, long amountPaise, LocalDate dueDate, String importKey) {
	}

	/** @param firstReminderAt when the earliest customer email will go out, or null if none will */
	record Created(List<Long> ids, Instant firstReminderAt) {
	}

	@Transactional(propagation = Propagation.MANDATORY)
	Created create(long businessId, PolicyService.Snapshot policy, List<NewDue> dues) {
		if (dues.isEmpty()) {
			return new Created(List.of(), null);
		}
		Business business = this.businesses.findById(businessId)
			.orElseThrow(() -> ApiException.notFound("Business not found"));
		SendWindow window = new SendWindow(ZoneId.of(business.getTimezone()), business.getQuietStart(),
				business.getQuietEnd());
		Instant now = this.clock.instant();

		List<Long> ids = this.jdbc.queryForList("SELECT nextval('dues_id_seq') FROM generate_series(1, ?)",
				Long.class, dues.size());
		List<Object[]> dueRows = new ArrayList<>(dues.size());
		List<Object[]> stepRows = new ArrayList<>();
		Instant firstReminder = null;
		for (int i = 0; i < dues.size(); i++) {
			NewDue due = dues.get(i);
			long id = ids.get(i);
			dueRows.add(new Object[] { id, businessId, due.customerId(), policy.policyId(), due.amountPaise(),
					due.dueDate(), DueStatus.SCHEDULED, Tokens.random(), policy.json(), due.importKey(), utc(now) });
			List<PlannedStep> plan = DueSchedule.plan(policy.definition(), due.dueDate(), window, now);
			for (PlannedStep step : plan) {
				stepRows.add(new Object[] { id, step.key(), step.action(), step.status(), utc(step.runAt()) });
			}
			Instant first = plan.stream()
				.filter((step) -> step.status().equals(StepStatus.PENDING) && step.action().equals("SEND_EMAIL"))
				.map(PlannedStep::runAt)
				.min(Comparator.naturalOrder())
				.orElse(null);
			if (first != null && (firstReminder == null || first.isBefore(firstReminder))) {
				firstReminder = first;
			}
		}
		batch("""
				INSERT INTO dues (id, business_id, customer_id, policy_id, amount_paise, due_date, status, pay_token,
				                  policy_snapshot, import_key, created_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)""", dueRows);
		batch("INSERT INTO due_steps (due_id, step_key, action, status, run_at) VALUES (?, ?, ?, ?, ?)", stepRows);
		return new Created(ids, firstReminder);
	}

	private void batch(String sql, List<Object[]> rows) {
		for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
			this.jdbc.batchUpdate(sql, rows.subList(from, Math.min(rows.size(), from + BATCH_SIZE)));
		}
	}

	private static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

}
