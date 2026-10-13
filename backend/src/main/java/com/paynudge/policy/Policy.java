package com.paynudge.policy;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A business's reminder policy. The definition is kept as its JSON text; {@link PolicyService} reads and writes it, so
 * the entity never depends on how Hibernate maps JSON.
 */
@Entity
@Table(name = "policies")
class Policy {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "business_id", nullable = false, updatable = false)
	private Long businessId;

	@Column(nullable = false, length = 100)
	private String name;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private String definition;

	/** A built-in starter: it can be used and copied, but not edited or deleted. */
	@Column(name = "is_template", nullable = false, updatable = false)
	private boolean starter;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Policy() {
	}

	Policy(long businessId, String name, String definition, boolean starter, Instant createdAt) {
		this.businessId = businessId;
		this.name = name;
		this.definition = definition;
		this.starter = starter;
		this.createdAt = createdAt;
	}

	void update(String name, String definition) {
		this.name = name;
		this.definition = definition;
	}

	Long getId() {
		return this.id;
	}

	Long getBusinessId() {
		return this.businessId;
	}

	String getName() {
		return this.name;
	}

	String getDefinition() {
		return this.definition;
	}

	boolean isStarter() {
		return this.starter;
	}

	Instant getCreatedAt() {
		return this.createdAt;
	}

}
