package com.paynudge.engine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * The worker pool: {@code paynudge.engine.workers} loops on virtual threads. Each claims and runs steps while there
 * are any, and naps briefly (with jitter, so loops don't poll in step) when the queue is empty. A wait costs nothing:
 * a reminder due next week is just a row with a future run time. One more loop, the reaper, hands back the steps of
 * workers that died (in this process or another one) every {@code reaper-interval}.
 */
@Component
class StepWorkers implements SmartLifecycle {

	private static final Logger log = LoggerFactory.getLogger(StepWorkers.class);

	private final StepWorker worker;

	private final EngineProperties properties;

	private final List<Thread> threads = new ArrayList<>();

	private volatile boolean running;

	StepWorkers(StepWorker worker, EngineProperties properties) {
		this.worker = worker;
		this.properties = properties;
	}

	@Override
	public synchronized void start() {
		if (this.running || this.properties.workers() <= 0) {
			return;
		}
		this.running = true;
		for (int i = 1; i <= this.properties.workers(); i++) {
			this.threads.add(Thread.ofVirtual().name("step-worker-" + i).start(this::work));
		}
		this.threads.add(Thread.ofVirtual().name("step-reaper").start(this::reap));
		log.info("Started {} step workers", this.properties.workers());
	}

	@Override
	public synchronized void stop() {
		this.running = false;
		this.threads.forEach(Thread::interrupt);
		for (Thread thread : this.threads) {
			try {
				thread.join(5_000);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
		this.threads.clear();
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}

	private void work() {
		long idleMillis = this.properties.idleSleep().toMillis();
		loop(() -> {
			if (!this.worker.runOne()) {
				Thread.sleep(idleMillis + ThreadLocalRandom.current().nextLong(idleMillis / 2 + 1));
			}
		}, Duration.ofMillis(idleMillis * 4));
	}

	private void reap() {
		loop(() -> {
			Thread.sleep(this.properties.reaperInterval().toMillis());
			int reaped = this.worker.reap();
			if (reaped > 0) {
				log.warn("Handed back {} steps whose worker stopped mid-step", reaped);
			}
		}, this.properties.reaperInterval());
	}

	/** Runs {@code round} until stopped. An error (the database briefly unreachable, say) pauses, then carries on. */
	private void loop(Round round, Duration pauseAfterError) {
		while (this.running) {
			try {
				round.run();
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
			catch (RuntimeException ex) {
				log.warn("{} error: {}", Thread.currentThread().getName(), ex.getMessage());
				try {
					Thread.sleep(pauseAfterError.toMillis());
				}
				catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
	}

	@FunctionalInterface
	private interface Round {

		void run() throws InterruptedException;

	}

}
