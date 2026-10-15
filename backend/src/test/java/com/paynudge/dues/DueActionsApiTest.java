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

/** The owner stops a due's reminders: it was paid outside PayNudge, or it is called off. */
class DueActionsApiTest extends IntegrationTest {

	private String token;

	private long due;

	@BeforeEach
	void dueWithItsWholeSchedule() {
		this.token = signUpOwner("asha@example.com", "Bright Tuition");
		long customer = idOf(postAs(this.token, "/api/customers", Map.of("name", "Aarav", "email", "a@example.com")));
		long policy = ((Number) JsonPath.read(bodyOf(getAs(this.token, "/api/policies")), "$[0].id")).longValue();
		this.due = idOf(postAs(this.token, "/api/dues",
				Map.of("customerId", customer, "amountPaise", 150000, "dueDate", "2026-01-20", "policyId", policy)));
	}

	@Test
	void markingADuePaidCancelsEveryReminderStillWaiting() {
		MvcTestResult result = postAs(this.token, "/api/dues/" + this.due + "/mark-paid", Map.of());

		assertThat(result).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("PAID");
		assertThat(result).bodyJson().extractingPath("$.paidAt").isEqualTo("2026-01-05T04:30:00Z");
		assertThat(stepStatuses()).containsOnly("CANCELLED Due is paid");

		// Again: nothing changes.
		assertThat(postAs(this.token, "/api/dues/" + this.due + "/mark-paid", Map.of())).hasStatusOk()
			.bodyJson()
			.extractingPath("$.paidAt")
			.isEqualTo("2026-01-05T04:30:00Z");

		MvcTestResult cancel = postAs(this.token, "/api/dues/" + this.due + "/cancel", Map.of());
		assertThat(cancel).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("DUE_PAID");
	}

	@Test
	void cancellingADueStopsItsReminders() {
		MvcTestResult result = postAs(this.token, "/api/dues/" + this.due + "/cancel", Map.of());

		assertThat(result).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
		assertThat(stepStatuses()).containsOnly("CANCELLED Due is cancelled");
		assertThat(postAs(this.token, "/api/dues/" + this.due + "/cancel", Map.of())).hasStatusOk();

		MvcTestResult paid = postAs(this.token, "/api/dues/" + this.due + "/mark-paid", Map.of());
		assertThat(paid).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("DUE_CANCELLED");
	}

	@Test
	void stepsThatAlreadyRanKeepTheirOutcome() {
		this.jdbc.update("UPDATE due_steps SET status = 'DONE', attempt = 1 WHERE due_id = ? AND step_key = 'remind_1'",
				this.due);

		postAs(this.token, "/api/dues/" + this.due + "/mark-paid", Map.of());

		assertThat(stepStatuses()).containsExactly("DONE null", "CANCELLED Due is paid", "CANCELLED Due is paid",
				"CANCELLED Due is paid", "CANCELLED Due is paid");
	}

	@Test
	void anOwnerCanNeverMarkPaidOrCancelAnotherOwnersDue() {
		String other = signUpOwner("ravi@example.com", "Ravi Gym");

		for (String action : List.of("mark-paid", "cancel")) {
			MvcTestResult result = postAs(other, "/api/dues/" + this.due + "/" + action, Map.of());
			assertThat(result).hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
		}
		assertThat(this.jdbc.queryForObject("SELECT status FROM dues WHERE id = ?", String.class, this.due))
			.isEqualTo("SCHEDULED");
		assertThat(stepStatuses()).containsOnly("PENDING null");
	}

	@Test
	void anUnknownDueIsNotFoundAndActionsNeedASignedInOwner() {
		assertThat(postAs(this.token, "/api/dues/999999/cancel", Map.of())).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(this.mvc.post().uri("/api/dues/" + this.due + "/mark-paid").exchange())
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private List<String> stepStatuses() {
		return this.jdbc.queryForList("SELECT status || ' ' || coalesce(error, 'null') FROM due_steps WHERE due_id = ? "
				+ "ORDER BY run_at, id", String.class, this.due);
	}

	private static long idOf(MvcTestResult result) {
		return ((Number) JsonPath.read(bodyOf(result), "$.id")).longValue();
	}

}
