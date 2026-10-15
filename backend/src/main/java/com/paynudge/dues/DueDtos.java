package com.paynudge.dues;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Request and response bodies for the dues endpoints. Money is always integer paise. */
final class DueDtos {

	private DueDtos() {
	}

	record CreateDueRequest(Long customerId, Long amountPaise, LocalDate dueDate, Long policyId) {
	}

	/** @param optedOut the customer used the unsubscribe link: no more reminders for any of their dues */
	record CustomerRef(long id, String name, String email, String phone, boolean optedOut) {
	}

	/** The next step still to run, or null when nothing is left. */
	record NextStep(String key, String action, Instant runAt) {
	}

	record DueSummary(long id, CustomerRef customer, long amountPaise, LocalDate dueDate, String status,
			NextStep nextStep, Instant createdAt) {
	}

	/**
	 * @param error why the step failed or was cancelled
	 * @param note why it is waiting (quiet hours, the daily limit, the gap between emails)
	 */
	record StepView(String key, String action, String status, Instant runAt, int attempt, String error, String note) {
	}

	record MessageView(String stepKey, String channel, String subject, Instant sentAt) {
	}

	/** @param policyName null if the policy was deleted later; the due keeps following its own copy */
	record DueDetail(long id, CustomerRef customer, long amountPaise, LocalDate dueDate, String status, Long policyId,
			String policyName, Instant paidAt, Instant createdAt, List<StepView> steps, List<MessageView> messages) {
	}

}
