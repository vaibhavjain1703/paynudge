package com.paynudge.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A business owner's login. The email is stored normalized (trimmed, lower case). */
@Entity
@Table(name = "users")
public class UserAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	/** Null for owners who sign in only with an email code or Google. */
	@Column(name = "password_hash")
	private String passwordHash;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected UserAccount() {
	}

	UserAccount(String email, String passwordHash, Instant createdAt) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return this.id;
	}

	public String getEmail() {
		return this.email;
	}

	String getPasswordHash() {
		return this.passwordHash;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
