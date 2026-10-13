package com.paynudge.dues;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import com.paynudge.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

class DueApiTest extends IntegrationTest {

	private static final String BODY = "Hi {name}, {amount} is due on {due_date}. Pay here: {pay_link}";

	@Test
	void aNewDueGetsItsWholeScheduleAtOnce() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");
		long aarav = customer(token, "Aarav Shah", "aarav@example.com");

		MvcTestResult result = postAs(token, "/api/dues", due(aarav, 150000, "2026-11-05", starterId(token)));

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("SCHEDULED");
		assertThat(result).bodyJson().extractingPath("$.amountPaise").isEqualTo(150000);
		assertThat(result).bodyJson().extractingPath("$.customer.name").isEqualTo("Aarav Shah");
		assertThat(result).bodyJson().extractingPath("$.policyName").isEqualTo("Friendly then firm");
		assertThat(result).bodyJson().extractingPath("$.steps[*].key")
			.isEqualTo(List.of("remind_1", "remind_2", "remind_3", "notify_owner", "final"));
		assertThat(result).bodyJson().extractingPath("$.steps[*].status")
			.isEqualTo(List.of("PENDING", "PENDING", "PENDING", "PENDING", "PENDING"));
		assertThat(result).bodyJson().extractingPath("$.steps[0].runAt").isEqualTo("2026-11-03T04:30:00Z");
		assertThat(result).bodyJson().extractingPath("$.messages").asArray().isEmpty();
		assertThat(bodyOf(result)).doesNotContain("payToken").doesNotContain("pay_token");
	}

	@Test
	void theListShowsEachDuesNextReminderSoonestDueFirst() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");
		long aarav = customer(token, "Aarav Shah", "aarav@example.com");
		long diya = customer(token, "Diya Rao", "diya@example.com");
		long policy = starterId(token);
		postAs(token, "/api/dues", due(diya, 200000, "2026-12-01", policy));
		postAs(token, "/api/dues", due(aarav, 150000, "2026-11-05", policy));

		MvcTestResult list = getAs(token, "/api/dues");

		assertThat(list).hasStatusOk().bodyJson().extractingPath("$.totalItems").isEqualTo(2);
		assertThat(list).bodyJson().extractingPath("$.items[*].customer.name").isEqualTo(List.of("Aarav Shah",
				"Diya Rao"));
		assertThat(list).bodyJson().extractingPath("$.items[0].nextStep").isEqualTo(Map.of("key", "remind_1",
				"action", "SEND_EMAIL", "runAt", "2026-11-03T04:30:00Z"));
	}

	@Test
	void aDueKeepsItsOwnCopyOfThePolicy() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");
		long aarav = customer(token, "Aarav Shah", "aarav@example.com");
		long mine = idOf(postAs(token, "/api/policies", Map.of("name", "Two nudges", "steps",
				List.of(email("first", -1), email("second", 2)))));
		long due = idOf(postAs(token, "/api/dues", due(aarav, 150000, "2026-11-05", mine)));

		putAs(token, "/api/policies/" + mine, Map.of("name", "Two nudges", "steps",
				List.of(email("first", -5), email("second", 5))));
		assertThat(getAs(token, "/api/dues/" + due)).bodyJson().extractingPath("$.steps[*].runAt")
			.isEqualTo(List.of("2026-11-04T04:30:00Z", "2026-11-07T04:30:00Z"));

		deleteAs(token, "/api/policies/" + mine);
		MvcTestResult afterDelete = getAs(token, "/api/dues/" + due);
		assertThat(afterDelete).bodyJson().extractingPath("$.policyName").isNull();
		assertThat(afterDelete).bodyJson().extractingPath("$.steps[*].key").isEqualTo(List.of("first", "second"));
		assertThat(this.jdbc.queryForObject(
				"SELECT policy_snapshot -> 'steps' -> 0 ->> 'offsetDays' FROM dues WHERE id = ?", String.class, due))
			.isEqualTo("-1");
	}

	@Test
	void invalidDuesNameEveryBadField() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");

		MvcTestResult empty = postAs(token, "/api/dues", Map.of());
		assertThat(empty).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.details").asMap()
			.containsOnlyKeys("customerId", "amountPaise", "dueDate", "policyId");

		long aarav = customer(token, "Aarav Shah", "aarav@example.com");
		MvcTestResult tooMuch = postAs(token, "/api/dues", due(aarav, 1_000_000_001L, "2026-11-05", starterId(token)));
		assertThat(tooMuch).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.details.amountPaise")
			.isEqualTo("Amounts above ₹1 crore are not supported");
	}

	@Test
	void duesNeedASignedInOwner() {
		assertThat(this.mvc.get().uri("/api/dues").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(this.mvc.post().uri("/api/dues").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void anOwnerCanNeverSeeOrUseAnotherOwnersDuesCustomersOrPolicies() {
		String asha = signUpOwner("asha@example.com", "Bright Tuition");
		String ravi = signUpOwner("ravi@example.com", "Iron Gym");
		long aarav = customer(asha, "Aarav Shah", "aarav@example.com");
		long ashasDue = idOf(postAs(asha, "/api/dues", due(aarav, 150000, "2026-11-05", starterId(asha))));

		assertThat(getAs(ravi, "/api/dues")).bodyJson().extractingPath("$.totalItems").isEqualTo(0);
		assertThat(getAs(ravi, "/api/dues/" + ashasDue)).hasStatus(HttpStatus.NOT_FOUND);

		// Ravi cannot schedule against Asha's customer, nor with Asha's policy.
		long raviCustomer = customer(ravi, "Gym member", "member@example.com");
		assertThat(postAs(ravi, "/api/dues", due(aarav, 150000, "2026-11-05", starterId(ravi))))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.details.customerId")
			.isEqualTo("Choose one of your customers");
		assertThat(postAs(ravi, "/api/dues", due(raviCustomer, 150000, "2026-11-05", starterId(asha))))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.details.policyId")
			.isEqualTo("Choose one of your reminder policies");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM dues", Long.class)).isEqualTo(1);
	}

	private long customer(String token, String name, String email) {
		return idOf(postAs(token, "/api/customers", Map.of("name", name, "email", email)));
	}

	private long starterId(String token) {
		return ((Number) JsonPath.read(bodyOf(getAs(token, "/api/policies")), "$[0].id")).longValue();
	}

	private static Map<String, Object> due(long customerId, long amountPaise, String dueDate, long policyId) {
		return Map.of("customerId", customerId, "amountPaise", amountPaise, "dueDate", dueDate, "policyId", policyId);
	}

	private static Map<String, Object> email(String key, int offsetDays) {
		Map<String, Object> step = new HashMap<>();
		step.put("key", key);
		step.put("offsetDays", offsetDays);
		step.put("action", "SEND_EMAIL");
		step.put("subject", "About {amount}");
		step.put("body", BODY);
		return step;
	}

	private static long idOf(MvcTestResult result) {
		return ((Number) JsonPath.read(bodyOf(result), "$.id")).longValue();
	}

}
