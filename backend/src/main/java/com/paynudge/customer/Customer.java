package com.paynudge.customer;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Someone who owes the business money. Belongs to exactly one business. The unsubscribe token goes into every
 * reminder's opt-out link; only the customer can opt out, so the owner can never set {@code optedOut}.
 */
@Entity
@Table(name = "customers")
public class Customer {

	private static final SecureRandom RANDOM = new SecureRandom();

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "business_id", nullable = false, updatable = false)
	private Long businessId;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(length = 255)
	private String email;

	@Column(length = 20)
	private String phone;

	@Column(name = "opted_out", nullable = false)
	private boolean optedOut;

	@Column(name = "unsub_token", nullable = false, updatable = false, length = 64)
	private String unsubToken;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Customer() {
	}

	Customer(long businessId, CustomerFields fields, Instant createdAt) {
		this.businessId = businessId;
		this.unsubToken = newUnsubscribeToken();
		this.createdAt = createdAt;
		update(fields);
	}

	void update(CustomerFields fields) {
		this.name = fields.name();
		this.email = fields.email();
		this.phone = fields.phone();
	}

	/** 32 random bytes, URL-safe: unguessable, so a link only works for the customer it was sent to. */
	static String newUnsubscribeToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public Long getId() {
		return this.id;
	}

	public Long getBusinessId() {
		return this.businessId;
	}

	public String getName() {
		return this.name;
	}

	public String getEmail() {
		return this.email;
	}

	public String getPhone() {
		return this.phone;
	}

	public boolean isOptedOut() {
		return this.optedOut;
	}

	public String getUnsubToken() {
		return this.unsubToken;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
