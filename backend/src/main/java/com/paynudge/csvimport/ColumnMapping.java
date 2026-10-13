package com.paynudge.csvimport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.paynudge.common.Text;

/**
 * Which column of the file holds each field, by header text (null when the file has no such column). Name, amount,
 * due date and at least one of email or phone are required.
 */
record ColumnMapping(String name, String email, String phone, String amount, String dueDate) {

	ColumnMapping {
		name = Text.stripToNull(name);
		email = Text.stripToNull(email);
		phone = Text.stripToNull(phone);
		amount = Text.stripToNull(amount);
		dueDate = Text.stripToNull(dueDate);
	}

	/** Field names (as in this record, plus "contact" for email-or-phone) that still need a column. */
	List<String> missing() {
		List<String> missing = new ArrayList<>();
		if (this.name == null) {
			missing.add("name");
		}
		if (this.email == null && this.phone == null) {
			missing.add("contact");
		}
		if (this.amount == null) {
			missing.add("amount");
		}
		if (this.dueDate == null) {
			missing.add("dueDate");
		}
		return missing;
	}

	/** Field name to header, for the fields that have a column. */
	Map<String, String> columns() {
		Map<String, String> columns = new LinkedHashMap<>();
		put(columns, "name", this.name);
		put(columns, "email", this.email);
		put(columns, "phone", this.phone);
		put(columns, "amount", this.amount);
		put(columns, "dueDate", this.dueDate);
		return columns;
	}

	/** Rejects a mapping that names a header the file does not have. */
	void checkAgainst(List<String> headers) {
		Map<String, Object> unknown = new LinkedHashMap<>();
		columns().forEach((field, header) -> {
			if (!headers.contains(header)) {
				unknown.put(field, "This file has no column called \"" + header + "\"");
			}
		});
		if (!unknown.isEmpty()) {
			throw ImportException.of(ImportException.UNKNOWN_COLUMN, "Some chosen columns are not in this file",
					unknown);
		}
	}

	private static void put(Map<String, String> columns, String field, String header) {
		if (header != null) {
			columns.put(field, header);
		}
	}

}
