package com.paynudge.payment;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Razorpay's REST API over HTTPS, with the key id and secret as basic auth. */
@Component
class RazorpayHttpApi implements RazorpayApi {

	private final RestClient client;

	/** A slow Razorpay must not hold the customer's click for long; they can simply press Pay again. */
	@Autowired
	RazorpayHttpApi(RazorpayProperties properties) {
		this(properties, RestClient.builder().requestFactory(timeouts()));
	}

	RazorpayHttpApi(RazorpayProperties properties, RestClient.Builder builder) {
		String credentials = properties.keyId() + ":" + properties.keySecret();
		this.client = builder.baseUrl(properties.baseUrl())
			.defaultHeader(HttpHeaders.AUTHORIZATION,
					"Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
			.build();
	}

	@Override
	public PaymentLink createPaymentLink(NewPaymentLink link) {
		Map<String, Object> customer = new LinkedHashMap<>();
		customer.put("name", link.customerName());
		if (link.customerEmail() != null) {
			customer.put("email", link.customerEmail());
		}
		if (link.customerPhone() != null) {
			customer.put("contact", link.customerPhone());
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("amount", link.amountPaise());
		body.put("currency", "INR");
		body.put("accept_partial", false);
		body.put("reference_id", link.referenceId());
		body.put("description", link.description());
		body.put("customer", customer);
		// PayNudge does the reminding; Razorpay should not message the customer too.
		body.put("notify", Map.of("sms", false, "email", false));
		body.put("reminder_enable", false);
		body.put("notes", Map.of("pay_token", link.payToken()));
		body.put("callback_url", link.callbackUrl());
		body.put("callback_method", "get");
		body.put("expire_by", link.expiresAt().getEpochSecond());
		try {
			return this.client.post()
				.uri("/v1/payment_links")
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(Link.class)
				.toPaymentLink();
		}
		catch (RestClientException ex) {
			throw new RazorpayException("Razorpay did not create the payment link", ex);
		}
	}

	@Override
	public PaymentLink paymentLink(String id) {
		try {
			return this.client.get().uri("/v1/payment_links/{id}", id).retrieve().body(Link.class).toPaymentLink();
		}
		catch (RestClientException ex) {
			throw new RazorpayException("Razorpay did not return the payment link", ex);
		}
	}

	private static JdkClientHttpRequestFactory timeouts() {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
		factory.setReadTimeout(Duration.ofSeconds(15));
		return factory;
	}

	/** A payment link as Razorpay sends it. {@code notes} is an object, or an empty array when there are none. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record Link(String id, @JsonProperty("short_url") String shortUrl, String status,
			@JsonProperty("expire_by") Long expireBy, Object notes, List<Payment> payments) {

		PaymentLink toPaymentLink() {
			String payToken = (this.notes instanceof Map<?, ?> map && map.get("pay_token") instanceof String token)
					? token : null;
			Instant expires = (this.expireBy == null || this.expireBy == 0) ? null
					: Instant.ofEpochSecond(this.expireBy);
			List<LinkPayment> paid = (this.payments == null) ? List.of()
					: this.payments.stream().map(Payment::toLinkPayment).toList();
			return new PaymentLink(this.id, this.shortUrl, this.status, expires, payToken, paid);
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Payment(@JsonProperty("payment_id") String paymentId, Long amount, String method, String status,
			@JsonProperty("created_at") Long createdAt) {

		LinkPayment toLinkPayment() {
			return new LinkPayment(this.paymentId, (this.amount == null) ? 0 : this.amount, this.method, this.status,
					(this.createdAt == null) ? null : Instant.ofEpochSecond(this.createdAt));
		}

	}

}
