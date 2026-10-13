package com.paynudge.engine;

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
 * a reminder due next week is just a row with a future run time.
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
			this.threads.add(Thread.ofVirtual().name("step-worker-" + i).start(this::loop));
		}
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

	private void loop() {
		long idleMillis = this.properties.idleSleep().toMillis();
		while (this.running) {
			try {
				if (!this.worker.runOne()) {
					Thread.sleep(idleMillis + ThreadLocalRandom.current().nextLong(idleMillis / 2 + 1));
				}
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
			catch (RuntimeException ex) {
				// The database is briefly unreachable, say. Back off and keep the loop alive.
				log.warn("Step worker error: {}", ex.getMessage());
				try {
					Thread.sleep(idleMillis * 4);
				}
				catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
	}

}
