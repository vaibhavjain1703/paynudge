package com.paynudge.engine;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.paynudge.RecordingChannel.HeldSend;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Paid, cancelled or unsubscribed always wins. A stop that lands while a reminder is going out waits for it (the
 * worker holds the due's and the customer's row locks), then cancels everything after it; a stop that lands first
 * means the reminder never goes.
 */
class StopRulesTest extends EngineTestSupport {

	private final ExecutorService background = Executors.newFixedThreadPool(2);

	private long customer;

	private long due;

	@BeforeEach
	void dueWithAReminderDueNow() {
		this.customer = customer("Aarav Shah", "aarav@example.com");
		// Due on 7 January: the first email (two days before) is due right now, on 5 January.
		this.due = createDue(this.customer, "2026-01-07");
	}

	@AfterEach
	void stopBackground() {
		this.background.shutdownNow();
	}

	@Test
	void markingPaidDuringASendWaitsForItThenStopsTheRest() throws Exception {
		HeldSend send = this.channel.holdNextSend();
		Future<Boolean> sending = this.background.submit(this.worker::runOne);
		send.awaitSender();

		Future<MvcTestResult> paying = this.background
			.submit(() -> postAs(this.token, "/api/dues/" + this.due + "/mark-paid", Map.of()));
		Thread.sleep(300);
		assertThat(paying).as("waits for the due's lock").isNotDone();

		send.complete();
		assertThat(sending.get(10, TimeUnit.SECONDS)).isTrue();
		assertThat(paying.get(10, TimeUnit.SECONDS)).hasStatusOk();

		assertThat(this.channel.sent()).hasSize(1);
		assertThat(stepStatuses(this.due)).containsExactly("remind_1 DONE 1", "remind_2 CANCELLED 0",
				"remind_3 CANCELLED 0", "notify_owner CANCELLED 0", "final CANCELLED 0");
		assertThat(dueStatus(this.due)).isEqualTo("PAID");
	}

	@Test
	void unsubscribingDuringASendWaitsForItThenStopsTheRest() throws Exception {
		HeldSend send = this.channel.holdNextSend();
		Future<Boolean> sending = this.background.submit(this.worker::runOne);
		send.awaitSender();

		String unsubscribe = "/api/public/unsubscribe/"
				+ this.jdbc.queryForObject("SELECT unsub_token FROM customers WHERE id = ?", String.class, this.customer);
		Future<MvcTestResult> leaving = this.background.submit(() -> this.mvc.post().uri(unsubscribe).exchange());
		Thread.sleep(300);
		assertThat(leaving).as("waits for the customer's lock").isNotDone();

		send.complete();
		assertThat(sending.get(10, TimeUnit.SECONDS)).isTrue();
		assertThat(leaving.get(10, TimeUnit.SECONDS)).hasStatusOk();

		assertThat(this.channel.sent()).hasSize(1);
		assertThat(stepStatuses(this.due)).containsExactly("remind_1 DONE 1", "remind_2 CANCELLED 0",
				"remind_3 CANCELLED 0", "notify_owner CANCELLED 0", "final CANCELLED 0");
	}

	@Test
	void aStopThatLandsFirstMeansTheReminderNeverGoes() {
		assertThat(postAs(this.token, "/api/dues/" + this.due + "/cancel", Map.of())).hasStatusOk();

		assertThat(this.worker.drain()).isZero();
		assertThat(this.channel.sent()).isEmpty();
		assertThat(stepStatuses(this.due)).allMatch((step) -> step.endsWith("CANCELLED 0"));
	}

}
