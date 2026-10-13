package com.paynudge.dues;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import com.paynudge.dues.DueSchedule.PlannedStep;
import com.paynudge.policy.PolicyDefinition;
import com.paynudge.policy.PolicyDefinition.Action;
import com.paynudge.policy.PolicyDefinition.Step;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DueScheduleTest {

	private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");

	private static final SendWindow NIGHTS = new SendWindow(KOLKATA, LocalTime.of(21, 0), LocalTime.of(9, 0));

	/** The shape of "Friendly then firm": emails at -2, 0, +3 and +10 days, the owner told at +7. */
	private static final PolicyDefinition FRIENDLY_THEN_FIRM = new PolicyDefinition(1,
			List.of(email("remind_1", -2), email("remind_2", 0), email("remind_3", 3),
					new Step("notify_owner", 7, Action.NOTIFY_OWNER, null, null, null), email("final", 10)),
			PolicyDefinition.STOP_ON);

	/** 10:00 in Kolkata on 5 January 2026. */
	private static final Instant NOW = Instant.parse("2026-01-05T04:30:00Z");

	@Test
	void stepsRunAtTenInTheMorningBusinessTimeOnTheirDay() {
		List<PlannedStep> plan = DueSchedule.plan(FRIENDLY_THEN_FIRM, LocalDate.of(2026, 11, 5), NIGHTS, NOW);

		assertThat(plan).extracting(PlannedStep::key, PlannedStep::status, PlannedStep::runAt)
			.containsExactly(step("remind_1", "PENDING", "2026-11-03T04:30:00Z"),
					step("remind_2", "PENDING", "2026-11-05T04:30:00Z"),
					step("remind_3", "PENDING", "2026-11-08T04:30:00Z"),
					step("notify_owner", "PENDING", "2026-11-12T04:30:00Z"),
					step("final", "PENDING", "2026-11-15T04:30:00Z"));
		assertThat(plan.get(3).action()).isEqualTo("NOTIFY_OWNER");
	}

	@Test
	void stepsMoveOutOfQuietHours() {
		SendWindow mornings = new SendWindow(KOLKATA, LocalTime.of(8, 0), LocalTime.of(12, 0));

		List<PlannedStep> plan = DueSchedule.plan(FRIENDLY_THEN_FIRM, LocalDate.of(2026, 11, 5), mornings, NOW);

		// 10:00 is inside 08:00 to 12:00, so every step waits for noon.
		assertThat(plan).extracting(PlannedStep::runAt).first().isEqualTo(Instant.parse("2026-11-03T06:30:00Z"));
	}

	@Test
	void anOverdueDueGetsItsLatestMissedEmailNowAndSkipsTheRest() {
		List<PlannedStep> plan = DueSchedule.plan(FRIENDLY_THEN_FIRM, LocalDate.of(2026, 1, 4), NIGHTS, NOW);

		assertThat(plan).extracting(PlannedStep::key, PlannedStep::status, PlannedStep::runAt)
			.containsExactly(step("remind_1", "SKIPPED", "2026-01-02T04:30:00Z"),
					step("remind_2", "PENDING", "2026-01-05T04:30:00Z"),
					step("remind_3", "PENDING", "2026-01-07T04:30:00Z"),
					step("notify_owner", "PENDING", "2026-01-11T04:30:00Z"),
					step("final", "PENDING", "2026-01-14T04:30:00Z"));
	}

	@Test
	void theCatchUpEmailWaitsIfTheNextOneIsLessThanADayAway() {
		Instant elevenAm = Instant.parse("2026-01-05T05:30:00Z");

		// Due 3 January: the +3 email is tomorrow at 10:00, 23 hours away, so nothing goes out today.
		List<PlannedStep> plan = DueSchedule.plan(FRIENDLY_THEN_FIRM, LocalDate.of(2026, 1, 3), NIGHTS, elevenAm);

		assertThat(plan).extracting(PlannedStep::key, PlannedStep::status)
			.containsExactly(tuple("remind_1", "SKIPPED"), tuple("remind_2", "SKIPPED"), tuple("remind_3", "PENDING"),
					tuple("notify_owner", "PENDING"), tuple("final", "PENDING"));
	}

	@Test
	void aCatchUpDuringQuietHoursWaitsForTheMorning() {
		Instant halfPastTenAtNight = Instant.parse("2026-01-05T17:00:00Z");

		List<PlannedStep> plan = DueSchedule.plan(FRIENDLY_THEN_FIRM, LocalDate.of(2025, 6, 1), NIGHTS,
				halfPastTenAtNight);

		// Long overdue: only the last email (the final notice) goes, at 09:00 the next morning.
		assertThat(plan).extracting(PlannedStep::key, PlannedStep::status, PlannedStep::runAt)
			.containsExactly(step("remind_1", "SKIPPED", "2025-05-30T04:30:00Z"),
					step("remind_2", "SKIPPED", "2025-06-01T04:30:00Z"),
					step("remind_3", "SKIPPED", "2025-06-04T04:30:00Z"),
					step("notify_owner", "SKIPPED", "2025-06-08T04:30:00Z"),
					step("final", "PENDING", "2026-01-06T03:30:00Z"));
	}

	@Test
	void quietHoursMayCrossMidnightOrNot() {
		// 22:30 and 06:00 are quiet overnight; 12:00 is not.
		assertThat(NIGHTS.nextAllowed(Instant.parse("2026-01-05T17:00:00Z")))
			.isEqualTo(Instant.parse("2026-01-06T03:30:00Z"));
		assertThat(NIGHTS.nextAllowed(Instant.parse("2026-01-05T00:30:00Z")))
			.isEqualTo(Instant.parse("2026-01-05T03:30:00Z"));
		assertThat(NIGHTS.nextAllowed(Instant.parse("2026-01-05T06:30:00Z")))
			.isEqualTo(Instant.parse("2026-01-05T06:30:00Z"));

		SendWindow lunch = new SendWindow(KOLKATA, LocalTime.of(13, 0), LocalTime.of(15, 0));
		assertThat(lunch.nextAllowed(Instant.parse("2026-01-05T08:00:00Z")))
			.isEqualTo(Instant.parse("2026-01-05T09:30:00Z"));
		assertThat(lunch.nextAllowed(Instant.parse("2026-01-05T10:00:00Z")))
			.isEqualTo(Instant.parse("2026-01-05T10:00:00Z"));
	}

	private static Step email(String key, int offsetDays) {
		return new Step(key, offsetDays, Action.SEND_EMAIL, "neutral", "About {amount}",
				"Hi {name}, {amount} is due. Pay: {pay_link}");
	}

	private static org.assertj.core.groups.Tuple step(String key, String status, String runAt) {
		return tuple(key, status, Instant.parse(runAt));
	}

	private static org.assertj.core.groups.Tuple tuple(Object... values) {
		return org.assertj.core.groups.Tuple.tuple(values);
	}

}
