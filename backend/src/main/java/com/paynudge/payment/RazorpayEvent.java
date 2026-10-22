package com.paynudge.payment;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The parts of a Razorpay webhook event PayNudge reads. Only {@code payment_link.paid} matters: the pay link was
 * paid in full. The link carries the due's pay token in its notes (set when the link is created), which is how the
 * payment finds its due.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record RazorpayEvent(String event, Payload payload) {

	static final String PAYMENT_LINK_PAID = "payment_link.paid";

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Payload(@JsonProperty("payment_link") Entity<PaymentLink> paymentLink, Entity<Payment> payment) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Entity<T>(T entity) {
	}

	/** {@code notes} is an object, or an empty array when the link has none (Razorpay's PHP heritage). */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record PaymentLink(String id, Object notes) {
	}

	/** {@code amount} in paise; {@code createdAt} in epoch seconds. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Payment(String id, Long amount, String currency, String method,
			@JsonProperty("created_at") Long createdAt) {
	}

	/** A paid pay link, as far as PayNudge is concerned. */
	record PaidLink(String payToken, String paymentId, long amountPaise, String method, Instant paidAt) {
	}

	/** The paid link this event reports, or empty for any other event (or a link PayNudge did not create). */
	Optional<PaidLink> paidLink(Instant receivedAt) {
		if (!PAYMENT_LINK_PAID.equals(this.event) || this.payload == null || this.payload.paymentLink() == null
				|| this.payload.paymentLink().entity() == null || this.payload.payment() == null) {
			return Optional.empty();
		}
		Payment payment = this.payload.payment().entity();
		String payToken = (this.payload.paymentLink().entity().notes() instanceof Map<?, ?> notes
				&& notes.get("pay_token") instanceof String token && !token.isBlank()) ? token : null;
		if (payment == null || payment.id() == null || payment.amount() == null || payment.amount() <= 0 || payToken == null
				|| !"INR".equals(payment.currency())) {
			return Optional.empty();
		}
		Instant paidAt = (payment.createdAt() != null) ? Instant.ofEpochSecond(payment.createdAt()) : receivedAt;
		return Optional.of(new PaidLink(payToken, payment.id(), payment.amount(), payment.method(), paidAt));
	}

}
