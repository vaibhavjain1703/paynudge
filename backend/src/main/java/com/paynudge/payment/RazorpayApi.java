package com.paynudge.payment;

import java.time.Instant;
import java.util.List;

/**
 * The two Razorpay calls PayNudge makes: create a payment link, and read one back (with its payments) to confirm a
 * payment the customer's browser reports. Tests replace it with a fake.
 */
public interface RazorpayApi {

	PaymentLink createPaymentLink(NewPaymentLink link);

	PaymentLink paymentLink(String id);

	/**
	 * @param referenceId unique across all the account's links (at most 40 characters)
	 * @param payToken kept in the link's notes: how a payment finds its due, from the webhook or the return trip
	 * @param callbackUrl where Razorpay sends the customer back, with the signed result in the query
	 */
	record NewPaymentLink(long amountPaise, String referenceId, String description, String customerName,
			String customerEmail, String customerPhone, String payToken, String callbackUrl, Instant expiresAt) {
	}

	/** @param status created, paid, partially_paid, expired or cancelled */
	record PaymentLink(String id, String shortUrl, String status, Instant expiresAt, String payToken,
			List<LinkPayment> payments) {
	}

	/** @param status captured once the money is in */
	record LinkPayment(String id, long amountPaise, String method, String status, Instant createdAt) {
	}

	/** Razorpay could not be reached, or refused the call. */
	class RazorpayException extends RuntimeException {

		public RazorpayException(String message, Throwable cause) {
			super(message, cause);
		}

	}

}
