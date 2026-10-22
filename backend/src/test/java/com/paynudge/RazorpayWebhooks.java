package com.paynudge;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Builds Razorpay webhook events the way Razorpay sends them, and signs them (or not) with the test profile's
 * webhook secret. The signing here is written independently of the production code, so a mistake in one shows up.
 */
public final class RazorpayWebhooks {

	/** {@code paynudge.razorpay.webhook-secret} in the test profile. */
	public static final String SECRET = "test-webhook-secret";

	/** 19 January 2026 07:00 UTC, the payment's own time. */
	public static final long PAID_AT_EPOCH = 1_768_806_000L;

	private RazorpayWebhooks() {
	}

	/** A {@code payment_link.paid} event for the link carrying {@code payToken} in its notes. */
	public static String paymentLinkPaid(String payToken, String paymentId, long amountPaise) {
		return """
				{"entity":"event","account_id":"acc_TestAccount01","event":"payment_link.paid",
				 "contains":["payment_link","order","payment"],
				 "payload":{
				  "payment_link":{"entity":{"id":"plink_%1$s","amount":%2$d,"amount_paid":%2$d,"currency":"INR",
				    "accept_partial":false,"notes":{"pay_token":"%3$s"},"status":"paid"}},
				  "order":{"entity":{"id":"order_%1$s","amount":%2$d,"amount_paid":%2$d,"status":"paid"}},
				  "payment":{"entity":{"id":"%1$s","amount":%2$d,"currency":"INR","status":"captured","method":"upi",
				    "captured":true,"created_at":%4$d}}},
				 "created_at":%4$d}""".formatted(paymentId, amountPaise, payToken, PAID_AT_EPOCH);
	}

	/** Razorpay's signature: hex HMAC-SHA256 of the raw body with the webhook secret. */
	public static String sign(String body, String secret) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Delivers a correctly signed event. */
	public static MvcTestResult deliver(MockMvcTester mvc, String body) {
		return deliver(mvc, body, sign(body, SECRET));
	}

	/** Delivers an event with the given signature header (null: none). */
	public static MvcTestResult deliver(MockMvcTester mvc, String body, String signature) {
		var request = mvc.post()
			.uri("/webhooks/razorpay")
			.contentType(MediaType.APPLICATION_JSON)
			.header("X-Razorpay-Event-Id", "evt_" + Integer.toHexString(body.hashCode()))
			.content(body);
		if (signature != null) {
			request = request.header("X-Razorpay-Signature", signature);
		}
		return request.exchange();
	}

}
