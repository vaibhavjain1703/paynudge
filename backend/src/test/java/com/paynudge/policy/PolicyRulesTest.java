package com.paynudge.policy;

import java.util.List;

import com.paynudge.policy.PolicyDefinition.Action;
import com.paynudge.policy.PolicyDefinition.Step;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class PolicyRulesTest {

	private static final String BODY = "Hi {name}, {amount} is due on {due_date}. Pay: {pay_link}";

	@Test
	void theStarterPoliciesFollowTheRules() {
		for (StarterPolicies.Starter starter : StarterPolicies.ALL) {
			assertThat(PolicyRules.check(starter.name(), starter.steps())).as(starter.name()).isEmpty();
		}
	}

	@Test
	void customerEmailsAreAtLeastADayApart() {
		List<Step> steps = List.of(email("first", 0), email("second", 0));

		assertThat(PolicyRules.check("Too fast", steps))
			.containsExactly(entry("steps[1].offsetDays", "Leave at least a day between emails to the customer"));
	}

	@Test
	void anOwnerNotificationMayShareADayWithAnEmail() {
		List<Step> steps = List.of(email("remind", 3), new Step("tell_me", 3, Action.NOTIFY_OWNER, null, null, null));

		assertThat(PolicyRules.check("Same day", steps)).isEmpty();
	}

	@Test
	void stepsGoInDateOrderWithUniqueKeys() {
		List<Step> steps = List.of(email("remind", 5), email("remind", 2));

		assertThat(PolicyRules.check("Muddled", steps)).containsEntry("steps[1].key", "Each step needs its own key")
			.containsEntry("steps[1].offsetDays", "Steps must be in date order");
	}

	@Test
	void everyEmailNamesTheCustomerTheAmountAndThePayLink() {
		Step vague = new Step("vague", 0, Action.SEND_EMAIL, "neutral", "Reminder", "Please pay soon.");

		assertThat(PolicyRules.check("Vague", List.of(vague))).containsExactly(
				entry("steps[0].body", "The message must include {amount}, {name}, {pay_link}"));
	}

	@Test
	void onlyKnownPlaceholdersAreAllowed() {
		Step typo = new Step("typo", 0, Action.SEND_EMAIL, null, "Hi {nmae}", BODY);

		assertThat(PolicyRules.check("Typo", List.of(typo))).containsExactly(entry("steps[0].subject",
				"Unknown placeholder {nmae}. Use {name}, {amount}, {due_date}, {pay_link} or {business_name}"));
	}

	@Test
	void policiesHaveANameAndOneToEightSteps() {
		assertThat(PolicyRules.check(" ", List.of())).containsOnlyKeys("name", "steps");

		List<Step> nine = java.util.stream.IntStream.range(0, 9).mapToObj((i) -> email("s" + i, i)).toList();
		assertThat(PolicyRules.check("Long", nine)).containsExactly(entry("steps", "Use at most 8 steps"));
	}

	@Test
	void offsetsStayWithinAReasonableWindow() {
		assertThat(PolicyRules.check("Far", List.of(email("early", -31)))).containsOnlyKeys("steps[0].offsetDays");
		assertThat(PolicyRules.check("Far", List.of(email("late", 91)))).containsOnlyKeys("steps[0].offsetDays");
	}

	@Test
	void placeholdersAreFilledByCode() {
		String filled = Placeholders.fill("Hi {name}, pay {amount} at {pay_link}. {missing}",
				java.util.Map.of("name", "Aarav", "amount", "₹1,500", "pay_link", "https://pay/x$1"));

		assertThat(filled).isEqualTo("Hi Aarav, pay ₹1,500 at https://pay/x$1. {missing}");
	}

	private static Step email(String key, int offsetDays) {
		return new Step(key, offsetDays, Action.SEND_EMAIL, "neutral", "About {amount}", BODY);
	}

}
