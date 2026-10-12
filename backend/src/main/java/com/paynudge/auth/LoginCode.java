package com.paynudge.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A one-time sign-in code sent by email. Only its keyed hash is stored. */
@Entity
@Table(name = "login_codes")
class LoginCode {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String email;

	@Column(name = "code_hash", nullable = false, length = 64)
	private String codeHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected LoginCode() {
	}

	LoginCode(String email, String codeHash, Instant expiresAt, Instant createdAt) {
		this.email = email;
		this.codeHash = codeHash;
		this.expiresAt = expiresAt;
		this.createdAt = createdAt;
	}

	/** Still worth checking: not used, not expired, and not locked by too many wrong guesses. */
	boolean isUsable(Instant now, int maxAttempts) {
		return this.consumedAt == null && now.isBefore(this.expiresAt) && this.attempts < maxAttempts;
	}

	void recordWrongGuess() {
		this.attempts++;
	}

	void consume(Instant now) {
		this.consumedAt = now;
	}

	String getCodeHash() {
		return this.codeHash;
	}

	Instant getCreatedAt() {
		return this.createdAt;
	}

}
