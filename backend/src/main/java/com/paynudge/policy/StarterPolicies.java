package com.paynudge.policy;

import java.util.List;

import com.paynudge.policy.PolicyDefinition.Action;
import com.paynudge.policy.PolicyDefinition.Step;

/**
 * The three policies every business starts with. Wording stays factual: no threats or legal claims, even in the final
 * notice. Every email carries an unsubscribe link, added by code.
 */
final class StarterPolicies {

	record Starter(String name, List<Step> steps) {
	}

	private static final String FRIENDLY_BODY = """
			Hi {name},

			Just a friendly reminder that {amount} is due on {due_date}. You can pay in a few seconds here:
			{pay_link}

			Thank you!
			{business_name}""";

	private static final String DUE_TODAY_BODY = """
			Hi {name},

			Your payment of {amount} to {business_name} is due today ({due_date}). You can pay here:
			{pay_link}

			If you have already paid, please ignore this message.""";

	private static final String OVERDUE_BODY = """
			Hi {name},

			We haven't received your payment of {amount}, which was due on {due_date}. Please pay today:
			{pay_link}

			If something is wrong, please get in touch with {business_name}.""";

	private static final String FINAL_BODY = """
			Hi {name},

			This is our final reminder about {amount}, due on {due_date}. Please pay here:
			{pay_link}

			After this, {business_name} will follow up with you directly.""";

	static final List<Starter> ALL = List.of(
			new Starter("Friendly then firm",
					List.of(email("remind_1", -2, "friendly", "A quick reminder from {business_name}", FRIENDLY_BODY),
							email("remind_2", 0, "neutral", "{amount} is due today", DUE_TODAY_BODY),
							email("remind_3", 3, "firm", "Your payment of {amount} is overdue", OVERDUE_BODY),
							owner("notify_owner", 7),
							email("final", 10, "final_notice", "Final reminder: {amount} was due on {due_date}",
									FINAL_BODY))),
			new Starter("Gentle",
					List.of(email("remind_1", -1, "friendly", "A quick reminder from {business_name}", FRIENDLY_BODY),
							email("remind_2", 2, "neutral", "A gentle nudge about {amount}", OVERDUE_BODY),
							owner("notify_owner", 7))),
			new Starter("Firm",
					List.of(email("remind_1", 0, "neutral", "{amount} is due today", DUE_TODAY_BODY),
							email("remind_2", 1, "firm", "Your payment of {amount} is overdue", OVERDUE_BODY),
							email("remind_3", 3, "firm", "Second notice: {amount} is overdue", OVERDUE_BODY),
							owner("notify_owner", 5),
							email("final", 7, "final_notice", "Final reminder: {amount} was due on {due_date}",
									FINAL_BODY))));

	private StarterPolicies() {
	}

	private static Step email(String key, int offsetDays, String tone, String subject, String body) {
		return new Step(key, offsetDays, Action.SEND_EMAIL, tone, subject, body);
	}

	private static Step owner(String key, int offsetDays) {
		return new Step(key, offsetDays, Action.NOTIFY_OWNER, null, null, null);
	}

}
