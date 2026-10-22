package com.paynudge.payment;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.jayway.jsonpath.JsonPath;
import com.paynudge.IntegrationTest;
import com.paynudge.payment.RazorpayApi.NewPaymentLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.paynudge.RazorpayWebhooks.deliver;
import static com.paynudge.RazorpayWebhooks.paymentLinkPaid;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The customer's pay page: what it shows, the Razorpay link its button opens (made once, reused), and the signed
 * result Razorpay hands back on the way home. No sign-in anywhere: the pay token in the link is the key.
 */
class PayPageApiTest extends IntegrationTest {

	/** {@code paynudge.razorpay.key-secret} in the test profile. */
	private static final String KEY_SECRET = "test-key-secret";

	private String token;

	private long due;

	private String payToken;

	@BeforeEach
	void dueOfEightyFiveHundred() {
		this.token = signUpOwner("asha@example.com", "Sunrise PG");
		long customer = idOf(postAs(this.token, "/api/customers",
				Map.of("name", "Rohan Gupta", "email", "rohan@example.com", "phone", "98765 43210")));
		long policy = ((Number) JsonPath.read(bodyOf(getAs(this.token, "/api/policies")), "$[0].id")).longValue();
		this.due = idOf(postAs(this.token, "/api/dues",
				Map.of("customerId", customer, "amountPaise", 850000, "dueDate", "2026-01-20", "policyId", policy)));
		this.payToken = this.jdbc.queryForObject("SELECT pay_token FROM dues WHERE id = ?", String.class, this.due);
	}

	@Test
	void thePageShowsWhoIsOwedWhatAndOnlyTheCustomersFirstName() {
		assertThat(page()).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{"businessName":"Sunrise PG","customerFirstName":"Rohan","amountPaise":850000,"dueDate":"2026-01-20",
				 "status":"OPEN","paidAt":null,"payable":true}""");
	}

	@Test
	void payingMakesOneRazorpayLinkAndReusesIt() {
		MvcTestResult first = link();

		assertThat(first).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{"url":"https://rzp.io/rzp/plink_1"}""");
		NewPaymentLink made = this.razorpay.created().get(0);
		assertThat(made.amountPaise()).isEqualTo(850000);
		assertThat(made.payToken()).isEqualTo(this.payToken);
		assertThat(made.callbackUrl()).isEqualTo("http://localhost:3000/pay/" + this.payToken);
		assertThat(made.referenceId()).isEqualTo("due-" + this.due + "-" + this.clock.instant().getEpochSecond());
		assertThat(made.customerName()).isEqualTo("Rohan Gupta");
		assertThat(made.customerEmail()).isEqualTo("rohan@example.com");
		assertThat(made.customerPhone()).isEqualTo("+919876543210");
		assertThat(made.expiresAt()).isEqualTo(this.clock.instant().plus(Duration.ofDays(7)));

		assertThat(link()).bodyJson().extractingPath("$.url").isEqualTo("https://rzp.io/rzp/plink_1");
		assertThat(this.razorpay.created()).as("a second click reuses the link").hasSize(1);

