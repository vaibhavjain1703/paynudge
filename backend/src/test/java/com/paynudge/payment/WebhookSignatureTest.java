package com.paynudge.payment;

import java.nio.charset.StandardCharsets;

import com.paynudge.RazorpayWebhooks;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureTest {

	private static final byte[] BODY = "{\"event\":\"payment_link.paid\"}".getBytes(StandardCharsets.UTF_8);

	@Test
	void aSignatureMadeWithTheSecretOverTheExactBodyIsValid() {
		String signature = RazorpayWebhooks.sign(new String(BODY, StandardCharsets.UTF_8), "s3cret");

		assertThat(WebhookSignature.valid(BODY, signature, "s3cret")).isTrue();
		assertThat(WebhookSignature.valid(BODY, signature.toUpperCase(), "s3cret")).as("hex case").isTrue();
		assertThat(WebhookSignature.valid(BODY, " " + signature + "\n", "s3cret")).as("stray whitespace").isTrue();
	}

	@Test
	void anythingElseIsNot() {
		String signature = RazorpayWebhooks.sign(new String(BODY, StandardCharsets.UTF_8), "s3cret");

		assertThat(WebhookSignature.valid(BODY, signature, "other")).isFalse();
		assertThat(WebhookSignature.valid("{}".getBytes(StandardCharsets.UTF_8), signature, "s3cret")).isFalse();
		assertThat(WebhookSignature.valid(BODY, signature.substring(1), "s3cret")).isFalse();
		assertThat(WebhookSignature.valid(BODY, null, "s3cret")).isFalse();
	}

	@Test
	void withoutASecretNothingIsValid() {
		String signature = RazorpayWebhooks.sign(new String(BODY, StandardCharsets.UTF_8), "s3cret");

		assertThat(WebhookSignature.valid(BODY, signature, "")).isFalse();
		assertThat(WebhookSignature.valid(BODY, signature, " ")).isFalse();
		assertThat(WebhookSignature.valid(BODY, signature, null)).isFalse();
	}

}
