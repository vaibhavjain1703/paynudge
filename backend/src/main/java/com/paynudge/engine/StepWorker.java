package com.paynudge.engine;

import java.util.Optional;

import com.paynudge.channel.Channel.ChannelException;
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

	private final DailyCap dailyCap;

	StepWorker(StepQueue queue, StepExecutor executor, DailyCap dailyCap) {
		this.queue = queue;
		this.executor = executor;
		this.dailyCap = dailyCap;
	}

	/**
	 * Claims and runs one step. A customer email first takes one of the business's sends for the day; with none left
	 * the step waits for tomorrow. @return false when no step is due
	 */
	public boolean runOne() {
		Optional<ClaimedStep> claimed = this.queue.claim();
		if (claimed.isEmpty()) {
			return false;
		}
		ClaimedStep step = claimed.get();
		Optional<DailyCap.Slot> slot = Optional.empty();
		if (step.emailsCustomer()) {
			slot = this.dailyCap.take(step.dueId());
			if (slot.isEmpty()) {
				this.queue.postpone(step, this.dailyCap.nextDay(step.dueId()), "Today's email limit was reached");
				return true;
			}
		}
		boolean sent = false;
		try {
			sent = this.executor.execute(step);
		}
		catch (ChannelException ex) {
			log.warn("Send failed dueId={} stepKey={} attempt={} permanent={}: {}", step.dueId(), step.stepKey(),
					step.attempt(), ex.permanent(), ex.getMessage());
			this.queue.failed(step, ex.getMessage(), ex.permanent());
		}
		catch (RuntimeException ex) {
			log.warn("Step failed dueId={} stepKey={} attempt={}: {}", step.dueId(), step.stepKey(), step.attempt(),
					ex.getMessage());
			this.queue.failed(step, ex.getMessage(), false);
		}
		finally {
			if (!sent) {
				slot.ifPresent(this.dailyCap::giveBack);
			}
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

	/** Hands back steps whose worker stopped mid-step. @return how many */
	public int reap() {
		return this.queue.reap();
	}

}
