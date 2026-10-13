package com.paynudge.common;

import java.util.Map;

/**
 * The single error body returned by every endpoint: {@code { "code", "message", "details" }}.
 * {@code code} is a stable machine-readable identifier; {@code details} is never null.
 */
public record ApiError(String code, String message, Map<String, Object> details) {

	public ApiError {
		details = details == null ? Map.of() : Map.copyOf(details);
	}

	public static ApiError of(String code, String message) {
		return new ApiError(code, message, Map.of());
	}

}
