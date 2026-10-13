package com.paynudge.policy;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The placeholders an owner may use in a reminder. Their values (name, amount, dates, links) are always filled in by
 * code from the due, never typed by the owner or generated, so a message can't carry a wrong amount or link.
 */
public final class Placeholders {

	public static final Set<String> ALLOWED = Set.of("name", "amount", "due_date", "pay_link", "business_name",
			"unsubscribe_link");

	/** Every customer email must say who it is for, how much, and where to pay. */
	public static final Set<String> REQUIRED = Set.of("name", "amount", "pay_link");

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)}");

	private Placeholders() {
	}

	/** The placeholder names used in the text, in order of first use. */
	public static Set<String> used(String text) {
		Set<String> used = new LinkedHashSet<>();
		Matcher matcher = PLACEHOLDER.matcher(text);
		while (matcher.find()) {
			used.add(matcher.group(1));
		}
		return used;
	}

	/** Replaces each known placeholder with its value. Unknown ones cannot occur: policies are checked on save. */
	public static String fill(String text, Map<String, String> values) {
		Matcher matcher = PLACEHOLDER.matcher(text);
		StringBuilder filled = new StringBuilder();
		while (matcher.find()) {
			String value = values.getOrDefault(matcher.group(1), matcher.group());
			matcher.appendReplacement(filled, Matcher.quoteReplacement(value));
		}
		matcher.appendTail(filled);
		return filled.toString();
	}

}
