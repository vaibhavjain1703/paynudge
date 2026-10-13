package com.paynudge.customer;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.paynudge.common.Text;

/**
 * A customer's name and contact details, cleaned up the same way whether they come from the customer form or a CSV
 * row. Emails are trimmed and lower-cased. Phones are stored as {@code +<country code><number>}; a bare 10-digit
 * number is taken as an Indian mobile. Every customer needs an email or a phone.
 */
public record CustomerFields(String name, String email, String phone) {

	static final int MAX_NAME_LENGTH = 200;

	static final int MAX_EMAIL_LENGTH = 255;

	private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

	private static final Pattern PHONE_CHARACTERS = Pattern.compile("\\+?[\\d\\s().-]+");

	private static final Pattern INDIAN_MOBILE = Pattern.compile("[6-9]\\d{9}");

	/** Cleans the raw values. Problems come back keyed by field ("name", "email" or "phone"), never thrown. */
	public static Parsed parse(String rawName, String rawEmail, String rawPhone) {
		Map<String, String> errors = new LinkedHashMap<>();
		String name = name(rawName, errors);
		String email = email(rawEmail, errors);
		String phone = phone(rawPhone, errors);
		if (email == null && phone == null && !errors.containsKey("email") && !errors.containsKey("phone")) {
			errors.put("email", "Add an email or a phone number");
		}
		return new Parsed(errors.isEmpty() ? new CustomerFields(name, email, phone) : null, Map.copyOf(errors));
	}

	private static String name(String raw, Map<String, String> errors) {
		String name = (raw == null) ? "" : raw.strip().replaceAll("\\s+", " ");
		if (name.isEmpty()) {
			errors.put("name", "Enter a name");
		}
		else if (name.length() > MAX_NAME_LENGTH) {
			errors.put("name", "Keep the name under " + MAX_NAME_LENGTH + " characters");
		}
		return name;
	}

	private static String email(String raw, Map<String, String> errors) {
		String email = Text.stripToNull(raw);
		if (email == null) {
			return null;
		}
		email = email.toLowerCase(Locale.ROOT);
		if (email.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(email).matches()) {
			errors.put("email", "Enter a valid email address");
			return null;
		}
		return email;
	}

	private static String phone(String raw, Map<String, String> errors) {
		String phone = Text.stripToNull(raw);
		if (phone == null) {
			return null;
		}
		String normalized = PHONE_CHARACTERS.matcher(phone).matches() ? normalizePhone(phone) : null;
		if (normalized == null) {
			errors.put("phone", "Enter a 10-digit mobile number, or + and the country code");
		}
		return normalized;
	}

	/** {@code +<digits>} for a number we understand, otherwise null. */
	private static String normalizePhone(String phone) {
		String digits = phone.replaceAll("\\D", "");
		if (phone.startsWith("+")) {
			if (digits.startsWith("91")) {
				return (digits.length() == 12) ? "+" + digits : null;
			}
			return (digits.length() >= 8 && digits.length() <= 15) ? "+" + digits : null;
		}
		String national = switch (digits.length()) {
			case 10 -> digits;
			case 11 -> digits.startsWith("0") ? digits.substring(1) : null;
			case 12 -> digits.startsWith("91") ? digits.substring(2) : null;
			default -> null;
		};
		return (national != null && INDIAN_MOBILE.matcher(national).matches()) ? "+91" + national : null;
	}

	/** Either cleaned fields (and no errors) or the errors (and no fields). */
	public record Parsed(CustomerFields fields, Map<String, String> errors) {

		public boolean valid() {
			return this.errors.isEmpty();
		}

	}

}
