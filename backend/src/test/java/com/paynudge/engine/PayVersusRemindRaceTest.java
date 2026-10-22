package com.paynudge.engine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import com.paynudge.engine.StepQueue.ClaimedStep;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static com.paynudge.RazorpayWebhooks.deliver;
import static com.paynudge.RazorpayWebhooks.paymentLinkPaid;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The race the engine's locking exists for: a reminder falls due at the very moment the customer pays. Over
 * thousands of trials a worker and the payment webhook are released together, in two kinds of trial:
 * <ul>
 * <li>the worker still has to claim the step, so the payment usually lands first (the step is cancelled while it
 * waits, or the worker finds the due paid once it has the lock);</li>
 * <li>the worker has already claimed the step, and the payment waits a moment, so either may reach the due's lock
 * first (a payment that loses waits while the reminder is being sent).</li>
 * </ul>
 * In no trial does a reminder go out once the due is paid, and every trial ends paid with nothing left waiting.
 */
class PayVersusRemindRaceTest extends EngineTestSupport {

	private static final int TRIALS = 2_000;

	/**
	 * In trials where the step is already claimed, the payment waits up to this long first. Its lock query is a
	 * single round trip while the worker's run takes a few, so without it the payment would nearly always win there
	 * too; with it both orders stay common on fast and slow machines alike.
	 */
	private static final long MAX_PAYMENT_DELAY_NANOS = 3_000_000;

	@Autowired
	private DataSource dataSource;

	@Test
	void aCustomerWhoHasPaidNeverGetsAReminder() throws Exception {
		// Due in June: every step is months away until a trial brings one due's first email forward to now.
		List<Long> dues = bulkDues(TRIALS, "2026-06-20");
		Map<Long, String> payTokens = this.jdbc.query("SELECT id, pay_token FROM dues", (rs) -> {
			Map<Long, String> tokens = new HashMap<>();
			while (rs.next()) {
				tokens.put(rs.getLong("id"), rs.getString("pay_token"));
			}
			return tokens;
		});

		// At the instant of every send, an outside observer reads the due as committed: it must not be paid yet.
		List<String> sentAfterPaid = new CopyOnWriteArrayList<>();
		this.channel.watchSends((message) -> {
			if ("PAID".equals(committedStatus(message.reference()))) {
				sentAfterPaid.add(message.reference());
			}
		});

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CyclicBarrier start = new CyclicBarrier(2);
		try {
			for (int trial = 0; trial < TRIALS; trial++) {
				long due = dues.get(trial);
				this.jdbc.update("UPDATE due_steps SET run_at = ? WHERE due_id = ? AND step_key = 'remind_1'",
						this.clock.instant().atOffset(ZoneOffset.UTC), due);
				String payment = paymentLinkPaid(payTokens.get(due), "pay_" + due, 150000);
				Optional<ClaimedStep> claimed = (trial % 2 == 0) ? Optional.empty() : this.queue.claim();
				Future<?> reminding = pool.submit(() -> {
					start.await();
					claimed.ifPresentOrElse(this.worker::run, this.worker::runOne);
					return null;
				});
				Future<MvcTestResult> paying = pool.submit(() -> {
					start.await();
					if (claimed.isPresent()) {
						spin(ThreadLocalRandom.current().nextLong(MAX_PAYMENT_DELAY_NANOS));
					}
					return deliver(this.mvc, payment);
				});
				assertThat(paying.get(10, TimeUnit.SECONDS)).hasStatusOk();
				reminding.get(10, TimeUnit.SECONDS);
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(sentAfterPaid).as("reminders sent to a customer who had already paid").isEmpty();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM dues WHERE status <> 'PAID'", Long.class)).isZero();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM payments", Long.class)).isEqualTo(TRIALS);
		assertThat(this.jdbc.queryForObject(
				"SELECT count(*) FROM due_steps WHERE status IN ('PENDING', 'RUNNING')", Long.class))
			.as("nothing left waiting")
			.isZero();

		long reminded = this.jdbc.queryForObject(
				"SELECT count(*) FROM due_steps WHERE step_key = 'remind_1' AND status = 'DONE'", Long.class);
		long cancelled = this.jdbc.queryForObject(
				"SELECT count(*) FROM due_steps WHERE step_key = 'remind_1' AND status = 'CANCELLED'", Long.class);
		assertThat(reminded + cancelled).as("the reminder either went first or was cancelled").isEqualTo(TRIALS);
		assertThat(reminded).as("trials the reminder won (else the race was never run)").isPositive();
		assertThat(cancelled).as("trials the payment won (else the race was never run)").isPositive();
		assertThat(this.channel.sent()).hasSize((int) reminded);
		assertThat(messageCount()).isEqualTo(reminded);
		System.out.printf("Pay versus remind: %d trials, reminder first in %d, payment first in %d%n", TRIALS, reminded,
				cancelled);
	}

	/** Busy-waits: sleeps are rounded up to the timer tick (milliseconds on Windows), far too coarse here. */
	private static void spin(long nanos) {
		long until = System.nanoTime() + nanos;
		while (System.nanoTime() < until) {
			Thread.onSpinWait();
		}
	}

	/** The due's status as committed, read on a connection of its own (not the sending worker's transaction). */
	private String committedStatus(String reference) {
		long due = Long.parseLong(reference.substring("due-".length(), reference.indexOf('.')));
		try (Connection connection = this.dataSource.getConnection();
				PreparedStatement query = connection.prepareStatement("SELECT status FROM dues WHERE id = ?")) {
			query.setLong(1, due);
			try (ResultSet row = query.executeQuery()) {
				return row.next() ? row.getString(1) : null;
			}
		}
		catch (SQLException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
