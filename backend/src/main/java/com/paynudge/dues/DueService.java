package com.paynudge.dues;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.paynudge.common.ApiException;
import com.paynudge.common.DueEvents.DueChanged;
import com.paynudge.common.DueEvents.DuesChanged;
import com.paynudge.common.ErrorCodes;
import com.paynudge.common.Text;
import com.paynudge.customer.CustomerRepository;
import com.paynudge.dues.DueDtos.CreateDueRequest;
import com.paynudge.dues.DueDtos.DueDetail;
import com.paynudge.dues.DueDtos.BulkActionRequest;
import com.paynudge.dues.DueDtos.BulkActionResult;
import com.paynudge.dues.DueDtos.DuePage;
import com.paynudge.policy.PolicyService;
import com.paynudge.security.Caller;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dues and their schedules. A due copies its policy when it is created (later policy edits never change it) and
 * gets every step at once, in the same transaction.
 */
@Service
public class DueService {

	/** ₹1 crore: a guard against typos, matching the CSV import. */
	static final long MAX_AMOUNT_PAISE = 1_000_000_000L;

	/** The most dues one bulk action may change: a page of the list. */
	static final int MAX_BULK = 100;

	static final String DUE_PAID = "DUE_PAID";

	static final String DUE_CANCELLED = "DUE_CANCELLED";

	private final DueWriter writer;

	private final DueQueries queries;

	private final PolicyService policies;

	private final CustomerRepository customers;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	private final ApplicationEventPublisher events;

	DueService(DueWriter writer, DueQueries queries, PolicyService policies, CustomerRepository customers,
			JdbcTemplate jdbc, Clock clock, ApplicationEventPublisher events) {
		this.writer = writer;
		this.queries = queries;
		this.policies = policies;
		this.customers = customers;
		this.jdbc = jdbc;
		this.clock = clock;
		this.events = events;
	}

	/**
	 * One due imported from a CSV row or typed in on the import page; {@code importKey} fingerprints it so the same
	 * due is never added twice.
	 */
	public record ImportedDue(long customerId, long amountPaise, LocalDate dueDate, String importKey) {
	}

	/**
	 * @param ids the new dues, in the order given
	 * @param firstReminderAt when the earliest customer email goes out, or null if none will
	 */
	public record ImportOutcome(List<Long> ids, Instant firstReminderAt) {

		public int created() {
			return this.ids.size();
		}

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
		this.events.publishEvent(new DuesChanged(businessId));
		return new ImportOutcome(created.ids(), created.firstReminderAt());
	}

	/** The business's due with this import key, if any. */
	public Optional<Long> findByImportKey(long businessId, String importKey) {
		return this.jdbc
			.queryForList("SELECT id FROM dues WHERE business_id = ? AND import_key = ?", Long.class, businessId,
					importKey)
			.stream()
			.findFirst();
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
		this.events.publishEvent(new DuesChanged(caller.businessId()));
		return detail(caller, created.ids().get(0));
	}

	DuePage list(Caller caller, String filter, String search, int page, int size) {
		ZoneId zone = ZoneId.of(this.jdbc.queryForObject("SELECT timezone FROM businesses WHERE id = ?", String.class,
				caller.businessId()));
		return this.queries.list(caller.businessId(), DueFilter.from(filter), Text.stripToNull(search),
				this.clock.instant().atZone(zone).toLocalDate(), page, size);
	}

	/**
	 * Marks many dues paid, or cancels them, in one go. Dues the action cannot change (another business's, or already
	 * paid or cancelled) are skipped. The dues are locked in id order, so two bulk actions never deadlock.
	 */
	@Transactional
	BulkActionResult bulk(Caller caller, BulkActionRequest request) {
		boolean markPaid = "mark-paid".equals(request.action());
		if (!markPaid && !"cancel".equals(request.action())) {
			throw ApiException.invalidField("action", "Use mark-paid or cancel");
		}
		if (request.ids() == null || request.ids().isEmpty() || request.ids().size() > MAX_BULK) {
			throw ApiException.invalidField("ids", "Choose between 1 and " + MAX_BULK + " dues");
		}
		List<Map<String, Object>> found = this.jdbc.queryForList("""
				SELECT id, status FROM dues WHERE business_id = ? AND id = ANY (?)
				ORDER BY id FOR UPDATE""", caller.businessId(), request.ids().toArray(Long[]::new));
		int updated = 0;
		for (Map<String, Object> due : found) {
			long id = ((Number) due.get("id")).longValue();
			String status = (String) due.get("status");
			if (DueStatus.CLOSED.contains(status)) {
				continue;
			}
			if (markPaid) {
				settle(id, this.clock.instant());
			}
			else {
				this.jdbc.update("UPDATE dues SET status = ? WHERE id = ?", DueStatus.CANCELLED, id);
				stopReminders(id, "Due is cancelled");
			}
			updated++;
		}
		if (updated > 0) {
			this.events.publishEvent(new DuesChanged(caller.businessId()));
		}
		return new BulkActionResult(updated, new HashSet<>(request.ids()).size() - updated);
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
			settle(id, this.clock.instant());
		}
		return detail(caller, id);
	}

	/** A due as a payment sees it, locked by {@link #lockForPayment}. */
	public record PayableDue(long id, long amountPaise, String status) {
	}

	/**
	 * Finds the due a pay link belongs to and takes its row lock (the same lock a sending worker holds), so a payment
	 * and a reminder never overlap. Must run inside the caller's transaction.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public Optional<PayableDue> lockForPayment(String payToken) {
		return this.jdbc
			.query("SELECT id, amount_paise, status FROM dues WHERE pay_token = ? FOR UPDATE",
					(rs, row) -> new PayableDue(rs.getLong("id"), rs.getLong("amount_paise"), rs.getString("status")),
					payToken)
			.stream()
			.findFirst();
	}

	/** Marks a locked due paid and cancels every reminder still waiting. Must run inside the caller's transaction. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void settle(long dueId, Instant paidAt) {
		this.jdbc.update("UPDATE dues SET status = ?, paid_at = ? WHERE id = ?", DueStatus.PAID,
				paidAt.atOffset(ZoneOffset.UTC), dueId);
		stopReminders(dueId, "Due is paid");
		this.events.publishEvent(new DueChanged(dueId));
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
			this.events.publishEvent(new DueChanged(id));
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
