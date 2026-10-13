package com.paynudge.csvimport;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Reads an amount in rupees the way people type it in spreadsheets ({@code ₹1,50,000}, {@code Rs. 1500/-},
 * {@code 1500.50}) into exact integer paise. Never goes through floating point.
 */
final class Amounts {

	/** A guard against typos (an extra zero or two), not a business rule: ₹1 crore. */
	static final BigDecimal MAX_RUPEES = new BigDecimal("10000000");

	private static final Pattern CURRENCY_PREFIX = Pattern.compile("(?i)^(₹|rs\\.?|inr)\\s*");

	private static final Pattern CURRENCY_SUFFIX = Pattern.compile("(?i)\\s*(/-|₹|rs\\.?|inr)$");

	private static final Pattern NUMBER = Pattern.compile("\\d+(\\.\\d+)?");

	private Amounts() {
	}

	static long parsePaise(String raw) {
		String value = (raw == null) ? "" : raw.strip();
		if (value.isEmpty()) {
			throw new CellException("Enter the amount");
		}
		String cleaned = CURRENCY_SUFFIX.matcher(CURRENCY_PREFIX.matcher(value).replaceFirst("")).replaceFirst("")
			.replace(",", "")
			.replace(" ", "");
		if (cleaned.startsWith("-")) {
			throw new CellException("The amount must be more than zero");
		}
		if (!NUMBER.matcher(cleaned).matches()) {
			throw new CellException("Use a number like 1500 or 1,500.50");
		}
		BigDecimal rupees = new BigDecimal(cleaned).stripTrailingZeros();
		if (rupees.scale() > 2) {
			throw new CellException("Use at most 2 decimal places");
		}
		if (rupees.signum() <= 0) {
			throw new CellException("The amount must be more than zero");
		}
		if (rupees.compareTo(MAX_RUPEES) > 0) {
			throw new CellException("Amounts above ₹1 crore are not supported");
		}
		return rupees.movePointRight(2).longValueExact();
	}

}
