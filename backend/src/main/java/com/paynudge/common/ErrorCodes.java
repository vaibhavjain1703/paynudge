package com.paynudge.common;

/** Error codes shared across modules. Module-specific codes live next to the code that throws them. */
public final class ErrorCodes {

	public static final String VALIDATION_FAILED = "VALIDATION_FAILED";

	public static final String MALFORMED_REQUEST = "MALFORMED_REQUEST";

	public static final String UNAUTHORIZED = "UNAUTHORIZED";

	public static final String FORBIDDEN = "FORBIDDEN";

	public static final String NOT_FOUND = "NOT_FOUND";

	public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";

	public static final String UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";

	public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";

	public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

	private ErrorCodes() {
	}

	/** The generic code for a status that has no more specific one. */
	public static String forStatus(int status) {
		return switch (status) {
			case 400 -> MALFORMED_REQUEST;
			case 401 -> UNAUTHORIZED;
			case 403 -> FORBIDDEN;
			case 404 -> NOT_FOUND;
			case 405 -> METHOD_NOT_ALLOWED;
			case 415 -> UNSUPPORTED_MEDIA_TYPE;
			default -> status >= 500 ? INTERNAL_ERROR : "HTTP_" + status;
		};
	}

}
