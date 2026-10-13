package com.paynudge.policy;

import java.util.List;

/**
 * What a policy does, stored as JSON in {@code policies.definition} and copied into each due as
 * {@code policy_snapshot}. Offsets are whole days relative to the due date.
 *
 * @param schemaVersion lets later versions of this shape be read side by side with this one
 * @param stopOn what ends a due's reminders; the engine always stops on these, whatever is stored
 */
public record PolicyDefinition(int schemaVersion, List<Step> steps, List<String> stopOn) {

	public static final int SCHEMA_VERSION = 1;

	public static final List<String> STOP_ON = List.of("PAID", "OPTED_OUT");

	public enum Action {

		/** Email the customer the step's subject and body. */
		SEND_EMAIL,

		/** Tell the owner the due is still unpaid, so they can follow up themselves. */
		NOTIFY_OWNER

	}

	/**
	 * @param tone a hint for wording (friendly, neutral, firm, final_notice); the text itself is in subject and body
	 * @param subject and body are only for {@link Action#SEND_EMAIL}; placeholders like {name} are filled by code
	 */
	public record Step(String key, int offsetDays, Action action, String tone, String subject, String body) {

		public boolean emailsCustomer() {
			return this.action == Action.SEND_EMAIL;
		}

	}

}
