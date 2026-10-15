package com.paynudge.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The checks made just before each customer email, whatever the schedule said: quiet hours as the business has them
 * now, a day between emails to the same person, and the business's daily cap. A step that has to wait goes back to
 * the queue with a note, and waiting does not use up one of its tries.
 */
class SendSafeguardsTest extends EngineTestSupport {

	@Test
	void quietHoursAreCheckedAgainWhenTheEmailIsAboutToGo() {
		long due = createDue(customer("Aarav Shah", "aarav@example.com"), "2026-01-20");
		// Scheduled for 10:00; then the owner makes 09:30 to 11:00 quiet.
		updateBusiness(Map.of("quietStart", "09:30", "quietEnd", "11:00"));

		this.clock.set(FIRST_REMINDER);
		assertThat(this.worker.drain()).isEqualTo(1);

		assertThat(this.channel.sent()).isEmpty();
		assertThat(stepStatuses(due).get(0)).as("waiting is not a try").isEqualTo("remind_1 PENDING 0");
		assertThat(runAt(due, "remind_1")).isEqualTo(Instant.parse("2026-01-18T05:30:00Z")); // 11:00 Kolkata
		assertThat(stepColumn(due, "remind_1", "note")).isEqualTo("Moved out of quiet hours");
		assertThat(this.jdbc.queryForObject("SELECT sent FROM daily_sends", Integer.class)).isZero();

		this.clock.set(Instant.parse("2026-01-18T05:30:00Z"));
		assertThat(this.worker.drain()).isEqualTo(1);
		assertThat(this.channel.sent()).hasSize(1);
		assertThat(stepStatuses(due).get(0)).isEqualTo("remind_1 DONE 1");
		assertThat(stepColumn(due, "remind_1", "note")).isNull();
	}

	@Test
	void aCustomerGetsAtMostOneEmailADayAcrossAllTheirDues() {
		// Two children's fees, one parent: both first reminders fall at 10:00 on 18 January.
		long parent = customer("Meera Iyer", "meera@example.com");
		long first = createDue(parent, "2026-01-20");
		long second = createDue(parent, "2026-01-20");

		this.clock.set(FIRST_REMINDER);
		assertThat(this.worker.drain()).isEqualTo(2);

		assertThat(this.channel.sent()).hasSize(1);
		long waiting = stepColumn(first, "remind_1", "status").equals("DONE") ? second : first;
		assertThat(stepStatuses(waiting).get(0)).isEqualTo("remind_1 PENDING 0");
		assertThat(runAt(waiting, "remind_1")).isEqualTo(FIRST_REMINDER.plus(Duration.ofDays(1)));
		assertThat(stepColumn(waiting, "remind_1", "note")).isEqualTo("Spaced out from the last email to this customer");

		this.clock.set(FIRST_REMINDER.plus(Duration.ofDays(1)).minusSeconds(1));
		assertThat(this.worker.drain()).isZero();
		this.clock.advance(Duration.ofSeconds(1));
		assertThat(this.worker.drain()).isEqualTo(1);
		assertThat(this.channel.sent()).hasSize(2);
		assertThat(stepStatuses(waiting).get(0)).isEqualTo("remind_1 DONE 1");
	}

	@Test
	void manyWorkersTogetherNeverPassTheDailyCap() throws Exception {
		updateBusiness(Map.of("dailySendCap", 3));
		List<Long> dues = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			// Due on 7 January: the first email (two days before) is due right now, on 5 January.
			dues.add(createDue(customer("Customer " + i, "customer" + i + "@example.com"), "2026-01-07"));
		}

		drainWithFourWorkers();

		assertThat(this.channel.sent()).hasSize(3);
		assertThat(this.jdbc.queryForObject("SELECT sent FROM daily_sends WHERE day = '2026-01-05'", Integer.class))
			.isEqualTo(3);
		List<Long> waiting = dues.stream()
			.filter((due) -> stepColumn(due, "remind_1", "status").equals("PENDING"))
			.toList();
		assertThat(waiting).hasSize(4);
		for (long due : waiting) {
			assertThat(stepStatuses(due).get(0)).isEqualTo("remind_1 PENDING 0");
			assertThat(runAt(due, "remind_1")).as("10:00 the next day")
				.isEqualTo(Instant.parse("2026-01-06T04:30:00Z"));
			assertThat(stepColumn(due, "remind_1", "note")).isEqualTo("Today's email limit was reached");
		}

		// The next day has a fresh allowance.
		this.clock.set(Instant.parse("2026-01-06T04:30:00Z"));
		drainWithFourWorkers();
		assertThat(this.channel.sent()).hasSize(6);
		assertThat(this.jdbc.queryForObject("SELECT sent FROM daily_sends WHERE day = '2026-01-06'", Integer.class))
			.isEqualTo(3);
	}

	private void updateBusiness(Map<String, Object> changes) {
		Map<String, Object> settings = new HashMap<>(Map.of("name", "Bright Tuition", "timezone",
				"Asia/Kolkata", "quietStart", "21:00", "quietEnd", "09:00", "dailySendCap", 500));
		settings.putAll(changes);
		assertThat(putAs(this.token, "/api/business", settings)).hasStatusOk();
	}

	private void drainWithFourWorkers() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(4);
		List<Callable<Integer>> workers = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			workers.add(this.worker::drain);
		}
		for (Future<Integer> result : pool.invokeAll(workers)) {
			result.get();
		}
		pool.shutdown();
	}

}
