package com.paynudge.csvimport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.paynudge.common.ApiException;
import com.paynudge.common.ErrorCodes;
import com.paynudge.csvimport.CsvReader.CsvRow;
import com.paynudge.csvimport.CsvReader.CsvTable;
import com.paynudge.csvimport.ImportDtos.ImportPreview;
import com.paynudge.csvimport.ImportDtos.ImportResult;
import com.paynudge.csvimport.ImportDtos.ManualEntryRequest;
import com.paynudge.csvimport.ImportDtos.ManualEntryResult;
import com.paynudge.csvimport.ImportDtos.PreviewRow;
import com.paynudge.csvimport.ImportDtos.Summary;
import com.paynudge.customer.Customer;
import com.paynudge.customer.CustomerFields;
import com.paynudge.customer.CustomerImporter;
import com.paynudge.customer.CustomerRepository;
import com.paynudge.dues.DueService;
import com.paynudge.security.Caller;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CSV import in two calls: preview (read, match columns, check every row, change nothing) and commit (the same checks
 * again on the server, then create the customers and a scheduled due for every valid row, following the chosen
 * policy). The client never sends checked data back; the file is the input both times.
 * <p>
 * Each row has a fingerprint (its tidied name, contact, amount and due date). A row whose fingerprint is already on a
 * due was imported before and is skipped, so importing the same file twice never reminds anyone twice.
 * <p>
 * A due can also be typed in by hand ({@link #add}): it is checked like a row and gets the same fingerprint.
 */
@Service
class ImportService {

	static final String DUE_EXISTS = "DUE_EXISTS";

	private final CustomerImporter customers;

	private final CustomerRepository customerRepository;

	private final DueService dues;

	ImportService(CustomerImporter customers, CustomerRepository customerRepository, DueService dues) {
		this.customers = customers;
		this.customerRepository = customerRepository;
		this.dues = dues;
	}

	/**
	 * One due typed in on the import page. It follows the CSV rules: the same checks, the same match with a customer
	 * already in the list (who keeps their own details), and the same fingerprint, so the same due typed twice, or
	 * typed after it was imported, is refused instead of reminding anyone twice.
	 */
	@Transactional
	ManualEntryResult add(Caller caller, ManualEntryRequest request) {
		Map<String, String> values = new HashMap<>();
		values.put("name", request.name());
		values.put("email", request.email());
		values.put("phone", request.phone());
		values.put("amount", request.amount());
		values.put("dueDate", request.dueDate());
		Entry entry = Entry.check(values, true);
		Map<String, Object> errors = new LinkedHashMap<>(entry.errors());
		if (request.policyId() == null) {
			errors.put("policyId", "Choose a reminder policy");
		}
		if (!errors.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "Request validation failed",
					errors);
		}

		long businessId = caller.businessId();
		this.dues.lockImports(businessId);
		this.dues.findByImportKey(businessId, entry.importKey()).ifPresent((existing) -> {
			throw new ApiException(HttpStatus.CONFLICT, DUE_EXISTS, "This due is already in your list",
					Map.of("dueId", existing));
		});
		CustomerImporter.Imported imported = this.customers.findOrCreate(businessId, List.of(entry.customer()));
		long customerId = imported.idsByKey().get(entry.customer().key());
		DueService.ImportOutcome outcome = this.dues.createImported(businessId, request.policyId(), List
			.of(new DueService.ImportedDue(customerId, entry.amountPaise(), entry.dueDate(), entry.importKey())));
		Customer customer = this.customerRepository.findByIdAndBusinessId(customerId, businessId).orElseThrow();
		return new ManualEntryResult(outcome.ids().get(0), customerId, customer.getName(), imported.created() > 0,
				customer.isOptedOut(), entry.amountPaise(), entry.dueDate(),
				customer.isOptedOut() ? null : outcome.firstReminderAt());
	}

	ImportPreview preview(Caller caller, byte[] file, ColumnMapping requested) {
		Analysis analysis = analyze(caller.businessId(), file, requested);
		return new ImportPreview(analysis.table().headers(), analysis.mapping(), analysis.missing(),
				analysis.summary(), analysis.rows().stream().map(Checked::toPreview).toList());
	}

	@Transactional
	ImportResult commit(Caller caller, byte[] file, ColumnMapping mapping, Long policyId) {
		if (mapping == null) {
			throw ImportException.of(ImportException.MISSING_COLUMNS, "Choose which column is which before importing");
		}
		if (policyId == null) {
			throw ApiException.invalidField("policyId", "Choose a reminder policy");
		}
		this.dues.lockImports(caller.businessId());
		Analysis analysis = analyze(caller.businessId(), file, mapping);
		if (!analysis.missing().isEmpty()) {
			throw ImportException.of(ImportException.MISSING_COLUMNS, "Choose a column for every required field",
					Map.of("missingColumns", analysis.missing()));
		}
		List<Checked> valid = analysis.rows().stream().filter(Checked::valid).toList();
		if (valid.isEmpty()) {
			throw ImportException.of(ImportException.NOTHING_TO_IMPORT,
					"None of the rows can be imported yet. Fix the errors in the file and try again.");
		}
		List<Checked> fresh = valid.stream().filter((row) -> !row.alreadyImported()).toList();
		CustomerImporter.Imported imported = this.customers.findOrCreate(caller.businessId(),
				fresh.stream().map(Checked::customer).toList());
		DueService.ImportOutcome outcome = this.dues.createImported(caller.businessId(), policyId, fresh.stream()
			.map((row) -> new DueService.ImportedDue(imported.idsByKey().get(row.customer().key()), row.amountPaise(),
					row.dueDate(), row.importKey()))
			.toList());
		int customers = imported.idsByKey().size();
		return new ImportResult(outcome.created(), valid.size() - fresh.size(), analysis.rows().size() - valid.size(),
				imported.created(), customers - imported.created(), outcome.firstReminderAt());
	}

	private Analysis analyze(long businessId, byte[] file, ColumnMapping requested) {
		CsvTable table = CsvReader.read(file);
		ColumnMapping mapping;
		if (requested != null) {
			requested.checkAgainst(table.headers());
			mapping = requested;
		}
		else {
			mapping = ColumnMatcher.guess(table.headers());
		}
		List<String> missing = mapping.missing();
		if (!missing.isEmpty()) {
			return new Analysis(table, mapping, missing, List.of(),
					new Summary(table.rows().size(), 0, 0, 0, 0, 0));
		}

		Map<String, Integer> columns = new HashMap<>();
		mapping.columns().forEach((field, header) -> columns.put(field, table.headers().indexOf(header)));
		List<Checked> rows = table.rows().stream().map((row) -> check(row, columns)).toList();

		List<Checked> validRows = rows.stream().filter(Checked::valid).toList();
		Set<String> importedBefore = this.dues.existingImportKeys(businessId,
				validRows.stream().map(Checked::importKey).toList());
		Set<String> knownCustomers = this.customers
			.existingIds(businessId, validRows.stream().map(Checked::customer).toList())
			.keySet();

		Map<String, Integer> firstRowByImportKey = new HashMap<>();
		Map<String, Integer> firstRowByCustomer = new HashMap<>();
		Set<String> newCustomers = new LinkedHashSet<>();
		Set<String> existingCustomers = new LinkedHashSet<>();
		List<Checked> annotated = new ArrayList<>(rows.size());
		int alreadyImported = 0;
		for (Checked row : rows) {
			if (!row.valid()) {
				annotated.add(row);
				continue;
			}
			String customerKey = row.customer().key();
			boolean known = knownCustomers.contains(customerKey);
			Integer duplicateOf = firstRowByImportKey.putIfAbsent(row.importKey(), row.row().number());
			if (duplicateOf != null || importedBefore.contains(row.importKey())) {
				alreadyImported++;
				String note = (duplicateOf != null) ? "Duplicate of row " + duplicateOf : "Already imported";
				annotated.add(row.with(note, known, true));
				continue;
			}
			(known ? existingCustomers : newCustomers).add(customerKey);
			Integer sameCustomer = firstRowByCustomer.putIfAbsent(customerKey, row.row().number());
			annotated.add(row.with((sameCustomer == null) ? null : "Same customer as row " + sameCustomer, known,
					false));
		}
		int valid = validRows.size();
		Summary summary = new Summary(rows.size(), valid, rows.size() - valid, alreadyImported, newCustomers.size(),
				existingCustomers.size());
		return new Analysis(table, mapping, missing, annotated, summary);
	}

	private static Checked check(CsvRow row, Map<String, Integer> columns) {
		Map<String, String> values = new LinkedHashMap<>();
		columns.forEach((field, column) -> values.put(field, row.cell(column)));
		Entry entry = Entry.check(values, columns.containsKey("email"));
		return new Checked(row, values, entry.customer(), entry.amountPaise(), entry.dueDate(), entry.errors(),
				entry.importKey(), null, false, false);
	}

	/** The fingerprint, from tidied values: a re-export with different spacing or case still matches. */
	private static String fingerprint(CustomerFields customer, long amountPaise, LocalDate dueDate) {
		String row = String.join("\n", customer.name().toLowerCase(Locale.ROOT), String.valueOf(customer.email()),
				String.valueOf(customer.phone()), Long.toString(amountPaise), dueDate.toString());
		try {
			return HexFormat.of()
				.formatHex(MessageDigest.getInstance("SHA-256").digest(row.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is always available", ex);
		}
	}

	private record Analysis(CsvTable table, ColumnMapping mapping, List<String> missing, List<Checked> rows,
			Summary summary) {
	}

	/**
	 * One due's values as typed, in a CSV row or the form, checked and tidied. {@code errors} (field to message) is
	 * empty when it can be added; the customer, amount, date and fingerprint are then all set.
	 */
	private record Entry(CustomerFields customer, Long amountPaise, LocalDate dueDate, Map<String, String> errors,
			String importKey) {

		/** @param emailColumn false for a file with only a phone column */
		static Entry check(Map<String, String> values, boolean emailColumn) {
			Map<String, String> errors = new LinkedHashMap<>();
			CustomerFields.Parsed customer = CustomerFields.parse(values.get("name"), values.get("email"),
					values.get("phone"));
			errors.putAll(customer.errors());
			if (errors.containsKey("email") && !emailColumn) {
				// "Add an email or a phone number" belongs to whichever contact column the file has.
				errors.put("phone", errors.remove("email"));
			}
			Long amountPaise = null;
			try {
				amountPaise = Amounts.parsePaise(values.get("amount"));
			}
			catch (CellException ex) {
				errors.put("amount", ex.getMessage());
			}
			LocalDate dueDate = null;
			try {
				dueDate = DueDates.parse(values.get("dueDate"));
			}
			catch (CellException ex) {
				errors.put("dueDate", ex.getMessage());
			}
			String importKey = errors.isEmpty() ? fingerprint(customer.fields(), amountPaise, dueDate) : null;
			return new Entry(customer.fields(), amountPaise, dueDate, Map.copyOf(errors), importKey);
		}

	}

	private record Checked(CsvRow row, Map<String, String> values, CustomerFields customer, Long amountPaise,
			LocalDate dueDate, Map<String, String> errors, String importKey, String note, boolean existingCustomer,
			boolean alreadyImported) {

		boolean valid() {
			return this.errors.isEmpty();
		}

		Checked with(String note, boolean existingCustomer, boolean alreadyImported) {
			return new Checked(this.row, this.values, this.customer, this.amountPaise, this.dueDate, this.errors,
					this.importKey, note, existingCustomer, alreadyImported);
		}

		PreviewRow toPreview() {
			return new PreviewRow(this.row.number(), this.values, this.amountPaise, this.dueDate, this.errors,
					this.note, this.existingCustomer, this.alreadyImported);
		}

	}

}
