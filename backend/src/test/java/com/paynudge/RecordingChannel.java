package com.paynudge;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.paynudge.channel.Channel;

/**
 * The fake customer channel: records every reminder instead of sending it. It can fail the next sends (an outage,
 * or an address the provider refuses), and can hold the next send mid-call until the test lets it go, as a slow
 * provider would, or a worker that dies there.
 */
public class RecordingChannel implements Channel {

	private final List<OutboundMessage> sent = new CopyOnWriteArrayList<>();

	private final AtomicInteger failuresLeft = new AtomicInteger();

	private final AtomicInteger refusalsLeft = new AtomicInteger();

	private final AtomicReference<HeldSend> held = new AtomicReference<>();

	@Override
	public String name() {
		return "EMAIL";
	}

	@Override
	public String send(OutboundMessage message) {
		HeldSend hold = this.held.getAndSet(null);
		if (hold != null) {
			hold.waitForRelease();
		}
		if (this.refusalsLeft.getAndUpdate((left) -> Math.max(0, left - 1)) > 0) {
			throw new ChannelException("The mail server refused the address " + message.to(), null, true);
		}
		if (this.failuresLeft.getAndUpdate((left) -> Math.max(0, left - 1)) > 0) {
			throw new ChannelException("simulated provider outage", null, false);
		}
		this.sent.add(message);
		return "fake-" + this.sent.size();
	}

	public List<OutboundMessage> sent() {
		return List.copyOf(this.sent);
	}

	/** The next sends fail as an outage would: worth trying again. */
	public void failNext(int sends) {
		this.failuresLeft.set(sends);
	}

	/** The next sends are refused for good, as an address the provider rejects would be. */
	public void refuseNext(int sends) {
		this.refusalsLeft.set(sends);
	}

	/** The next send stops inside the provider call until {@link HeldSend#complete()} or {@link HeldSend#die()}. */
	public HeldSend holdNextSend() {
		HeldSend hold = new HeldSend();
		this.held.set(hold);
		return hold;
	}

	public void reset() {
		this.sent.clear();
		this.failuresLeft.set(0);
		this.refusalsLeft.set(0);
		HeldSend hold = this.held.getAndSet(null);
		if (hold != null) {
			hold.die();
		}
	}

	/** A send held mid-call. */
	public static final class HeldSend {

		private final CountDownLatch reached = new CountDownLatch(1);

		private final CountDownLatch released = new CountDownLatch(1);

		private volatile boolean dies;

		/** Waits until a worker is inside the held send. */
		public void awaitSender() throws InterruptedException {
			if (!this.reached.await(10, TimeUnit.SECONDS)) {
				throw new AssertionError("No worker reached the held send");
			}
		}

		/** The provider takes the message after all. */
		public void complete() {
			this.released.countDown();
		}

		/** The call never finishes: its worker is gone. The message never reached the provider. */
		public void die() {
			this.dies = true;
			this.released.countDown();
		}

		private void waitForRelease() {
			this.reached.countDown();
			try {
				if (!this.released.await(30, TimeUnit.SECONDS)) {
					throw new AssertionError("The held send was never released");
				}
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			if (this.dies) {
				throw new ChannelException("the worker died mid-send", null, false);
			}
		}

	}

}
