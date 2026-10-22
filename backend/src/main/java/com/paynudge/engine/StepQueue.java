package com.paynudge.engine;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.paynudge.common.DueEvents.DueChanged;
import com.paynudge.dues.DueStatus;
import com.paynudge.dues.StepStatus;
import com.paynudge.policy.PolicyDefinition;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Postgres is the queue: a step is a row with a run time. Claiming takes the oldest due step with
 * {@code FOR UPDATE SKIP LOCKED}, so any number of workers each get a different one without waiting on each other,
 * and leases it for a while under a fresh token. Every later write about the step names that token, so a worker
 * that lost its lease cannot touch the step again. Times come from the injected clock, so tests can move time.
 */
@Component
class StepQueue {

	private final JdbcTemplate jdbc;

	private final Clock clock;

	private final EngineProperties properties;

	private final ApplicationEventPublisher events;

	StepQueue(JdbcTemplate jdbc, Clock clock, EngineProperties properties, ApplicationEventPublisher events) {
		this.jdbc = jdbc;
		this.clock = clock;
		this.properties = properties;
		this.events = events;
	}

	/** A step a worker has claimed. {@code attempt} counts this try (1 for the first). */
	record ClaimedStep(long id, long dueId, String stepKey, String action, int attempt, UUID leaseToken) {

		boolean emailsCustomer() {
			return PolicyDefinition.Action.SEND_EMAIL.name().equals(this.action);
		}

	}

	@Transactional
	Optional<ClaimedStep> claim() {
		Instant now = this.clock.instant();
		List<ClaimedStep> claimed = this.jdbc.query("""
				UPDATE due_steps SET status = 'RUNNING', attempt = attempt + 1, lease_until = ?,
				                     lease_token = gen_random_uuid()
				WHERE id = (
				    SELECT id FROM due_steps
				    WHERE status = 'PENDING' AND run_at <= ?
				    ORDER BY run_at, id
				    FOR UPDATE SKIP LOCKED
				    LIMIT 1)
				RETURNING id, due_id, step_key, action, attempt, lease_token""",
				(rs, row) -> new ClaimedStep(rs.getLong("id"), rs.getLong("due_id"), rs.getString("step_key"),
						rs.getString("action"), rs.getInt("attempt"), rs.getObject("lease_token", UUID.class)),
				utc(now.plus(this.properties.lease())), utc(now));
		return claimed.stream().findFirst();
	}

	/**
	 * Puts the step back to wait until {@code runAt} (quiet hours, the daily cap, the gap between emails). Waiting is
	 * not a failure, so the try this claim counted is given back.
	 */
	@Transactional
	void postpone(ClaimedStep step, Instant runAt, String note) {
		int updated = this.jdbc.update("""
				UPDATE due_steps SET status = 'PENDING', run_at = ?, attempt = attempt - 1, lease_until = NULL,
				                     lease_token = NULL, note = ?
				WHERE id = ? AND lease_token = ?""", utc(runAt), note, step.id(), step.leaseToken());
		if (updated > 0) {
			this.events.publishEvent(new DueChanged(step.dueId()));
		}
	}

	/**
	 * After a failed try. A permanent failure, or the last allowed try, ends the step as FAILED and the due needs its
	 * owner. Otherwise the step goes back to PENDING after a growing wait (30 s, 1 min, 2 min, ...) plus up to a tenth
	 * more at random, so steps that failed together (a provider outage) do not all retry in the same instant.
	 */
	@Transactional
	void failed(ClaimedStep step, String error, boolean permanent) {
		Instant now = this.clock.instant();
		boolean giveUp = permanent || step.attempt() >= this.properties.maxAttempts();
		Duration wait = this.properties.retryDelay().multipliedBy(1L << Math.min(step.attempt() - 1, 20));
		Duration jitter = Duration.ofMillis(ThreadLocalRandom.current().nextLong(wait.toMillis() / 10 + 1));
		int updated = this.jdbc.update("""
				UPDATE due_steps SET status = ?, run_at = ?, lease_until = NULL, lease_token = NULL, error = ?
				WHERE id = ? AND lease_token = ?""", giveUp ? StepStatus.FAILED : StepStatus.PENDING,
				utc(giveUp ? now : now.plus(wait).plus(jitter)), truncate(error), step.id(), step.leaseToken());
		if (giveUp && updated > 0) {
			needsOwner(step.dueId());
		}
		if (updated > 0) {
			this.events.publishEvent(new DueChanged(step.dueId()));
		}
	}

	/**
	 * Hands back steps whose worker stopped (the process died, or lost its database connection) before finishing:
	 * their lease ran out. A worker still busy with its step holds the step's row lock, so {@code SKIP LOCKED} leaves
	 * it alone however long its send takes. A step that has used every try (it keeps killing its worker, say) fails
	 * instead of looping forever. @return how many steps were handed back or failed
	 */
	@Transactional
	int reap() {
		Instant now = this.clock.instant();
		List<Reaped> reaped = this.jdbc.query("""
				UPDATE due_steps SET status = CASE WHEN attempt >= ? THEN 'FAILED' ELSE 'PENDING' END,
				                     run_at = ?, lease_until = NULL, lease_token = NULL,
				                     error = CASE WHEN attempt >= ? THEN 'The worker stopped mid-step on every try'
				                                  ELSE 'The worker stopped mid-step' END
				WHERE id IN (
				    SELECT id FROM due_steps
				    WHERE status = 'RUNNING' AND lease_until < ?
				    ORDER BY lease_until
				    FOR UPDATE SKIP LOCKED
				    LIMIT 500)
				RETURNING due_id, status""",
				(rs, row) -> new Reaped(rs.getLong("due_id"), StepStatus.FAILED.equals(rs.getString("status"))),
				this.properties.maxAttempts(), utc(now), this.properties.maxAttempts(), utc(now));
		reaped.stream().filter(Reaped::failed).forEach((step) -> needsOwner(step.dueId()));
		reaped.stream().map(Reaped::dueId).distinct().forEach((due) -> this.events.publishEvent(new DueChanged(due)));
		return reaped.size();
	}

	/** A step failed for good: the due shows as needing the owner, unless it is already closed. */
	private void needsOwner(long dueId) {
		this.jdbc.update("UPDATE dues SET status = ? WHERE id = ? AND status IN (?, ?)", DueStatus.ESCALATED, dueId,
				DueStatus.SCHEDULED, DueStatus.ACTIVE);
	}

	private static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private static String truncate(String error) {
		String text = (error == null) ? "Unknown error" : error;
		return (text.length() <= 500) ? text : text.substring(0, 500);
	}

	private record Reaped(long dueId, boolean failed) {
	}

}
