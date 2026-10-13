package com.paynudge.engine;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import com.paynudge.common.Money;
import com.paynudge.common.PublicLinks;
import com.paynudge.policy.Placeholders;
import com.paynudge.policy.PolicyDefinition.Step;

/**
 * Writes the actual messages. Every value (name, amount, date, links) comes from the due, never from the template
 * author, and every customer email ends with an unsubscribe link whatever the template says.
 */
final class Reminders {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH);

	private Reminders() {
	}

	record Composed(String subject, String text) {
	}

	/** What a reminder is about: the due, its customer and the business. */
	record DueFacts(long dueId, String status, long amountPaise, LocalDate dueDate, String payToken,
			String customerName, String customerEmail, boolean customerOptedOut, String unsubscribeToken,
			String businessName, String ownerEmail) {
	}

	static Composed customerEmail(Step step, DueFacts due, PublicLinks links) {
		Map<String, String> values = Map.of("name", due.customerName(), "amount", Money.rupees(due.amountPaise()),
				"due_date", DATE.format(due.dueDate()), "pay_link", links.pay(due.payToken()), "business_name",
				due.businessName(), "unsubscribe_link", links.unsubscribe(due.unsubscribeToken()));
		String footer = """


				--
				You are receiving this because %s uses PayNudge for payment reminders.
				Stop these reminders: %s""".formatted(due.businessName(), links.unsubscribe(due.unsubscribeToken()));
		return new Composed(Placeholders.fill(step.subject(), values), Placeholders.fill(step.body(), values) + footer);
	}

	static Composed ownerNotice(DueFacts due, PublicLinks links) {
		String amount = Money.rupees(due.amountPaise());
		return new Composed("Still unpaid: %s owes %s".formatted(due.customerName(), amount), """
				Hello,

				%s has not paid %s, which was due on %s. PayNudge has sent the reminders in your policy so far, \
				and will keep going until they pay or the policy ends.

				You may want to follow up personally: %s

				PayNudge""".formatted(due.customerName(), amount, DATE.format(due.dueDate()), links.due(due.dueId())));
	}

}
