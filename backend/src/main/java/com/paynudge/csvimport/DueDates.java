package com.paynudge.csvimport;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Reads a due date as Indian spreadsheets write it. Numeric dates are day first ({@code 05/11/2026} is 5 November);
 * ISO dates ({@code 2026-11-05}) and written months ({@code 5 Nov 2026}, {@code 05-Nov-26}, {@code Nov 5, 2026})
 * work too. A time of day after the date, as Excel sometimes exports, is ignored.
 */
final class DueDates {

	private static final List<DateTimeFormatter> FORMATS = List
		.of("uuuu-M-d", "d/M/uuuu", "d-M-uuuu", "d.M.uuuu", "d/M/uu", "d-M-uu", "d.M.uu", "d MMM uuuu", "d-MMM-uuuu",
				"d MMM uu", "d-MMM-uu", "d MMMM uuuu", "d-MMMM-uuuu", "d MMM, uuuu", "MMM d, uuuu", "MMMM d, uuuu",
				"MMM d uuuu", "MMMM d uuuu")
		.stream()
		.map((pattern) -> new DateTimeFormatterBuilder().parseCaseInsensitive()
			.appendPattern(pattern)
			.toFormatter(Locale.ENGLISH)
			.withResolverStyle(ResolverStyle.STRICT))
		.toList();

	private static final Pattern TRAILING_TIME = Pattern.compile("[ T]\\d{1,2}:\\d{2}(:\\d{2}(\\.\\d+)?)?\\s*(?i:am|pm)?$");

	private static final Pattern ORDINAL = Pattern.compile("(\\d)(?i:st|nd|rd|th)\\b");

	private static final Pattern SPACES = Pattern.compile("\\s+");

	private DueDates() {
	}

	static LocalDate parse(String raw) {
		String value = (raw == null) ? "" : raw.strip();
		if (value.isEmpty()) {
			throw new CellException("Enter the due date");
		}
		value = TRAILING_TIME.matcher(value).replaceFirst("");
		value = ORDINAL.matcher(value).replaceAll("$1");
		value = SPACES.matcher(value).replaceAll(" ");
		for (DateTimeFormatter format : FORMATS) {
			try {
				LocalDate date = LocalDate.parse(value, format);
				if (date.getYear() < 2000 || date.getYear() > 2100) {
					throw new CellException("Check the year in this date");
				}
				return date;
			}
			catch (DateTimeParseException ex) {
				// try the next format
			}
		}
		throw new CellException("Use a date like 05/11/2026 (day/month/year)");
	}

}
