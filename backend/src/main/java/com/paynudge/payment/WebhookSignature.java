package com.paynudge.payment;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Razorpay's webhook signature: the hex HMAC-SHA256 of the raw request body, keyed with the webhook secret. It is
 * checked over the exact bytes received (re-serialised JSON would not match) and compared in constant time.
 */
final class WebhookSignature {

	private WebhookSignature() {
	}

	static boolean valid(byte[] body, String signature, String secret) {
		if (secret == null || secret.isBlank() || signature == null || signature.isBlank()) {
			return false;
		}
		byte[] expected = sign(body, secret).getBytes(StandardCharsets.US_ASCII);
		byte[] given = signature.strip().toLowerCase().getBytes(StandardCharsets.US_ASCII);
		return MessageDigest.isEqual(expected, given);
	}

	static String sign(byte[] body, String secret) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(body));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA256 is not available", ex);
		}
	}

}
