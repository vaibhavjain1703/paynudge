package com.paynudge.engine;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.jayway.jsonpath.JsonPath;
import com.paynudge.channel.Channel.OutboundMessage;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/** The reminder engine end to end against Postgres, with the clock moved by hand. */
class EngineTest extends EngineTestSupport {

	@Test
	void aDueSchedulesAndItsFirstReminderSends() {
		long due = createDue(customer("Aarav Shah", "aarav@example.com"), "2026-01-20");

		assertThat(this.worker.drain()).as("nothing is due on 5 January").isZero();

		this.clock.set(FIRST_REMINDER);
		assertThat(this.worker.drain()).isEqualTo(1);

		assertThat(this.channel.sent()).hasSize(1);
		OutboundMessage reminder = this.channel.sent().get(0);
		String unsubscribeToken = unsubscribeToken(due);
		assertThat(reminder.to()).isEqualTo("aarav@example.com");
		assertThat(reminder.subject()).isEqualTo("A quick reminder from Bright Tuition");
		assertThat(reminder.text()).startsWith("Hi Aarav Shah,")
			.contains("₹1,500 is due on 20 Jan 2026")
			.contains("http://localhost:3000/pay/" + payToken(due))
			.endsWith("Stop these reminders: http://localhost:3000/unsubscribe/" + unsubscribeToken);
		assertThat(reminder.reference()).isEqualTo("due-" + due + ".remind_1");
		assertThat(reminder.oneClickUnsubscribe())
			.isEqualTo("http://localhost:8080/api/public/unsubscribe/" + unsubscribeToken);

		assertThat(stepStatuses(due)).containsExactly("remind_1 DONE 1", "remind_2 PENDING 0", "remind_3 PENDING 0",
				"notify_owner PENDING 0", "final PENDING 0");
		assertThat(dueStatus(due)).isEqualTo("ACTIVE");
		assertThat(this.jdbc.queryForObject("SELECT provider_id FROM messages WHERE due_id = ?", String.class, due))
			.isEqualTo("fake-1");
		assertThat(this.jdbc.queryForObject("SELECT sent_at FROM messages WHERE due_id = ?",
				OffsetDateTime.class, due)
			.toInstant()).as("the engine's clock, not the database's").isEqualTo(FIRST_REMINDER);
		// Thirteen days on, the sign-up token has long expired: sign in again to look at the due.
		MvcTestResult login = this.mvc.post()
			.uri("/api/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content(this.json.writeValueAsString(Map.of("email", "asha@example.com", "password", OWNER_PASSWORD)))
			.exchange();
		String fresh = JsonPath.read(bodyOf(login), "$.token");
		assertThat(getAs(fresh, "/api/dues/" + due)).bodyJson()
			.extractingPath("$.messages[0].stepKey")
			.isEqualTo("remind_1");

		assertThat(this.worker.drain()).as("the next email waits for 20 January").isZero();
	}

	@Test
	void aPaidDueGetsNoMoreReminders() {
		long due = createDue(customer("Aarav Shah", "aarav@example.com"), "2026-01-20");
		this.jdbc.update("UPDATE dues SET status = 'PAID' WHERE id = ?", due);

		this.clock.set(FIRST_REMINDER);
		this.worker.drain();

		assertThat(this.channel.sent()).isEmpty();
		assertThat(stepStatuses(due).get(0)).isEqualTo("remind_1 CANCELLED 1");
		assertThat(stepColumn(due, "remind_1", "error")).isEqualTo("Due is paid");
	}

	@Test
	void aCustomerWhoOptedOutGetsNothing() {
		long aarav = customer("Aarav Shah", "aarav@example.com");
		long due = createDue(aarav, "2026-01-20");
		this.jdbc.update("UPDATE customers SET opted_out = true WHERE id = ?", aarav);

		this.clock.set(FIRST_REMINDER);
		this.worker.drain();

		assertThat(this.channel.sent()).isEmpty();
		assertThat(stepStatuses(due).get(0)).isEqualTo("remind_1 CANCELLED 1");
		assertThat(stepColumn(due, "remind_1", "error")).isEqualTo("Customer unsubscribed");
	}

	@Test
	void aCustomerWithOnlyAPhoneIsSkippedUntilThereIsAPhoneChannel() {
		long kabir = idOf(postAs(this.token, "/api/customers", Map.of("name", "Kabir", "phone", "9123456789")));
		long due = createDue(kabir, "2026-01-20");

		this.clock.set(FIRST_REMINDER);
		this.worker.drain();

		assertThat(this.channel.sent()).isEmpty();
		assertThat(stepStatuses(due).get(0)).isEqualTo("remind_1 SKIPPED 1");
		assertThat(this.jdbc.queryForObject("SELECT sum(sent) FROM daily_sends", Long.class))
			.as("a skipped email gives its slot back")
			.isZero();
	}

	@Test
	void theOwnerHearsWhenTheDueIsStillUnpaid() {
		long due = createDue(customer("Aarav Shah", "aarav@example.com"), "2026-01-20");

		for (String day : List.of("2026-01-18", "2026-01-20", "2026-01-23", "2026-01-27")) {
			this.clock.set(Instant.parse(day + "T04:30:00Z"));
			assertThat(this.worker.drain()).as(day).isEqualTo(1);
		}

		assertThat(this.channel.sent()).extracting(OutboundMessage::subject)
			.containsExactly("A quick reminder from Bright Tuition", "Payment of ₹1,500 due on 20 Jan 2026",
					"Your payment of ₹1,500 is overdue");
		assertThat(this.emails.sent()).hasSize(1);
		assertThat(this.emails.last().to()).isEqualTo("asha@example.com");
		assertThat(this.emails.last().subject()).isEqualTo("Still unpaid: Aarav Shah owes ₹1,500");
		assertThat(this.emails.last().text()).contains("http://localhost:3000/dues/" + due);
		assertThat(dueStatus(due)).isEqualTo("ESCALATED");
	}

	@Test
	void aFailedSendIsRetriedAfterAShortWait() {
		long due = createDue(customer("Aarav Shah", "aarav@example.com"), "2026-01-20");
		this.channel.failNext(1);

		this.clock.set(FIRST_REMINDER);
		this.worker.drain();

		assertThat(this.channel.sent()).isEmpty();
		assertThat(stepStatuses(due).get(0)).isEqualTo("remind_1 PENDING 1");
		assertThat(messageCount()).as("rolled back").isZero();
		assertThat(stepColumn(due, "remind_1", "error")).isEqualTo("simulated provider outage");
		// 30 seconds, plus up to a tenth more so a batch of failures does not retry in step.
		assertThat(Duration.between(FIRST_REMINDER, runAt(due, "remind_1"))).isBetween(Duration.ofSeconds(30),
				Duration.ofSeconds(33));

		this.clock.advance(Duration.ofSeconds(29));
		assertThat(this.worker.drain()).isZero();
		this.clock.advance(Duration.ofSeconds(4));
		assertThat(this.worker.drain()).isEqualTo(1);
		assertThat(this.channel.sent()).hasSize(1);
		assertThat(stepStatuses(due).get(0)).isEqualTo("remind_1 DONE 2");
	}

	@Test
	void twoFailuresThenSuccessTakeThreeTriesWithGrowingWaits() {
		long due = createDue(customer("Aarav Shah", "aarav@example.com"), "2026-01-20");
		this.channel.failNext(2);
		this.clock.set(FIRST_REMINDER);

		this.worker.drain();
		Duration firstWait = Duration.between(this.clock.instant(), runAt(due, "remind_1"));
		this.clock.set(runAt(due, "remind_1"));
		this.worker.drain();
		Duration secondWait = Duration.between(this.clock.instant(), runAt(due, "remind_1"));
		this.clock.set(runAt(due, "remind_1"));
		this.worker.drain();

		assertThat(firstWait).isBetween(Duration.ofSeconds(30), Duration.ofSeconds(33));
		assertThat(secondWait).isBetween(Duration.ofSeconds(60), Duration.ofSeconds(66));
		assertThat(stepStatuses(due).get(0)).isEqualTo("remind_1 DONE 3");
		assertThat(this.channel.sent()).hasSize(1);
		assertThat(messageCount()).isEqualTo(1);
		assertThat(stepColumn(due, "remind_1", "error")).as("cleared once it went").isNull();
	}

	@Test
	void aStepThatKeepsFailingStopsAfterFiveTriesAndTheDueNeedsTheOwner() {
		long due = createDue(customer("Aarav Shah", "aarav@example.com"), "2026-01-20");
		this.channel.failNext(5);

		this.clock.set(FIRST_REMINDER);
		for (int i = 0; i < 5; i++) {
			this.worker.drain();
			this.clock.advance(Duration.ofMinutes(10)); // longer than any of the retry waits so far
		}

		assertThat(stepStatuses(due).get(0)).isEqualTo("remind_1 FAILED 5");
		assertThat(this.channel.sent()).isEmpty();
		assertThat(dueStatus(due)).isEqualTo("ESCALATED");
	}

	@Test
	void anAddressTheProviderRefusesFailsAtOnceWithoutRetries() {
		long due = createDue(customer("Aarav Shah", "aarav@example.com"), "2026-01-20");
		this.channel.refuseNext(1);

		this.clock.set(FIRST_REMINDER);
		this.worker.drain();

		assertThat(stepStatuses(due).get(0)).isEqualTo("remind_1 FAILED 1");
		assertThat(stepColumn(due, "remind_1", "error"))
			.isEqualTo("The mail server refused the address aarav@example.com");
		assertThat(dueStatus(due)).isEqualTo("ESCALATED");
		this.clock.advance(Duration.ofHours(1));
		assertThat(this.worker.drain()).as("no retry").isZero();
	}

	@Test
	void aStepThatAlreadySentIsOnlyFinishedWhenRunAgain() {
		long due = createDue(customer("Aarav Shah", "aarav@example.com"), "2026-01-20");
		// As if a worker sent remind_1 and then died before marking the step done.
		this.jdbc.update("INSERT INTO messages (due_id, step_key, channel, body) VALUES (?, 'remind_1', 'EMAIL', 'x')",
				due);

		this.clock.set(FIRST_REMINDER);
		this.worker.drain();

		assertThat(this.channel.sent()).as("no second email").isEmpty();
		assertThat(stepStatuses(due).get(0)).isEqualTo("remind_1 DONE 1");
	}

	@Test
	void manyWorkersAtOnceSendEveryReminderExactlyOnce() throws Exception {
		int dues = 40;
		for (int i = 0; i < dues; i++) {
			// Due on 7 January: the first email (two days before) is due right now.
			createDue(customer("Customer " + i, "customer" + i + "@example.com"), "2026-01-07");
		}

		ExecutorService pool = Executors.newFixedThreadPool(4);
		List<Callable<Integer>> workers = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			workers.add(this.worker::drain);
		}
		int ran = 0;
		for (Future<Integer> result : pool.invokeAll(workers)) {
			ran += result.get();
		}
		pool.shutdown();

		assertThat(ran).isEqualTo(dues);
		assertThat(this.channel.sent()).hasSize(dues);
		assertThat(this.channel.sent()).extracting(OutboundMessage::to).doesNotHaveDuplicates();
		assertThat(messageCount()).isEqualTo(dues);
		assertThat(this.jdbc.queryForObject(
				"SELECT count(*) FROM due_steps WHERE step_key = 'remind_1' AND status = 'DONE' AND attempt = 1",
				Long.class))
			.isEqualTo(dues);
		assertThat(this.jdbc.queryForObject("SELECT sent FROM daily_sends", Integer.class)).isEqualTo(dues);
	}

	private String payToken(long due) {
		return this.jdbc.queryForObject("SELECT pay_token FROM dues WHERE id = ?", String.class, due);
	}

	private String unsubscribeToken(long due) {
		return this.jdbc.queryForObject(
				"SELECT c.unsub_token FROM dues d JOIN customers c ON c.id = d.customer_id WHERE d.id = ?",
				String.class, due);
	}

}
