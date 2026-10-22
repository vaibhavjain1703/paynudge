package com.paynudge.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Razorpay, in test mode for the MVP.
 * @param keyId the API key id ({@code rzp_test_...}); with {@code keySecret} it creates payment links
 * @param keySecret the API key secret; it also signs the result Razorpay hands back after a payment
 * @param webhookSecret the secret set on the webhook in Razorpay's dashboard; Razorpay signs every event with it.
 * While it is empty no webhook is accepted.
 * @param baseUrl Razorpay's API (tests point it elsewhere)
 */
@ConfigurationProperties("paynudge.razorpay")
public record RazorpayProperties(String keyId, String keySecret, String webhookSecret,
		@DefaultValue("https://api.razorpay.com") String baseUrl) {

	/** Whether payment links can be made at all. */
	boolean payable() {
		return this.keyId != null && !this.keyId.isBlank() && this.keySecret != null && !this.keySecret.isBlank();
	}

}
