package com.paynudge.dues;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.paynudge.dues.DueSchedule.PlannedStep;
import com.paynudge.policy.PolicyDefinition;
import com.paynudge.policy.PolicyDefinition.Action;
import com.paynudge.policy.PolicyDefinition.Step;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The schedule's promises, checked over thousands of random cases (fixed seed, so a failure repeats): random valid
 * policies, due dates, creation times, quiet windows (overnight or not) and time zones, including ones with daylight
 * saving and half-hour or quarter-hour offsets.
 * <ul>
 * <li>nothing is scheduled inside quiet hours;</li>
 * <li>nothing is scheduled in the past;</li>
 * <li>steps that will run do so in the policy's order;</li>
 * <li>at most one missed email is caught up, sent as soon as quiet hours allow, and never within a day of the next
 * one; every other missed step is skipped.</li>
 * </ul>
 */
class DueSchedulePropertyTest {

	private static final int CASES = 10_000;

	private static final List<ZoneId> ZONES = List.of("Asia/Kolkata", "Asia/Kathmandu", "Asia/Dubai", "Europe/London",
			"America/New_York", "Australia/Adelaide", "Pacific/Chatham", "UTC")
		.stream()
		.map(ZoneId::of)
		.toList();

	@Test
	void schedulesKeepTheirPromisesWhateverThePolicyZoneOrQuietHours() {
		Random random = new Random(20260120L);
		for (int i = 0; i < CASES; i++) {
			PolicyDefinition policy = randomPolicy(random);
			LocalDate dueDate = LocalDate.of(2026, 1, 1).plusDays(random.nextInt(730));
			SendWindow window = randomWindow(random);
			Instant now = dueDate.atStartOfDay(window.zone())
				.toInstant()
				.plus(Duration.ofMinutes(random.nextLong(-40L * 24 * 60, 100L * 24 * 60)));
			String what = "case %d: %s, due %s, now %s, quiet %s-%s, offsets %s".formatted(i, window.zone(), dueDate,
					now, window.quietStart(), window.quietEnd(),
					policy.steps().stream().map((step) -> step.offsetDays() + step.action().name().substring(0, 1))
						.toList());

			List<PlannedStep> plan = DueSchedule.plan(policy, dueDate, window, now);

			assertThat(plan).as(what).hasSize(policy.steps().size());
			List<PlannedStep> pending = plan.stream()
				.filter((step) -> step.status().equals(StepStatus.PENDING))
				.toList();
			Instant previous = null;
			for (PlannedStep step : pending) {
				assertThat(window.nextAllowed(step.runAt())).as("%s: %s is in quiet hours", what, step.key())
					.isEqualTo(step.runAt());
				assertThat(step.runAt()).as("%s: %s is in the past", what, step.key()).isAfterOrEqualTo(now);
				if (previous != null) {
					assertThat(step.runAt()).as("%s: %s runs out of order", what, step.key())
						.isAfterOrEqualTo(previous);
				}
				previous = step.runAt();
			}
			for (PlannedStep step : plan) {
				if (step.status().equals(StepStatus.SKIPPED)) {
					assertThat(step.runAt()).as("%s: %s skipped though not missed", what, step.key()).isBefore(now);
				}
			}

			// Missed: its time (after moving out of quiet hours) had passed when the due was created.
			List<PlannedStep> caughtUp = pending.stream()
				.filter((step) -> window.nextAllowed(nominal(policy, step, dueDate, window)).isBefore(now))
				.toList();
			assertThat(caughtUp).as("%s: more than one catch-up", what).hasSizeLessThanOrEqualTo(1);
			for (PlannedStep catchUp : caughtUp) {
				assertThat(catchUp.action()).as(what).isEqualTo(Action.SEND_EMAIL.name());
				assertThat(catchUp.runAt()).as(what).isEqualTo(window.nextAllowed(now));
				pending.stream()
					.filter((step) -> step.action().equals(Action.SEND_EMAIL.name()) && step != catchUp)
					.findFirst()
					.ifPresent((next) -> assertThat(Duration.between(catchUp.runAt(), next.runAt()))
						.as("%s: catch-up too close to %s", what, next.key())
						.isGreaterThanOrEqualTo(DueSchedule.MIN_GAP));
			}
		}
	}

	/** 10:00 business time on the step's day, before quiet hours move it. */
	private static Instant nominal(PolicyDefinition policy, PlannedStep planned, LocalDate dueDate, SendWindow window) {
		Step step = policy.steps().stream().filter((candidate) -> candidate.key().equals(planned.key())).findFirst()
			.orElseThrow();
		return dueDate.plusDays(step.offsetDays()).atTime(DueSchedule.SEND_TIME).atZone(window.zone()).toInstant();
	}

	/** A policy PolicyRules would accept: 1 to 8 steps in date order, a day or more apart, at least one email. */
	private static PolicyDefinition randomPolicy(Random random) {
		int count = 1 + random.nextInt(8);
		List<Step> steps = new ArrayList<>();
		int offset = -30 + random.nextInt(31);
		boolean email = false;
		for (int i = 0; i < count && offset <= 90; i++) {
			Action action = (random.nextInt(5) == 0 && i > 0) ? Action.NOTIFY_OWNER : Action.SEND_EMAIL;
			email |= action == Action.SEND_EMAIL;
			steps.add(new Step("s" + i, offset, action, "neutral", "Subject", "Hi {name}, {amount}: {pay_link}"));
			offset += 1 + random.nextInt(10);
		}
		if (!email) {
			steps.set(0, new Step("s0", steps.get(0).offsetDays(), Action.SEND_EMAIL, "neutral", "Subject",
					"Hi {name}, {amount}: {pay_link}"));
		}
		return new PolicyDefinition(PolicyDefinition.SCHEMA_VERSION, steps, PolicyDefinition.STOP_ON);
	}

	/** Quiet hours of any length (but not empty), starting and ending on any minute. */
	private static SendWindow randomWindow(Random random) {
		LocalTime start = LocalTime.of(random.nextInt(24), random.nextInt(60));
		LocalTime end;
		do {
			end = LocalTime.of(random.nextInt(24), random.nextInt(60));
		}
		while (end.equals(start));
		return new SendWindow(ZONES.get(random.nextInt(ZONES.size())), start, end);
	}

}
