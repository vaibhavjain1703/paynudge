package com.paynudge.engine;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import com.jayway.jsonpath.JsonPath;
import com.paynudge.IntegrationTest;
import com.paynudge.dues.DueService;
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

	@Autowired
	DueService dues;

	String token;

	@BeforeEach
	void signUp() {
		this.token = signUpOwner("asha@example.com", "Bright Tuition");
	}

	long customer(String name, String email) {
		return idOf(postAs(this.token, "/api/customers", Map.of("name", name, "email", email)));
	}

	long createDue(long customerId, String dueDate) {
		return idOf(postAs(this.token, "/api/dues", Map.of("customerId", customerId, "amountPaise", 150000,
				"dueDate", dueDate, "policyId", starterPolicy())));
	}

	/**
	 * {@code count} customers with one ₹1,500 "Friendly then firm" due each, written in bulk as an import would (the
	 * API, one due at a time, would take minutes). The daily cap is lifted so it never gets in the way.
	 * @return the due ids, oldest first
	 */
	List<Long> bulkDues(int count, String dueDate) {
		long business = this.jdbc.queryForObject("SELECT id FROM businesses", Long.class);
		this.jdbc.update("UPDATE businesses SET daily_send_cap = 1000000 WHERE id = ?", business);
		this.jdbc.batchUpdate("INSERT INTO customers (business_id, name, email, unsub_token) VALUES (?, ?, ?, ?)",
				IntStream.range(0, count)
					.mapToObj((i) -> new Object[] { business, "Customer " + i, "customer" + i + "@example.com",
							UUID.randomUUID().toString() })
					.toList());
		List<Long> customers = this.jdbc.queryForList("SELECT id FROM customers WHERE business_id = ? ORDER BY id",
				Long.class, business);
		this.dues.createImported(business, starterPolicy(), customers.stream()
			.map((customer) -> new DueService.ImportedDue(customer, 150000, LocalDate.parse(dueDate), null))
			.toList());
		return this.jdbc.queryForList("SELECT id FROM dues ORDER BY id", Long.class);
	}

	/** Runs {@code workers} workers at once until nothing is due. @return how many steps they ran in all */
	int drainWith(int workers) throws Exception {
		Callable<Integer> drain = this.worker::drain;
		ExecutorService pool = Executors.newFixedThreadPool(workers);
		try {
			int ran = 0;
			for (Future<Integer> result : pool.invokeAll(Collections.nCopies(workers, drain))) {
				ran += result.get();
			}
			return ran;
		}
		finally {
			pool.shutdown();
		}
	}

	/** "Friendly then firm", the first starter policy. */
	long starterPolicy() {
		return ((Number) JsonPath.read(bodyOf(getAs(this.token, "/api/policies")), "$[0].id")).longValue();
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
