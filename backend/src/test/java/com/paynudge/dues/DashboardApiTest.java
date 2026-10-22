package com.paynudge.dues;

import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import com.paynudge.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The owner's dashboard. Today is Monday 5 January 2026 in Kolkata; "this month", "this week" and "overdue" are
 * Kolkata's, not UTC's.
 */
class DashboardApiTest extends IntegrationTest {

	private String token;

	private long policy;

	@BeforeEach
	void owner() {
		this.token = signUpOwner("asha@example.com", "Bright Tuition");
		this.policy = ((Number) JsonPath.read(bodyOf(getAs(this.token, "/api/policies")), "$[0].id")).longValue();
	}

	@Test
	void totalsChartAndUpcomingRemindersAreTheBusinesssOwn() {
		due("Aarav", 150000, "2026-01-20"); // open, not yet due
		due("Bilal", 200000, "2026-01-02"); // open and overdue
		long chitra = due("Chitra", 50000, "2026-01-03"); // paid today
		long dev = due("Dev", 70000, "2025-12-20"); // paid on Monday 29 December
		long gita = due("Gita", 30000, "2025-12-24"); // paid at 00:30 on 1 January in Kolkata (still 2025 in UTC)
		long esha = due("Esha", 90000, "2026-01-10"); // cancelled
		long farah = due("Farah", 110000, "2026-01-01"); // overdue, and the policy has run out
		postAs(this.token, "/api/dues/" + chitra + "/mark-paid", Map.of());
		paidAt(dev, "2025-12-29T06:00:00Z");
		paidAt(gita, "2025-12-31T19:00:00Z");
		postAs(this.token, "/api/dues/" + esha + "/cancel", Map.of());
		this.jdbc.update("UPDATE dues SET status = 'ESCALATED' WHERE id = ?", farah);

		MvcTestResult result = getAs(this.token, "/api/dashboard");

		assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"dues":7,"collectedThisMonth":{"paise":80000,"dues":2},
				 "outstanding":{"paise":460000,"dues":3},
				 "overdue":{"paise":310000,"dues":2},
				 "needsYou":1}""");
		assertThat(result).bodyJson().extractingPath("$.collections[*].weekStart").asArray().hasSize(12)
			.startsWith("2025-10-20")
			.endsWith("2026-01-05");
		assertThat(result).bodyJson()
			.extractingPath("$.collections[*].paise")
			.isEqualTo(List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 100000, 50000));
		// Today: Bilal's third email (3 days late) and Farah's catch-up. Then the owner hears about Farah (8th) and
		// Bilal (9th), and Farah's final notice goes (11th). Aarav's first email (18th) is sixth.
		assertThat(result).bodyJson()
			.extractingPath("$.upcoming[*].customerName")
			.isEqualTo(List.of("Bilal", "Farah", "Farah", "Bilal", "Farah"));
		assertThat(result).bodyJson()
			.extractingPath("$.upcoming[*].action")
			.isEqualTo(List.of("SEND_EMAIL", "SEND_EMAIL", "NOTIFY_OWNER", "NOTIFY_OWNER", "SEND_EMAIL"));
		assertThat(result).bodyJson().extractingPath("$.upcoming[0].runAt").isEqualTo("2026-01-05T04:30:00Z");
	}

	@Test
	void aNewBusinessStartsAtZero() {
		MvcTestResult result = getAs(this.token, "/api/dashboard");

		assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"dues":0,"collectedThisMonth":{"paise":0,"dues":0},"outstanding":{"paise":0,"dues":0},
				 "overdue":{"paise":0,"dues":0},"needsYou":0,"upcoming":[]}""");
		assertThat(result).bodyJson().extractingPath("$.collections[*].paise").asArray().hasSize(12).containsOnly(0);
	}

	@Test
	void theDashboardNeedsASignedInOwner() {
		assertThat(this.mvc.get().uri("/api/dashboard").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private long due(String name, long paise, String dueDate) {
		long customer = idOf(postAs(this.token, "/api/customers",
				Map.of("name", name, "email", name.toLowerCase() + "@example.com")));
		return idOf(postAs(this.token, "/api/dues",
				Map.of("customerId", customer, "amountPaise", paise, "dueDate", dueDate, "policyId", this.policy)));
	}

	private void paidAt(long due, String instant) {
		postAs(this.token, "/api/dues/" + due + "/mark-paid", Map.of());
		this.jdbc.update("UPDATE dues SET paid_at = ?::timestamptz WHERE id = ?", instant, due);
	}

	private static long idOf(MvcTestResult result) {
		return ((Number) JsonPath.read(bodyOf(result), "$.id")).longValue();
	}

}
