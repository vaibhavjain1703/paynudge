package com.paynudge.csvimport;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paynudge.csvimport.CsvReader.CsvRow;
import com.paynudge.csvimport.CsvReader.CsvTable;
import com.paynudge.csvimport.ImportDtos.ImportPreview;
import com.paynudge.csvimport.ImportDtos.ImportResult;
import com.paynudge.csvimport.ImportDtos.PreviewRow;
import com.paynudge.csvimport.ImportDtos.Summary;
import com.paynudge.customer.CustomerFields;
import com.paynudge.customer.CustomerImporter;
import com.paynudge.security.Caller;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CSV import in two calls: preview (read, match columns, check every row, change nothing) and commit (the same checks
 * again on the server, then import every valid row). The client never sends checked data back; the file is the
 * input both times.
 */
@Service
class ImportService {

	private final CustomerImporter customers;

	ImportService(CustomerImporter customers) {
		this.customers = customers;
	}

	ImportPreview preview(Caller caller, byte[] file, ColumnMapping requested) {
		Analysis analysis = analyze(caller.businessId(), file, requested);
		return new ImportPreview(analysis.table().headers(), analysis.mapping(), analysis.missing(),
				analysis.summary(), analysis.rows().stream().map(Checked::toPreview).toList());
	}

	@Transactional
	ImportResult commit(Caller caller, byte[] file, ColumnMapping mapping) {
		if (mapping == null) {
			throw ImportException.of(ImportException.MISSING_COLUMNS, "Choose which column is which before importing");
		}
		Analysis analysis = analyze(caller.businessId(), file, mapping);
		if (!analysis.missing().isEmpty()) {
			throw ImportException.of(ImportException.MISSING_COLUMNS, "Choose a column for every required field",
					Map.of("missingColumns", analysis.missing()));
		}
		List<CustomerFields> valid = analysis.rows()
			.stream()
			.filter(Checked::valid)
			.map(Checked::customer)
			.toList();
		if (valid.isEmpty()) {
			throw ImportException.of(ImportException.NOTHING_TO_IMPORT,
					"None of the rows can be imported yet. Fix the errors in the file and try again.");
		}
		CustomerImporter.Imported imported = this.customers.findOrCreate(caller.businessId(), valid);
		int customers = imported.idsByKey().size();
		return new ImportResult(valid.size(), analysis.rows().size() - valid.size(), imported.created(),
				customers - imported.created());
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
			return new Analysis(table, mapping, missing, List.of(), new Summary(table.rows().size(), 0, 0, 0, 0));
		}

		Map<String, Integer> columns = new HashMap<>();
		mapping.columns().forEach((field, header) -> columns.put(field, table.headers().indexOf(header)));
		List<Checked> rows = table.rows().stream().map((row) -> check(row, columns)).toList();

		List<CustomerFields> validCustomers = rows.stream().filter(Checked::valid).map(Checked::customer).toList();
		Set<String> existing = this.customers.existingIds(businessId, validCustomers).keySet();
		Map<String, Integer> firstRowByKey = new HashMap<>();
		List<Checked> annotated = new ArrayList<>(rows.size());
		Set<String> newKeys = new LinkedHashSet<>();
		Set<String> existingKeys = new LinkedHashSet<>();
		for (Checked row : rows) {
			if (!row.valid()) {
				annotated.add(row);
				continue;
			}
			String key = row.customer().key();
			boolean known = existing.contains(key);
			(known ? existingKeys : newKeys).add(key);
			Integer first = firstRowByKey.putIfAbsent(key, row.row().number());
			String note = (first == null) ? null : "Same customer as row " + first;
			annotated.add(row.with(note, known));
		}
		int valid = validCustomers.size();
		Summary summary = new Summary(rows.size(), valid, rows.size() - valid, newKeys.size(), existingKeys.size());
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
		return new Checked(row, values, customer.fields(), amountPaise, dueDate, Map.copyOf(errors), null, false);
	}

	private record Analysis(CsvTable table, ColumnMapping mapping, List<String> missing, List<Checked> rows,
			Summary summary) {
	}

	private record Checked(CsvRow row, Map<String, String> values, CustomerFields customer, Long amountPaise,
			LocalDate dueDate, Map<String, String> errors, String note, boolean existingCustomer) {

		boolean valid() {
			return this.errors.isEmpty();
		}

		Checked with(String note, boolean existingCustomer) {
			return new Checked(this.row, this.values, this.customer, this.amountPaise, this.dueDate, this.errors, note,
					existingCustomer);
		}

		PreviewRow toPreview() {
			return new PreviewRow(this.row.number(), this.values, this.amountPaise, this.dueDate, this.errors,
					this.note, this.existingCustomer);
		}

	}

}
