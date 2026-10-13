package com.paynudge.common;

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

}
