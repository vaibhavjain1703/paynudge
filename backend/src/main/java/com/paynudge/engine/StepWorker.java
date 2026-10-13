package com.paynudge.engine;

import java.util.Optional;

import com.paynudge.engine.StepQueue.ClaimedStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/** One unit of engine work: claim a due step and run it. The worker loops and the tests both drive this. */
@Component
public class StepWorker {

	private static final Logger log = LoggerFactory.getLogger(StepWorker.class);

	private final StepQueue queue;

	private final StepExecutor executor;

	StepWorker(StepQueue queue, StepExecutor executor) {
		this.queue = queue;
		this.executor = executor;
	}

	/** Claims and runs one step. @return false when no step is due */
	public boolean runOne() {
		Optional<ClaimedStep> claimed = this.queue.claim();
		if (claimed.isEmpty()) {
			return false;
		}
		ClaimedStep step = claimed.get();
		try {
			this.executor.execute(step);
		}
		catch (RuntimeException ex) {
			log.warn("Step failed dueId={} stepKey={} attempt={}: {}", step.dueId(), step.stepKey(), step.attempt(),
					ex.getMessage());
			this.queue.retryLater(step, ex.getMessage());
		}
		return true;
	}

	/** Runs every step that is due now, one after another. @return how many ran */
	public int drain() {
		int ran = 0;
		while (runOne()) {
			ran++;
		}
		return ran;
	}

}
