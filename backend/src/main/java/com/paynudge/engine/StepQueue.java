package com.paynudge.engine;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.paynudge.dues.StepStatus;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Postgres is the queue: a step is a row with a run time. Claiming takes the oldest due step with
 * {@code FOR UPDATE SKIP LOCKED}, so any number of workers each get a different one without waiting on each other,
 * and leases it for a while. Times come from the injected clock, so tests can move time.
 */
@Component
class StepQueue {

	private final JdbcTemplate jdbc;

	private final Clock clock;

	private final EngineProperties properties;

	StepQueue(JdbcTemplate jdbc, Clock clock, EngineProperties properties) {
		this.jdbc = jdbc;
		this.clock = clock;
		this.properties = properties;
	}

	/** A step a worker has claimed. {@code attempt} counts this try (1 for the first). */
	record ClaimedStep(long id, long dueId, String stepKey, String action, int attempt) {
	}

	@Transactional
	Optional<ClaimedStep> claim() {
		Instant now = this.clock.instant();
		List<ClaimedStep> claimed = this.jdbc.query("""
				UPDATE due_steps SET status = 'RUNNING', attempt = attempt + 1, lease_until = ?
				WHERE id = (
				    SELECT id FROM due_steps
				    WHERE status = 'PENDING' AND run_at <= ?
				    ORDER BY run_at, id
				    FOR UPDATE SKIP LOCKED
				    LIMIT 1)
				RETURNING id, due_id, step_key, action, attempt""",
				(rs, row) -> new ClaimedStep(rs.getLong("id"), rs.getLong("due_id"), rs.getString("step_key"),
						rs.getString("action"), rs.getInt("attempt")),
				now.plus(this.properties.lease()).atOffset(ZoneOffset.UTC), now.atOffset(ZoneOffset.UTC));
		return claimed.stream().findFirst();
	}

	/**
	 * After a failed try: back to PENDING with a growing delay (30 s, 1 min, 2 min, ...), or FAILED once every attempt
	 * is used. Only touches the step if this worker still holds it.
	 */
	@Transactional
	void retryLater(ClaimedStep step, String error) {
		boolean exhausted = step.attempt() >= this.properties.maxAttempts();
		Instant retryAt = this.clock.instant()
			.plus(this.properties.retryDelay().multipliedBy(1L << Math.min(step.attempt() - 1, 20)));
		this.jdbc.update("""
				UPDATE due_steps SET status = ?, run_at = ?, lease_until = NULL, error = ?
				WHERE id = ? AND status = 'RUNNING' AND attempt = ?""",
				exhausted ? StepStatus.FAILED : StepStatus.PENDING,
				exhausted ? this.clock.instant().atOffset(ZoneOffset.UTC) : retryAt.atOffset(ZoneOffset.UTC),
				truncate(error), step.id(), step.attempt());
	}

	private static String truncate(String error) {
		String text = (error == null) ? "Unknown error" : error;
		return (text.length() <= 500) ? text : text.substring(0, 500);
	}

}
