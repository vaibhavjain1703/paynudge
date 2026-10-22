package com.paynudge.dues;

import java.util.Arrays;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import com.paynudge.IntegrationTest;
import com.paynudge.engine.StepWorker;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.paynudge.RazorpayWebhooks.deliver;
import static com.paynudge.RazorpayWebhooks.paymentLinkPaid;
import static org.assertj.core.api.Assertions.assertThat;

/** What the due's timeline is drawn from: its steps (with their day and tone), the emails sent, the payments. */
class DueDetailApiTest extends IntegrationTest {

	@Autowired
	private StepWorker worker;

	@Test
	void theDetailCarriesEachStepsDayAndToneTheEmailsSentAndThePayments() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");
		long customer = idOf(postAs(token, "/api/customers", Map.of("name", "Aarav", "email", "aarav@example.com")));
		long policy = ((Number) JsonPath.read(bodyOf(getAs(token, "/api/policies")), "$[0].id")).longValue();
		// Due on 7 January: the first email (two days before) is due now, on 5 January.
		long due = idOf(postAs(token, "/api/dues",
				Map.of("customerId", customer, "amountPaise", 150000, "dueDate", "2026-01-07", "policyId", policy)));

		MvcTestResult scheduled = getAs(token, "/api/dues/" + due);
		assertThat(scheduled).bodyJson().extractingPath("$.steps[*].offsetDays").isEqualTo(
				Arrays.asList(-2, 0, 3, 7, 10));
		assertThat(scheduled).bodyJson().extractingPath("$.steps[*].tone").isEqualTo(
				Arrays.asList("friendly", "neutral", "firm", null, "final_notice"));
		assertThat(scheduled).bodyJson().extractingPath("$.payments").asArray().isEmpty();

		this.worker.drain();
		String payToken = this.jdbc.queryForObject("SELECT pay_token FROM dues WHERE id = ?", String.class, due);
		deliver(this.mvc, paymentLinkPaid(payToken, "pay_Q1", 150000));

		MvcTestResult paid = getAs(token, "/api/dues/" + due);
		assertThat(paid).bodyJson().extractingPath("$.status").isEqualTo("PAID");
		assertThat(paid).bodyJson().extractingPath("$.steps[*].status").isEqualTo(
				Arrays.asList("DONE", "CANCELLED", "CANCELLED", "CANCELLED", "CANCELLED"));
		assertThat(paid).bodyJson().extractingPath("$.messages[0]").isEqualTo(Map.of("stepKey", "remind_1", "channel",
				"EMAIL", "subject", "A quick reminder from Bright Tuition", "sentAt", "2026-01-05T04:30:00Z"));
		assertThat(paid).bodyJson().extractingPath("$.payments[0]").isEqualTo(Map.of("providerPaymentId", "pay_Q1",
				"amountPaise", 150000, "method", "upi", "paidAt", "2026-01-19T07:00:00Z"));
	}

	private static long idOf(MvcTestResult result) {
		return ((Number) JsonPath.read(bodyOf(result), "$.id")).longValue();
	}

}
