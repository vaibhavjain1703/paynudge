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
import com.paynudge.csvimport.CsvReader.CsvRow;
import com.paynudge.csvimport.CsvReader.CsvTable;
import com.paynudge.csvimport.ImportDtos.ImportPreview;
import com.paynudge.csvimport.ImportDtos.ImportResult;
import com.paynudge.csvimport.ImportDtos.PreviewRow;
import com.paynudge.csvimport.ImportDtos.Summary;
import com.paynudge.customer.CustomerFields;
import com.paynudge.customer.CustomerImporter;
import com.paynudge.dues.DueService;
import com.paynudge.security.Caller;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CSV import in two calls: preview (read, match columns, check every row, change nothing) and commit (the same checks
 * again on the server, then create the customers and a scheduled due for every valid row, following the chosen
 * policy). The client never sends checked data back; the file is the input both times.
 * <p>
 * Each row has a fingerprint (its tidied name, contact, amount and due date). A row whose fingerprint is already on a
 * due was imported before and is skipped, so importing the same file twice never reminds anyone twice.
 */
@Service
class ImportService {

	private final CustomerImporter customers;

	private final DueService dues;

	ImportService(CustomerImporter customers, DueService dues) {
		this.customers = customers;
		this.dues = dues;
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
		Map<String, String> errors = new LinkedHashMap<>();

		CustomerFields.Parsed customer = CustomerFields.parse(values.get("name"), values.get("email"),
				values.get("phone"));
		errors.putAll(customer.errors());
		if (errors.containsKey("email") && !columns.containsKey("email")) {
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
		String importKey = errors.isEmpty() ? importKey(customer.fields(), amountPaise, dueDate) : null;
		return new Checked(row, values, customer.fields(), amountPaise, dueDate, Map.copyOf(errors), importKey, null,
				false, false);
	}

	/** The row's fingerprint, from tidied values: a re-export with different spacing or case still matches. */
	private static String importKey(CustomerFields customer, long amountPaise, LocalDate dueDate) {
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
