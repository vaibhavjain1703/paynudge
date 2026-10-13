package com.paynudge.policy;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.paynudge.policy.PolicyDefinition.Action;
import com.paynudge.policy.PolicyDefinition.Step;

/**
 * What makes a policy acceptable. Besides shape (at most 8 steps, unique keys, known actions, offsets in order),
 * two rules protect customers: emails to the same customer are at least a day apart, and every email names the
 * customer, the amount and the pay link.
 */
final class PolicyRules {

	static final int MAX_STEPS = 8;

	static final int MIN_OFFSET_DAYS = -30;

	static final int MAX_OFFSET_DAYS = 90;

	static final int MIN_DAYS_BETWEEN_EMAILS = 1;

	static final Set<String> TONES = Set.of("friendly", "neutral", "firm", "final_notice");

	private static final Pattern KEY = Pattern.compile("[a-z0-9_]{1,64}");

	private PolicyRules() {
	}

	/** Problems keyed by field, e.g. "name", "steps" or "steps[2].body". Empty when the policy is fine. */
	static Map<String, String> check(String name, List<Step> steps) {
		Map<String, String> errors = new LinkedHashMap<>();
		if (name == null || name.isBlank()) {
			errors.put("name", "Enter a name");
		}
		else if (name.length() > 100) {
			errors.put("name", "Keep the name under 100 characters");
		}
		if (steps == null || steps.isEmpty()) {
			errors.put("steps", "Add at least one step");
			return errors;
		}
		if (steps.size() > MAX_STEPS) {
			errors.put("steps", "Use at most " + MAX_STEPS + " steps");
			return errors;
		}

		Set<String> keys = new HashSet<>();
		Integer previousOffset = null;
		Integer previousEmailOffset = null;
		for (int i = 0; i < steps.size(); i++) {
			Step step = steps.get(i);
			String at = "steps[" + i + "].";
			if (step.key() == null || !KEY.matcher(step.key()).matches()) {
				errors.put(at + "key", "Use lower-case letters, digits and _ (up to 64)");
			}
			else if (!keys.add(step.key())) {
				errors.put(at + "key", "Each step needs its own key");
			}
			if (step.action() == null) {
				errors.put(at + "action", "Choose what this step does");
			}
			if (step.offsetDays() < MIN_OFFSET_DAYS || step.offsetDays() > MAX_OFFSET_DAYS) {
				errors.put(at + "offsetDays",
						"Use a day between " + MIN_OFFSET_DAYS + " and +" + MAX_OFFSET_DAYS + " from the due date");
			}
			else if (previousOffset != null && step.offsetDays() < previousOffset) {
				errors.put(at + "offsetDays", "Steps must be in date order");
			}
			if (step.action() == Action.SEND_EMAIL) {
				boolean inOrder = !errors.containsKey(at + "offsetDays");
				if (inOrder && previousEmailOffset != null
						&& step.offsetDays() - previousEmailOffset < MIN_DAYS_BETWEEN_EMAILS) {
					errors.put(at + "offsetDays", "Leave at least a day between emails to the customer");
				}
				previousEmailOffset = step.offsetDays();
				checkEmail(step, at, errors);
			}
			previousOffset = step.offsetDays();
		}
		return errors;
	}

	private static void checkEmail(Step step, String at, Map<String, String> errors) {
		if (step.tone() != null && !TONES.contains(step.tone())) {
			errors.put(at + "tone", "Use friendly, neutral, firm or final_notice");
		}
		if (step.subject() == null || step.subject().isBlank()) {
			errors.put(at + "subject", "Enter a subject");
		}
		else if (step.subject().length() > 150) {
			errors.put(at + "subject", "Keep the subject under 150 characters");
		}
		else if (!unknownPlaceholders(step.subject()).isEmpty()) {
			errors.put(at + "subject", unknownMessage(step.subject()));
		}
		if (step.body() == null || step.body().isBlank()) {
			errors.put(at + "body", "Enter the message");
			return;
		}
		if (step.body().length() > 2000) {
			errors.put(at + "body", "Keep the message under 2,000 characters");
			return;
		}
		if (!unknownPlaceholders(step.body()).isEmpty()) {
			errors.put(at + "body", unknownMessage(step.body()));
			return;
		}
		Set<String> used = Placeholders.used(step.body());
		List<String> missing = Placeholders.REQUIRED.stream().filter((p) -> !used.contains(p)).sorted().toList();
		if (!missing.isEmpty()) {
			errors.put(at + "body", "The message must include " + String.join(", ", missing.stream()
				.map((p) -> "{" + p + "}")
				.toList()));
		}
	}

	private static Set<String> unknownPlaceholders(String text) {
		Set<String> unknown = new HashSet<>(Placeholders.used(text));
		unknown.removeAll(Placeholders.ALLOWED);
		return unknown;
	}

	private static String unknownMessage(String text) {
		return "Unknown placeholder " + unknownPlaceholders(text).stream().sorted().map((p) -> "{" + p + "}")
			.findFirst()
			.orElse("") + ". Use {name}, {amount}, {due_date}, {pay_link} or {business_name}";
	}

}
