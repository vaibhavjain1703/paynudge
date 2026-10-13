package com.paynudge.csvimport;

/** One cell's value cannot be used. The message is shown to the owner next to that cell. */
class CellException extends RuntimeException {

	CellException(String message) {
		super(message, null, false, false);
	}

}
