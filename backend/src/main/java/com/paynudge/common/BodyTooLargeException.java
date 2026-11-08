package com.paynudge.common;

import java.io.IOException;

/** A request body read past the size cap. The error handler answers 413 {@code BODY_TOO_LARGE}. */
public class BodyTooLargeException extends IOException {

	public BodyTooLargeException(String message) {
		super(message);
	}

}
