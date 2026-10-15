package com.paynudge.engine;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.paynudge.RecordingChannel.HeldSend;
import com.paynudge.engine.StepQueue.ClaimedStep;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Workers that die, or stall, in the middle of a step. However it happens, the customer gets exactly one email:
 * the dying worker's transaction rolls back, its lease runs out, the reaper hands the step back, and another
 * worker sends it once. A worker that is merely slow is never taken over.
 */
class CrashRecoveryTest extends EngineTestSupport {

	private final ExecutorService doomed = Executors.newSingleThreadExecutor();

	@Autowired
	private StepExecutor executor;

	private long due;

	@BeforeEach
	void dueWithAReminderDueNow() {
		this.due = createDue(customer("Aarav Shah", "aarav@example.com"), "2026-01-20");
		this.clock.set(FIRST_REMINDER);
	}

	@AfterEach
	void stopDoomedWorker() {
		this.doomed.shutdownNow();
	}

	@Test
	void aWorkerKilledMidSendStillEndsWithOneSend() throws Exception {
		HeldSend send = this.channel.holdNextSend();
		Future<Boolean> zombie = this.doomed.submit(this.worker::runOne);
		send.awaitSender();

		// Kill it the way the database sees a process die: its session ends and its transaction rolls back.
		assertThat(killSessionsInTransaction()).isEqualTo(1);
		assertThat(stepStatuses(this.due).get(0)).isEqualTo("remind_1 RUNNING 1");
		assertThat(messageCount()).isZero();

		// Until the lease runs out the step stays with the dead worker.
		this.clock.advance(Duration.ofSeconds(59));
		assertThat(this.worker.reap()).isZero();
		assertThat(this.worker.drain()).isZero();

		this.clock.advance(Duration.ofSeconds(2));
		assertThat(this.worker.reap()).isEqualTo(1);
		assertThat(stepStatuses(this.due).get(0)).isEqualTo("remind_1 PENDING 1");
		assertThat(stepColumn(this.due, "remind_1", "error")).isEqualTo("The worker stopped mid-step");
		assertThat(this.worker.drain()).isEqualTo(1);

		// The dead worker's call comes back (it never reached the provider) and can no longer touch the step.
		send.die();
		zombie.get(10, TimeUnit.SECONDS);

		assertThat(this.channel.sent()).hasSize(1);
		assertThat(messageCount()).isEqualTo(1);
		assertThat(stepStatuses(this.due).get(0)).isEqualTo("remind_1 DONE 2");
		assertThat(stepColumn(this.due, "remind_1", "error")).isNull();
		assertThat(this.jdbc.queryForObject("SELECT sent FROM daily_sends", Integer.class)).isEqualTo(1);
	}

	@Test
	void aSlowSendThatOutlastsItsLeaseIsNeverTakenOver() throws Exception {
		HeldSend send = this.channel.holdNextSend();
		Future<Boolean> slow = this.doomed.submit(this.worker::runOne);
		send.awaitSender();

		// Long past the 60-second lease, but the worker is alive and holds the step's row lock.
		this.clock.advance(Duration.ofMinutes(5));
		assertThat(this.worker.reap()).isZero();
		assertThat(this.worker.drain()).isZero();

		send.complete();
		assertThat(slow.get(10, TimeUnit.SECONDS)).isTrue();

		assertThat(this.channel.sent()).hasSize(1);
		assertThat(stepStatuses(this.due).get(0)).isEqualTo("remind_1 DONE 1");
		assertThat(this.worker.reap()).isZero();
	}

	@Test
	void aWorkerThatDiesRightAfterClaimingIsReplaced() {
		// Claimed, and then nothing: the worker died before running the step.
		assertThat(this.queue.claim()).isPresent();

		this.clock.advance(Duration.ofSeconds(61));
		assertThat(this.worker.reap()).isEqualTo(1);
		assertThat(this.worker.drain()).isEqualTo(1);

		assertThat(this.channel.sent()).hasSize(1);
		assertThat(stepStatuses(this.due).get(0)).isEqualTo("remind_1 DONE 2");
	}

	@Test
	void aWorkerWhoseLeaseRanOutBeforeItStartedDoesNothing() {
		ClaimedStep stale = this.queue.claim().orElseThrow();
		this.clock.advance(Duration.ofSeconds(61));
		this.worker.reap();
		assertThat(this.worker.drain()).isEqualTo(1);

		// The first worker wakes up (a long GC pause, say) and runs the step it claimed a while ago.
		assertThat(this.executor.execute(stale)).isFalse();
		this.queue.failed(stale, "late", false);

		assertThat(this.channel.sent()).hasSize(1);
		assertThat(stepStatuses(this.due).get(0)).isEqualTo("remind_1 DONE 2");
		assertThat(stepColumn(this.due, "remind_1", "error")).isNull();
	}

	@Test
	void aStepThatKillsEveryWorkerFailsAfterFiveTries() {
		for (int attempt = 1; attempt <= 5; attempt++) {
			assertThat(this.queue.claim()).as("try %d", attempt).isPresent();
			this.clock.advance(Duration.ofSeconds(61));
			assertThat(this.worker.reap()).isEqualTo(1);
		}

		assertThat(stepStatuses(this.due).get(0)).isEqualTo("remind_1 FAILED 5");
		assertThat(stepColumn(this.due, "remind_1", "error")).isEqualTo("The worker stopped mid-step on every try");
		assertThat(dueStatus(this.due)).isEqualTo("ESCALATED");
		assertThat(this.worker.drain()).isZero();
		assertThat(this.channel.sent()).isEmpty();
	}

	/** Ends every other session with an open transaction, and waits until each is gone. */
	private int killSessionsInTransaction() {
		return this.jdbc.queryForObject("""
				SELECT count(*) FILTER (WHERE pg_terminate_backend(pid, 5000)) FROM pg_stat_activity
				WHERE datname = current_database() AND pid <> pg_backend_pid() AND state = 'idle in transaction'""",
				Integer.class);
	}

}
