package com.paynudge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The engine's never-twice guarantees rest on database constraints, not application code. These tests pin them so a
 * later migration cannot quietly drop one.
 */
class SchemaConstraintsTest extends IntegrationTest {

	private long userId;

	private long businessId;

	private long customerId;

	private long dueId;

	@BeforeEach
	void seed() {
		this.userId = insertUser("owner@example.com");
		this.businessId = insertBusiness(this.userId);
		this.customerId = id("""
				INSERT INTO customers (business_id, name, email, unsub_token)
				VALUES (?, 'Asha', 'asha@example.com', 'unsub-1') RETURNING id""", this.businessId);
		this.dueId = insertDue(this.customerId, 150_000L, "pay-1");
	}

	@Test
	void stepKeyIsUniquePerDue() {
		insertStep(this.dueId, "remind_1");

		assertThatThrownBy(() -> insertStep(this.dueId, "remind_1")).isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void aMessageIsRecordedAtMostOncePerDueStepAndChannel() {
		insertMessage(this.dueId, "remind_1", "EMAIL");
		insertMessage(this.dueId, "remind_1", "SMS");

		assertThatThrownBy(() -> insertMessage(this.dueId, "remind_1", "EMAIL"))
			.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void aProviderPaymentIsRecordedOnce() {
		insertPayment(this.dueId, "pay_ABC");

		assertThatThrownBy(() -> insertPayment(this.dueId, "pay_ABC")).isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void aRunningStepMustHoldALease() {
		assertThatThrownBy(() -> this.jdbc.update("""
				INSERT INTO due_steps (due_id, step_key, action, status, run_at)
				VALUES (?, 'remind_1', 'SEND_EMAIL', 'RUNNING', now())""", this.dueId))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void amountsMustBePositive() {
		assertThatThrownBy(() -> insertDue(this.customerId, 0L, "pay-zero"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void unknownStatusesAreRejected() {
		assertThatThrownBy(() -> this.jdbc.update("UPDATE dues SET status = 'LOST' WHERE id = ?", this.dueId))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void anOwnerHasOneBusiness() {
		assertThatThrownBy(() -> insertBusiness(this.userId)).isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void deletingACustomerRemovesTheirDuesAndEverythingHangingOffThem() {
		insertStep(this.dueId, "remind_1");
		insertMessage(this.dueId, "remind_1", "EMAIL");
		insertPayment(this.dueId, "pay_DEL");

		this.jdbc.update("DELETE FROM customers WHERE id = ?", this.customerId);

		for (String table : new String[] { "dues", "due_steps", "messages", "payments" }) {
			assertThat(this.jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class)).as(table).isZero();
		}
	}

	private long insertUser(String email) {
		return id("INSERT INTO users (email, password_hash) VALUES (?, 'x') RETURNING id", email);
	}

	private long insertBusiness(long ownerId) {
		return id("INSERT INTO businesses (owner_id, name) VALUES (?, 'Bright Tuition') RETURNING id", ownerId);
	}

	private long insertDue(long customerId, long amountPaise, String payToken) {
		return id("""
				INSERT INTO dues (business_id, customer_id, amount_paise, due_date, status, pay_token, policy_snapshot)
				VALUES (?, ?, ?, DATE '2026-02-01', 'SCHEDULED', ?, '{}'::jsonb) RETURNING id""", this.businessId,
				customerId, amountPaise, payToken);
	}

	private void insertStep(long dueId, String stepKey) {
		this.jdbc.update("""
				INSERT INTO due_steps (due_id, step_key, action, status, run_at)
				VALUES (?, ?, 'SEND_EMAIL', 'PENDING', now())""", dueId, stepKey);
	}

	private void insertMessage(long dueId, String stepKey, String channel) {
		this.jdbc.update("INSERT INTO messages (due_id, step_key, channel, body) VALUES (?, ?, ?, 'hello')", dueId,
				stepKey, channel);
	}

	private void insertPayment(long dueId, String providerPaymentId) {
		this.jdbc.update("""
				INSERT INTO payments (due_id, provider_payment_id, amount_paise, paid_at)
				VALUES (?, ?, 150000, now())""", dueId, providerPaymentId);
	}

	private long id(String sql, Object... args) {
		Long id = this.jdbc.queryForObject(sql, Long.class, args);
		assertThat(id).isNotNull();
		return id;
	}

}
