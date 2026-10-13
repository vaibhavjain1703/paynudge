package com.paynudge.csvimport;

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

	/** {@code rows} counts every data row; new and existing customers count distinct customers among valid rows. */
	record Summary(int rows, int valid, int invalid, int newCustomers, int existingCustomers) {
	}

	/**
	 * One data row. {@code row} is its spreadsheet row number. {@code values} holds the raw cells by field.
	 * {@code errors} (field to message) is empty for a row that will be imported. {@code note} explains a row that
	 * shares its customer with an earlier row.
	 */
	record PreviewRow(int row, Map<String, String> values, Long amountPaise, LocalDate dueDate,
			Map<String, String> errors, String note, boolean existingCustomer) {
	}

	record ImportResult(int importedRows, int skippedRows, int customersCreated, int customersMatched) {
	}

}
