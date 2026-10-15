package com.paynudge.dues;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import com.paynudge.policy.PolicyDefinition;
import com.paynudge.policy.PolicyDefinition.Step;

/**
 * Turns a policy into concrete times for one due. Each step runs at {@link #SEND_TIME} (business time) on its day,
 * moved out of quiet hours.
 * <p>
 * A due created late (an overdue row in an import) has steps whose time has passed. Those are skipped, except the
 * most recent customer email, which goes out now, so the customer hears once rather than never or in a burst. It is
 * skipped too if the next email is less than a day away, which keeps the gap between emails.
 */
final class DueSchedule {

	static final LocalTime SEND_TIME = SendWindow.SEND_TIME;

	static final Duration MIN_GAP = Duration.ofDays(1);

	private DueSchedule() {
	}

	record PlannedStep(String key, String action, String status, Instant runAt) {
	}

	static List<PlannedStep> plan(PolicyDefinition policy, LocalDate dueDate, SendWindow window, Instant now) {
		List<Instant> times = new ArrayList<>();
		for (Step step : policy.steps()) {
			Instant planned = dueDate.plusDays(step.offsetDays()).atTime(SEND_TIME).atZone(window.zone()).toInstant();
			times.add(window.nextAllowed(planned));
		}

		int catchUp = -1;
		Instant nextEmail = null;
		for (int i = 0; i < policy.steps().size(); i++) {
			if (!policy.steps().get(i).emailsCustomer()) {
				continue;
			}
			if (times.get(i).isBefore(now)) {
				catchUp = i;
			}
			else if (nextEmail == null) {
				nextEmail = times.get(i);
			}
		}
		Instant catchUpAt = window.nextAllowed(now);
		if (catchUp >= 0 && nextEmail != null && Duration.between(catchUpAt, nextEmail).compareTo(MIN_GAP) < 0) {
			catchUp = -1;
		}

		List<PlannedStep> planned = new ArrayList<>();
		for (int i = 0; i < policy.steps().size(); i++) {
			Step step = policy.steps().get(i);
			String action = step.action().name();
			if (i == catchUp) {
				planned.add(new PlannedStep(step.key(), action, StepStatus.PENDING, catchUpAt));
			}
			else if (times.get(i).isBefore(now)) {
				planned.add(new PlannedStep(step.key(), action, StepStatus.SKIPPED, times.get(i)));
			}
			else {
				planned.add(new PlannedStep(step.key(), action, StepStatus.PENDING, times.get(i)));
			}
		}
		return planned;
	}

}
