package com.paynudge.dues;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paynudge.common.ApiException;
import com.paynudge.common.ErrorCodes;
import com.paynudge.common.PageResponse;
import com.paynudge.customer.CustomerRepository;
import com.paynudge.dues.DueDtos.CreateDueRequest;
import com.paynudge.dues.DueDtos.DueDetail;
import com.paynudge.dues.DueDtos.DueSummary;
import com.paynudge.policy.PolicyService;
import com.paynudge.security.Caller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dues and their schedules. A due copies its policy when it is created (later policy edits never change it) and
 * gets every step at once, in the same transaction.
 */
@Service
public class DueService {

	/** ₹1 crore: a guard against typos, matching the CSV import. */
	static final long MAX_AMOUNT_PAISE = 1_000_000_000L;

	static final String DUE_PAID = "DUE_PAID";

	static final String DUE_CANCELLED = "DUE_CANCELLED";

	private final DueWriter writer;

	private final DueQueries queries;

	private final PolicyService policies;

	private final CustomerRepository customers;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	DueService(DueWriter writer, DueQueries queries, PolicyService policies, CustomerRepository customers,
			JdbcTemplate jdbc, Clock clock) {
		this.writer = writer;
		this.queries = queries;
		this.policies = policies;
		this.customers = customers;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** One due imported from a CSV row; {@code importKey} fingerprints the row so a re-import skips it. */
	public record ImportedDue(long customerId, long amountPaise, LocalDate dueDate, String importKey) {
	}

	/** @param firstReminderAt when the earliest customer email goes out, or null if none will */
	public record ImportOutcome(int created, Instant firstReminderAt) {
	}

	/**
	 * Creates the imported dues in the caller's transaction. The caller has already left out rows whose import key
	 * exists (see {@link #existingImportKeys}).
	 */
	@Transactional
	public ImportOutcome createImported(long businessId, long policyId, List<ImportedDue> dues) {
		PolicyService.Snapshot policy = this.policies.snapshot(businessId, policyId);
		DueWriter.Created created = this.writer.create(businessId, policy, dues.stream()
			.map((due) -> new DueWriter.NewDue(due.customerId(), due.amountPaise(), due.dueDate(), due.importKey()))
			.toList());
		return new ImportOutcome(created.ids().size(), created.firstReminderAt());
	}

	/** Which of these import keys already belong to one of the business's dues. */
	public Set<String> existingImportKeys(long businessId, Collection<String> keys) {
		if (keys.isEmpty()) {
			return Set.of();
		}
		return new HashSet<>(this.jdbc.queryForList(
				"SELECT import_key FROM dues WHERE business_id = ? AND import_key = ANY (?)", String.class, businessId,
				keys.toArray(String[]::new)));
	}

	/** Serializes imports per business, so two uploads of one file cannot both create its dues. */
	public void lockImports(long businessId) {
		this.jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", (rs) -> null, "import:" + businessId);
	}

	@Transactional
	DueDetail create(Caller caller, CreateDueRequest request) {
		Map<String, Object> errors = new LinkedHashMap<>();
		if (request.customerId() == null
				|| this.customers.findByIdAndBusinessId(request.customerId(), caller.businessId()).isEmpty()) {
			errors.put("customerId", "Choose one of your customers");
		}
		if (request.amountPaise() == null || request.amountPaise() <= 0) {
			errors.put("amountPaise", "Enter an amount more than zero");
		}
		else if (request.amountPaise() > MAX_AMOUNT_PAISE) {
			errors.put("amountPaise", "Amounts above ₹1 crore are not supported");
		}
		if (request.dueDate() == null) {
			errors.put("dueDate", "Enter the due date");
		}
		else if (request.dueDate().getYear() < 2000 || request.dueDate().getYear() > 2100) {
			errors.put("dueDate", "Check the year in this date");
		}
		if (request.policyId() == null) {
			errors.put("policyId", "Choose a reminder policy");
		}
		if (!errors.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "Request validation failed",
					errors);
		}
		PolicyService.Snapshot policy = this.policies.snapshot(caller.businessId(), request.policyId());
		DueWriter.Created created = this.writer.create(caller.businessId(), policy, List
			.of(new DueWriter.NewDue(request.customerId(), request.amountPaise(), request.dueDate(), null)));
		return detail(caller, created.ids().get(0));
	}

	PageResponse<DueSummary> list(Caller caller, int page, int size) {
		return this.queries.list(caller.businessId(), page, size);
	}

	DueDetail detail(Caller caller, long id) {
		return this.queries.detail(caller.businessId(), id).orElseThrow(() -> ApiException.notFound("Due not found"));
	}

	/**
	 * Paid outside PayNudge (cash, a bank transfer). Takes the due's row lock, as a worker does before sending, so a
	 * reminder that is going out right now finishes first and none starts afterwards. Repeating it changes nothing.
	 */
	@Transactional
	DueDetail markPaid(Caller caller, long id) {
		String status = lockStatus(caller.businessId(), id);
		if (DueStatus.CANCELLED.equals(status)) {
			throw ApiException.conflict(DUE_CANCELLED, "This due was cancelled, so it cannot be marked paid");
		}
		if (!DueStatus.PAID.equals(status)) {
			this.jdbc.update("UPDATE dues SET status = ?, paid_at = ? WHERE id = ?", DueStatus.PAID,
					this.clock.instant().atOffset(ZoneOffset.UTC), id);
			stopReminders(id, "Due is paid");
		}
		return detail(caller, id);
	}

	/** The owner calls the due off: no more reminders. Repeating it changes nothing. */
	@Transactional
	DueDetail cancel(Caller caller, long id) {
		String status = lockStatus(caller.businessId(), id);
		if (DueStatus.PAID.equals(status)) {
			throw ApiException.conflict(DUE_PAID, "This due is already paid");
		}
		if (!DueStatus.CANCELLED.equals(status)) {
			this.jdbc.update("UPDATE dues SET status = ? WHERE id = ?", DueStatus.CANCELLED, id);
			stopReminders(id, "Due is cancelled");
		}
		return detail(caller, id);
	}

	private String lockStatus(long businessId, long id) {
		return this.jdbc
			.queryForList("SELECT status FROM dues WHERE id = ? AND business_id = ? FOR UPDATE", String.class, id,
					businessId)
			.stream()
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("Due not found"));
	}

	/** Cancels the steps still waiting. A step a worker holds right now cancels itself when it sees the new status. */
	private void stopReminders(long dueId, String reason) {
		this.jdbc.update("UPDATE due_steps SET status = ?, error = ?, note = NULL WHERE due_id = ? AND status = ?",
				StepStatus.CANCELLED, reason, dueId, StepStatus.PENDING);
	}

}
