package com.paynudge.csvimport;

import java.util.Map;

import com.paynudge.common.ApiException;

import org.springframework.http.HttpStatus;

/** A problem with the file or the column choice as a whole (as opposed to one row). Always a 400. */
final class ImportException {

	static final String EMPTY_FILE = "EMPTY_FILE";

	static final String NOT_CSV = "NOT_CSV";

	static final String NO_ROWS = "NO_ROWS";

	static final String TOO_MANY_ROWS = "TOO_MANY_ROWS";

	static final String UNKNOWN_COLUMN = "UNKNOWN_COLUMN";

	static final String MISSING_COLUMNS = "MISSING_COLUMNS";

	static final String NOTHING_TO_IMPORT = "NOTHING_TO_IMPORT";

	private ImportException() {
	}

	static ApiException of(String code, String message) {
		return new ApiException(HttpStatus.BAD_REQUEST, code, message);
	}

	static ApiException of(String code, String message, Map<String, Object> details) {
		return new ApiException(HttpStatus.BAD_REQUEST, code, message, details);
	}

	static ApiException notCsv(String message) {
		return of(NOT_CSV, message);
	}

}
