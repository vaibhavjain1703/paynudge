package com.paynudge;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.paynudge.channel.Channel;

/** The fake customer channel: records every reminder instead of sending it, and can fail the next sends. */
public class RecordingChannel implements Channel {

	private final List<OutboundMessage> sent = new CopyOnWriteArrayList<>();

	private final AtomicInteger failuresLeft = new AtomicInteger();

	@Override
	public String name() {
		return "EMAIL";
	}

	@Override
	public String send(OutboundMessage message) {
		if (this.failuresLeft.getAndUpdate((left) -> Math.max(0, left - 1)) > 0) {
			throw new ChannelException("simulated provider outage", null);
		}
		this.sent.add(message);
		return "fake-" + this.sent.size();
	}

	public List<OutboundMessage> sent() {
		return List.copyOf(this.sent);
	}

	public void failNext(int sends) {
		this.failuresLeft.set(sends);
	}

	public void reset() {
		this.sent.clear();
		this.failuresLeft.set(0);
	}

}
