package com.paynudge.payment;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import com.paynudge.payment.RazorpayApi.LinkPayment;
import com.paynudge.payment.RazorpayApi.NewPaymentLink;
import com.paynudge.payment.RazorpayApi.PaymentLink;
import com.paynudge.payment.RazorpayApi.RazorpayException;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The HTTP calls to Razorpay's payment link API, against a mock server. */
class RazorpayHttpApiTest {

	private static final Instant EXPIRES = Instant.ofEpochSecond(1_769_000_000L);

	private final RestClient.Builder builder = RestClient.builder();

	private final MockRestServiceServer razorpay = MockRestServiceServer.bindTo(this.builder).build();

	private final RazorpayHttpApi api = new RazorpayHttpApi(
			new RazorpayProperties("rzp_test_Key", "s3cret", null, "https://razorpay.test"), this.builder);

	@Test
	void aLinkIsCreatedForTheFullAmountWithoutRazorpayMessagingTheCustomer() {
		this.razorpay.expect(requestTo("https://razorpay.test/v1/payment_links"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Basic " + base64("rzp_test_Key:s3cret")))
			.andExpect(content().json("""
					{"amount":850000,"currency":"INR","accept_partial":false,"reference_id":"due-7-1767590000",
					 "description":"Payment to Sunrise PG",
					 "customer":{"name":"Rohan Gupta","email":"rohan@example.com","contact":"+919876543210"},
					 "notify":{"sms":false,"email":false},"reminder_enable":false,"notes":{"pay_token":"tok"},
					 "callback_url":"https://app.test/pay/tok","callback_method":"get","expire_by":1769000000}""",
					JsonCompareMode.STRICT))
			.andRespond(withSuccess("""
					{"id":"plink_Q1","short_url":"https://rzp.io/rzp/Q1","status":"created","expire_by":1769000000,
					 "notes":{"pay_token":"tok"},"payments":null,"amount":850000}""", MediaType.APPLICATION_JSON));

		PaymentLink link = this.api.createPaymentLink(new NewPaymentLink(850000, "due-7-1767590000",
				"Payment to Sunrise PG", "Rohan Gupta", "rohan@example.com", "+919876543210", "tok",
				"https://app.test/pay/tok", EXPIRES));

		assertThat(link).isEqualTo(new PaymentLink("plink_Q1", "https://rzp.io/rzp/Q1", "created", EXPIRES, "tok",
				List.of()));
		this.razorpay.verify();
	}

	@Test
	void aPaidLinkComesBackWithItsPayments() {
		this.razorpay.expect(requestTo("https://razorpay.test/v1/payment_links/plink_Q1"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("""
					{"id":"plink_Q1","short_url":"https://rzp.io/rzp/Q1","status":"paid","expire_by":1769000000,
					 "notes":{"pay_token":"tok"},
					 "payments":[{"amount":850000,"created_at":1767591000,"method":"upi","payment_id":"pay_R1",
					              "plink_id":"plink_Q1","status":"captured","updated_at":1767591001}]}""",
					MediaType.APPLICATION_JSON));

		PaymentLink link = this.api.paymentLink("plink_Q1");

		assertThat(link.status()).isEqualTo("paid");
		assertThat(link.payToken()).isEqualTo("tok");
		assertThat(link.payments()).containsExactly(
				new LinkPayment("pay_R1", 850000, "upi", "captured", Instant.ofEpochSecond(1_767_591_000L)));
	}

	@Test
	void linkNotesMayBeAnEmptyArray() {
		this.razorpay.expect(requestTo("https://razorpay.test/v1/payment_links/plink_Q1"))
			.andRespond(withSuccess("""
					{"id":"plink_Q1","short_url":"https://rzp.io/rzp/Q1","status":"created","expire_by":0,
					 "notes":[],"payments":[]}""", MediaType.APPLICATION_JSON));

		PaymentLink link = this.api.paymentLink("plink_Q1");

		assertThat(link.payToken()).isNull();
		assertThat(link.expiresAt()).isNull();
	}

	@Test
	void aRefusalComesBackAsARazorpayException() {
		this.razorpay.expect(requestTo("https://razorpay.test/v1/payment_links"))
			.andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":{\"code\":\"BAD_REQUEST_ERROR\",\"description\":\"reference_id already exists\"}}"));

		assertThatThrownBy(() -> this.api.createPaymentLink(new NewPaymentLink(850000, "due-7-1", "Payment",
				"Rohan", null, null, "tok", "https://app.test/pay/tok", EXPIRES)))
			.isInstanceOf(RazorpayException.class);
	}

	private static String base64(String value) {
		return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
	}

}
