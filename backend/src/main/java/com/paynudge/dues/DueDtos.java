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

	record CustomerRef(long id, String name, String email, String phone) {
	}

	/** The next step still to run, or null when nothing is left. */
	record NextStep(String key, String action, Instant runAt) {
	}

	record DueSummary(long id, CustomerRef customer, long amountPaise, LocalDate dueDate, String status,
			NextStep nextStep, Instant createdAt) {
	}

	record StepView(String key, String action, String status, Instant runAt, int attempt, String error) {
	}

	record MessageView(String stepKey, String channel, String subject, Instant sentAt) {
	}

	/** @param policyName null if the policy was deleted later; the due keeps following its own copy */
	record DueDetail(long id, CustomerRef customer, long amountPaise, LocalDate dueDate, String status, Long policyId,
			String policyName, Instant paidAt, Instant createdAt, List<StepView> steps, List<MessageView> messages) {
	}

}