		// With less than a day left on it, the next click gets a fresh one.
		this.clock.advance(Duration.ofDays(6).plusHours(1));
		assertThat(link()).bodyJson().extractingPath("$.url").isEqualTo("https://rzp.io/rzp/plink_2");
	}

	@Test
	void thereIsNothingToPayOnAPaidOrCancelledDue() {
		postAs(this.token, "/api/dues/" + this.due + "/mark-paid", Map.of());

		assertThat(page()).bodyJson().extractingPath("$.status").isEqualTo("PAID");
		assertThat(page()).bodyJson().extractingPath("$.payable").isEqualTo(false);
		assertThat(link()).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.message")
			.isEqualTo("This is already paid");
		assertThat(this.razorpay.created()).isEmpty();
	}

	@Test
	void razorpayBeingDownIsSaidPlainly() {
		this.razorpay.down(true);

		assertThat(link()).hasStatus(HttpStatus.BAD_GATEWAY)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("PAYMENT_PROVIDER_ERROR");
	}

	@Test
	void theSignedResultFromRazorpayRecordsThePaymentOnce() {
		link();
		Instant paidAt = Instant.parse("2026-01-05T05:00:00Z");
		this.razorpay.pay("plink_1", "pay_R1", 850000, paidAt);

		MvcTestResult back = confirm(trip("pay_R1", "plink_1", "paid", KEY_SECRET));

		assertThat(back).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("PAID");
		assertThat(back).bodyJson().extractingPath("$.paidAt").isEqualTo("2026-01-05T05:00:00Z");
		assertThat(this.jdbc.queryForList("SELECT provider_payment_id || ' ' || method FROM payments", String.class))
			.containsExactly("pay_R1 upi");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM due_steps WHERE due_id = ? AND status = 'PENDING'",
				Long.class, this.due))
			.isZero();

		// The webhook for the same payment arrives later: nothing changes.
		assertThat(deliver(this.mvc, paymentLinkPaid(this.payToken, "pay_R1", 850000))).bodyJson()
			.extractingPath("$.status")
			.isEqualTo("duplicate");
		assertThat(confirm(trip("pay_R1", "plink_1", "paid", KEY_SECRET))).hasStatusOk();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM payments", Long.class)).isEqualTo(1);
	}

	@Test
	void aResultNotSignedByRazorpayChangesNothing() {
		link();
		this.razorpay.pay("plink_1", "pay_R1", 850000, Instant.parse("2026-01-05T05:00:00Z"));

		assertThat(confirm(trip("pay_R1", "plink_1", "paid", "a-guessed-secret"))).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("INVALID_SIGNATURE");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM payments", Long.class)).isZero();
		assertThat(page()).bodyJson().extractingPath("$.status").isEqualTo("OPEN");
	}

	@Test
	void aLinkForAnotherDueCannotPayThisOne() {
		long other = idOf(postAs(this.token, "/api/customers", Map.of("name", "Meera", "email", "meera@example.com")));
		long policy = ((Number) JsonPath.read(bodyOf(getAs(this.token, "/api/policies")), "$[0].id")).longValue();
		long cheaper = idOf(postAs(this.token, "/api/dues",
				Map.of("customerId", other, "amountPaise", 100, "dueDate", "2026-01-20", "policyId", policy)));
		String cheaperToken = this.jdbc.queryForObject("SELECT pay_token FROM dues WHERE id = ?", String.class,
				cheaper);
		this.mvc.post().uri("/api/public/pay/" + cheaperToken + "/link").exchange();
		this.razorpay.pay("plink_1", "pay_R1", 100, Instant.parse("2026-01-05T05:00:00Z"));

		// A genuine result, but from Meera's ₹1 link, brought to Rohan's page.
		assertThat(confirm(trip("pay_R1", "plink_1", "paid", KEY_SECRET))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(page()).bodyJson().extractingPath("$.status").isEqualTo("OPEN");
	}

	@Test
	void aLinkNotPaidYetLeavesTheDueOpen() {
		link();

		assertThat(confirm(trip("pay_R1", "plink_1", "paid", KEY_SECRET))).hasStatusOk()
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo("OPEN");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM payments", Long.class)).isZero();
	}

	@Test
	void anUnknownLinkIsNotFound() {
		assertThat(this.mvc.get().uri("/api/public/pay/not-a-real-token").exchange()).hasStatus(HttpStatus.NOT_FOUND)
			.bodyJson()
			.extractingPath("$.message")
			.isEqualTo("This payment link is not valid");
		assertThat(this.mvc.post().uri("/api/public/pay/not-a-real-token/link").exchange())
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	private MvcTestResult page() {
		return this.mvc.get().uri("/api/public/pay/" + this.payToken).exchange();
	}

	private MvcTestResult link() {
		return this.mvc.post().uri("/api/public/pay/" + this.payToken + "/link").exchange();
	}

	private MvcTestResult confirm(Map<String, String> trip) {
		return this.mvc.post()
			.uri("/api/public/pay/" + this.payToken + "/confirm")
			.contentType(MediaType.APPLICATION_JSON)
			.content(this.json.writeValueAsString(trip))
			.exchange();
	}

	/** The query Razorpay adds on the way back, signed as Razorpay signs it (with the key secret). */
	private static Map<String, String> trip(String paymentId, String linkId, String status, String secret) {
		String reference = "due-ref";
		return Map.of("paymentId", paymentId, "paymentLinkId", linkId, "referenceId", reference, "status", status,
				"signature", hmac(linkId + "|" + reference + "|" + status + "|" + paymentId, secret));
	}

	private static String hmac(String payload, String secret) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
		}
		catch (java.security.GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static long idOf(MvcTestResult result) {
		return ((Number) JsonPath.read(bodyOf(result), "$.id")).longValue();
	}

}
