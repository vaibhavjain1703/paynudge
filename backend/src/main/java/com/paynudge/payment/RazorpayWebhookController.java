package com.paynudge.payment;

import java.time.Clock;
import java.util.Optional;

import com.paynudge.common.ApiException;
import com.paynudge.common.ErrorCodes;
import com.paynudge.payment.RazorpayEvent.PaidLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Razorpay's webhook. Nothing is read before the signature checks out, so a forged or altered event changes nothing.
 * A genuine event PayNudge has no use for (another event type, a link it did not create) still gets a 200, or
 * Razorpay would keep retrying it.
 */
@RestController
class RazorpayWebhookController {

	static final String INVALID_SIGNATURE = "INVALID_SIGNATURE";

	private static final Logger log = LoggerFactory.getLogger(RazorpayWebhookController.class);

	private final RazorpayProperties properties;

	private final Payments payments;

	private final JsonMapper json;

	private final Clock clock;

	RazorpayWebhookController(RazorpayProperties properties, Payments payments, JsonMapper json, Clock clock) {
		this.properties = properties;
		this.payments = payments;
		this.json = json;
		this.clock = clock;
	}

	/** {@code status}: recorded, duplicate, unknown_link, underpaid or ignored. */
	record WebhookReceipt(String status) {
	}

	@PostMapping("/webhooks/razorpay")
	WebhookReceipt receive(@RequestBody byte[] body,
			@RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
			@RequestHeader(name = "X-Razorpay-Event-Id", required = false) String eventId) {
		if (!WebhookSignature.valid(body, signature, this.properties.webhookSecret())) {
			log.warn("Webhook rejected: bad signature eventId={}", eventId);
			throw ApiException.unauthorized(INVALID_SIGNATURE, "The webhook signature does not match");
		}
		RazorpayEvent event;
		try {
			event = this.json.readValue(body, RazorpayEvent.class);
		}
		catch (JacksonException ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.MALFORMED_REQUEST, "The event is not valid JSON");
		}
		Optional<PaidLink> paid = event.paidLink(this.clock.instant());
		if (paid.isEmpty()) {
			log.info("Webhook ignored event={} eventId={}", event.event(), eventId);
			return new WebhookReceipt("ignored");
		}
		Payments.Outcome outcome = this.payments.record(paid.get());
		log.info("Webhook {} paymentId={} eventId={}", outcome, paid.get().paymentId(), eventId);
		return new WebhookReceipt(outcome.name().toLowerCase());
	}

}
