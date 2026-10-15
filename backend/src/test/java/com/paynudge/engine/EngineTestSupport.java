package com.paynudge.engine;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import com.paynudge.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Engine tests drive the workers by hand (the pool is off in tests) and move the clock. The due in most tests
 * follows "Friendly then firm" for ₹1,500 due 20 January 2026: emails on 18, 20 and 23 January and 30 January
 * (final), the owner told on 27 January, all at 10:00 Kolkata time (04:30 UTC). The clock starts on 5 January.
 */
abstract class EngineTestSupport extends IntegrationTest {

	static final Instant FIRST_REMINDER = Instant.parse("2026-01-18T04:30:00Z");

	@Autowired
	StepWorker worker;

	@Autowired
	StepQueue queue;

	String token;

	@BeforeEach
	void signUp() {
		this.token = signUpOwner("asha@example.com", "Bright Tuition");
	}

	long customer(String name, String email) {
		return idOf(postAs(this.token, "/api/customers", Map.of("name", name, "email", email)));
	}

	long createDue(long customerId, String dueDate) {
		long policy = ((Number) JsonPath.read(bodyOf(getAs(this.token, "/api/policies")), "$[0].id")).longValue();
		return idOf(postAs(this.token, "/api/dues",
				Map.of("customerId", customerId, "amountPaise", 150000, "dueDate", dueDate, "policyId", policy)));
	}

	/** "key STATUS attempt" for each step, in run order. */
	List<String> stepStatuses(long due) {
		return this.jdbc.queryForList(
				"SELECT step_key || ' ' || status || ' ' || attempt FROM due_steps WHERE due_id = ? ORDER BY run_at, id",
				String.class, due);
	}

	String stepColumn(long due, String stepKey, String column) {
		return this.jdbc.queryForObject("SELECT " + column + "::text FROM due_steps WHERE due_id = ? AND step_key = ?",
				String.class, due, stepKey);
	}

	Instant runAt(long due, String stepKey) {
		return this.jdbc.queryForObject("SELECT run_at FROM due_steps WHERE due_id = ? AND step_key = ?",
				OffsetDateTime.class, due, stepKey)
			.toInstant();
	}

	String dueStatus(long due) {
		return this.jdbc.queryForObject("SELECT status FROM dues WHERE id = ?", String.class, due);
	}

	long messageCount() {
		return this.jdbc.queryForObject("SELECT count(*) FROM messages", Long.class);
	}

	static long idOf(MvcTestResult result) {
		return ((Number) JsonPath.read(bodyOf(result), "$.id")).longValue();
	}

}
