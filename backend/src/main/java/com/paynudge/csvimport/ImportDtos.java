package com.paynudge.csvimport;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Response bodies for the import endpoints. */
final class ImportDtos {

	private ImportDtos() {
	}

	/**
	 * What importing this file would do. {@code missingColumns} lists fields with no column yet ("contact" means
	 * neither email nor phone); until it is empty, rows are not checked and {@code rows} is empty.
	 */
	record ImportPreview(List<String> headers, ColumnMapping mapping, List<String> missingColumns, Summary summary,
			List<PreviewRow> rows) {
	}

	/**
	 * {@code rows} counts every data row; {@code valid} those without errors, of which {@code alreadyImported} were
	 * imported before (or repeat an earlier row) and will be skipped. Customer counts cover the rows to import.
	 */
	record Summary(int rows, int valid, int invalid, int alreadyImported, int newCustomers, int existingCustomers) {
	}

	/**
	 * One data row. {@code row} is its spreadsheet row number. {@code values} holds the raw cells by field.
	 * {@code errors} (field to message) is empty for a row that can be imported. {@code note} explains a skipped row
	 * or one that shares its customer with an earlier row.
	 */
	record PreviewRow(int row, Map<String, String> values, Long amountPaise, LocalDate dueDate,
			Map<String, String> errors, String note, boolean existingCustomer, boolean alreadyImported) {
	}

	/**
	 * @param importedRows dues created, each with its reminders scheduled
	 * @param alreadyImported valid rows skipped because they were imported before
	 * @param skippedRows rows with errors
	 * @param firstReminderAt when the earliest new reminder goes out, or null if none will
	 */
	record ImportResult(int importedRows, int alreadyImported, int skippedRows, int customersCreated,
			int customersMatched, Instant firstReminderAt) {
	}

	/**
	 * One due typed in by hand, as typed: the amount in rupees ({@code 8,500} or {@code 1500.50}) and the due date as
	 * {@code 2026-11-05} (or any way a CSV can write it). Checked by the same rules as a CSV row.
	 */
	record ManualEntryRequest(String name, String email, String phone, String amount, String dueDate, Long policyId) {
	}

	/**
	 * @param newCustomer false when the email (or, without one, the phone) belongs to a customer already in the list,
	 * who keeps their own name and details
	 * @param optedOut the customer has unsubscribed, so no reminders will go out
	 * @param firstReminderAt when the first email goes out, or null if none will
	 */
	record ManualEntryResult(long dueId, long customerId, String customerName, boolean newCustomer, boolean optedOut,
			long amountPaise, LocalDate dueDate, Instant firstReminderAt) {
	}

}
