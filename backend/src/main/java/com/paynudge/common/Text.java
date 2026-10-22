package com.paynudge.common;

import java.util.Locale;

/** Small helpers for user-entered text. */
public final class Text {

	private Text() {
	}

	public static String strip(String value) {
		return (value == null) ? null : value.strip();
	}

	/** Strips the value, and turns an empty result into null (for optional fields). */
	public static String stripToNull(String value) {
		String stripped = strip(value);
		return (stripped == null || stripped.isEmpty()) ? null : stripped;
	}

	/**
	 * A lower-case SQL LIKE pattern matching text that contains {@code search} literally, with {@code \} as the
	 * escape character (so a search for "50%" finds "50%", not everything starting with "50").
	 */
	public static String containsPattern(String search) {
		String escaped = search.toLowerCase(Locale.ROOT)
			.replace("\\", "\\\\")
			.replace("%", "\\%")
			.replace("_", "\\_");
		return "%" + escaped + "%";
	}

}
