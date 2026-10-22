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

	/** How many dues (among those matching the search) each filter chip would show. */
	record DueCounts(long all, long open, long needsYou, long overdue, long paid, long cancelled) {
	}

	/** A page of the dues list, with the counts for the filter chips. */
	record DuePage(List<DueSummary> items, int page, int size, long totalItems, int totalPages, DueCounts counts) {
	}

	/**
	 * @param offsetDays days from the due date, as the due's own copy of the policy has it
	 * @param tone friendly, neutral, firm or final_notice for emails; null for owner notices
	 * @param error why the step failed or was cancelled
	 * @param note why it is waiting (quiet hours, the daily limit, the gap between emails)
	 */
	record StepView(String key, String action, String status, Instant runAt, int attempt, Integer offsetDays,
			String tone, String error, String note) {
	}

	record MessageView(String stepKey, String channel, String subject, Instant sentAt) {
	}

	record PaymentView(String providerPaymentId, long amountPaise, String method, Instant paidAt) {
	}

	/** @param policyName null if the policy was deleted later; the due keeps following its own copy */
	record DueDetail(long id, CustomerRef customer, long amountPaise, LocalDate dueDate, String status, Long policyId,
			String policyName, Instant paidAt, Instant createdAt, List<StepView> steps, List<MessageView> messages,
			List<PaymentView> payments) {
	}

	/** {@code action}: "mark-paid" or "cancel". */
	record BulkActionRequest(List<Long> ids, String action) {
	}

	/** @param skipped not found, or already paid or cancelled in a way the action cannot change */
	record BulkActionResult(int updated, int skipped) {
	}

}
