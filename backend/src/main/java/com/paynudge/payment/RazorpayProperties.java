package com.paynudge.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param webhookSecret the secret set on the webhook in Razorpay's dashboard; Razorpay signs every event with it.
 * While it is empty no webhook is accepted.
 */
@ConfigurationProperties("paynudge.razorpay")
public record RazorpayProperties(String webhookSecret) {
}
