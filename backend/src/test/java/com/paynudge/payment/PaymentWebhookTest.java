package com.paynudge.payment;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;
import com.paynudge.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.paynudge.RazorpayWebhooks.PAID_AT_EPOCH;
import static com.paynudge.RazorpayWebhooks.SECRET;
import static com.paynudge.RazorpayWebhooks.deliver;
import static com.paynudge.RazorpayWebhooks.paymentLinkPaid;
import static com.paynudge.RazorpayWebhooks.sign;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Razorpay's webhook: a genuine payment closes its due once, however many times (and however simultaneously) it is
 * delivered; anything forged, altered or unknown changes nothing.
 */
class PaymentWebhookTest extends IntegrationTest {

	private long due;

	private String payToken;

	@BeforeEach
	void dueOfFifteenHundredRupees() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");
		long customer = idOf(postAs(token, "/api/customers", Map.of("name", "Aarav", "email", "aarav@example.com")));
		long policy = ((Number) JsonPath.read(bodyOf(getAs(token, "/api/policies")), "$[0].id")).longValue();
		this.due = idOf(postAs(token, "/api/dues",
				Map.of("customerId", customer, "amountPaise", 150000, "dueDate", "2026-01-20", "policyId", policy)));
		this.payToken = this.jdbc.queryForObject("SELECT pay_token FROM dues WHERE id = ?", String.class, this.due);
	}

	@Test
	void aPaidLinkClosesTheDueAndCancelsItsReminders() {
		MvcTestResult result = deliver(this.mvc, paymentLinkPaid(this.payToken, "pay_Q1", 150000));

		assertThat(result).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{"status":"recorded"}""");
		assertThat(dueStatus()).isEqualTo("PAID");
		assertThat(this.jdbc.queryForObject("SELECT paid_at FROM dues WHERE id = ?", OffsetDateTime.class, this.due)
			.toInstant()).as("when the customer paid").isEqualTo(Instant.ofEpochSecond(PAID_AT_EPOCH));
		assertThat(this.jdbc.queryForList(
				"SELECT provider_payment_id || ' ' || amount_paise || ' ' || method FROM payments WHERE due_id = ?",
				String.class, this.due))
			.containsExactly("pay_Q1 150000 upi");
		assertThat(stepStatuses()).containsOnly("CANCELLED Due is paid");
	}

	@Test
	void aRedeliveredPaymentIsRecordedOnce() {
		String event = paymentLinkPaid(this.payToken, "pay_Q1", 150000);

		assertThat(deliver(this.mvc, event)).bodyJson().extractingPath("$.status").isEqualTo("recorded");
		for (int i = 0; i < 4; i++) {
			assertThat(deliver(this.mvc, event)).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("duplicate");
		}

		assertThat(paymentCount()).isEqualTo(1);
		assertThat(dueStatus()).isEqualTo("PAID");
	}

	@Test
	void copiesDeliveredAtTheSameMomentRecordOnePayment() throws Exception {
		String event = paymentLinkPaid(this.payToken, "pay_Q1", 150000);
		int copies = 8;
		CyclicBarrier start = new CyclicBarrier(copies);
		ExecutorService pool = Executors.newFixedThreadPool(copies);
		List<Callable<String>> deliveries = new ArrayList<>();
		for (int i = 0; i < copies; i++) {
			deliveries.add(() -> {
				start.await();
				return JsonPath.read(bodyOf(deliver(this.mvc, event)), "$.status");
			});
		}
		List<String> statuses = new ArrayList<>();
		for (Future<String> status : pool.invokeAll(deliveries)) {
			statuses.add(status.get());
		}
		pool.shutdown();

		assertThat(statuses).containsOnlyOnce("recorded").containsOnly("recorded", "duplicate").hasSize(copies);
		assertThat(paymentCount()).isEqualTo(1);
	}

	@Test
	void aForgedOrAlteredEventChangesNothing() {
		String event = paymentLinkPaid(this.payToken, "pay_Q1", 150000);
		String cheaper = paymentLinkPaid(this.payToken, "pay_Q1", 100);

		List<MvcTestResult> rejected = List.of(
				deliver(this.mvc, event, sign(event, "a-guessed-secret")), // signed with the wrong secret
				deliver(this.mvc, event, null), // not signed at all
				deliver(this.mvc, event, ""), // empty signature
				deliver(this.mvc, cheaper, sign(event, SECRET))); // body altered after signing
		for (MvcTestResult result : rejected) {
			assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED)
				.bodyJson()
				.extractingPath("$.code")
				.isEqualTo("INVALID_SIGNATURE");
		}

		assertThat(paymentCount()).isZero();
		assertThat(dueStatus()).isEqualTo("SCHEDULED");
		assertThat(stepStatuses()).containsOnly("PENDING null");
	}

	@Test
	void genuineEventsPayNudgeHasNoUseForAreAcknowledgedAndIgnored() {
		String unknownLink = paymentLinkPaid("not-one-of-ours", "pay_Q1", 150000);
		String otherEvent = paymentLinkPaid(this.payToken, "pay_Q2", 150000)
			.replace("\"event\":\"payment_link.paid\"", "\"event\":\"payment.failed\"");
		String noNotes = paymentLinkPaid(this.payToken, "pay_Q3", 150000)
			.replace("{\"pay_token\":\"" + this.payToken + "\"}", "[]");

		assertThat(deliver(this.mvc, unknownLink)).hasStatusOk()
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo("unknown_link");
		assertThat(deliver(this.mvc, otherEvent)).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("ignored");
		assertThat(deliver(this.mvc, noNotes)).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("ignored");

		assertThat(paymentCount()).isZero();
		assertThat(dueStatus()).isEqualTo("SCHEDULED");
	}

	@Test
	void aSignedEventThatIsNotJsonIsABadRequest() {
		assertThat(deliver(this.mvc, "{not json")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("MALFORMED_REQUEST");
	}

	@Test
	void lessThanTheDueIsRecordedButLeavesTheDueOpen() {
		assertThat(deliver(this.mvc, paymentLinkPaid(this.payToken, "pay_Q1", 100000))).hasStatusOk()
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo("underpaid");

		assertThat(paymentCount()).isEqualTo(1);
		assertThat(dueStatus()).isEqualTo("SCHEDULED");
		assertThat(stepStatuses()).containsOnly("PENDING null");
	}

	@Test
	void aCancelledDueThatGetsPaidIsPaid() {
		this.jdbc.update("UPDATE dues SET status = 'CANCELLED' WHERE id = ?", this.due);

		assertThat(deliver(this.mvc, paymentLinkPaid(this.payToken, "pay_Q1", 150000))).bodyJson()
			.extractingPath("$.status")
			.isEqualTo("recorded");
		assertThat(dueStatus()).isEqualTo("PAID");
	}

	private String dueStatus() {
		return this.jdbc.queryForObject("SELECT status FROM dues WHERE id = ?", String.class, this.due);
	}

	private long paymentCount() {
		return this.jdbc.queryForObject("SELECT count(*) FROM payments", Long.class);
	}

	private List<String> stepStatuses() {
		return this.jdbc.queryForList("SELECT status || ' ' || coalesce(error, 'null') FROM due_steps WHERE due_id = ?",
				String.class, this.due);
	}

	private static long idOf(MvcTestResult result) {
		return ((Number) JsonPath.read(bodyOf(result), "$.id")).longValue();
	}

}
