package com.paynudge.realtime;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.SmartLifecycle;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Open event streams, by business: every signed-in tab of an owner holds one. On connect a stream gets "ready" (the
 * page refetches what it shows, so a reconnect is never stale); after that, whatever is sent for the business. A
 * comment every 25 seconds keeps proxies from closing a quiet stream. One instance holds them all, which suits the
 * single API process of the MVP; more instances would need Postgres LISTEN/NOTIFY between them.
 */
@Component
public class LiveUpdates implements SmartLifecycle {

	/** A stream ends after this; the browser reconnects at once. */
	static final Duration STREAM_LIFETIME = Duration.ofMinutes(30);

	static final Duration HEARTBEAT = Duration.ofSeconds(25);

	private static final Logger log = LoggerFactory.getLogger(LiveUpdates.class);

	private final Map<Long, Set<SseEmitter>> streams = new ConcurrentHashMap<>();

	private volatile Thread heartbeat;

	/** A new stream for the business, already sent its "ready" event. */
	public SseEmitter subscribe(long businessId) {
		SseEmitter stream = new SseEmitter(STREAM_LIFETIME.toMillis());
		Set<SseEmitter> open = this.streams.computeIfAbsent(businessId, (id) -> ConcurrentHashMap.newKeySet());
		open.add(stream);
		Runnable remove = () -> open.remove(stream);
		stream.onCompletion(remove);
		stream.onTimeout(remove);
		stream.onError((ex) -> remove.run());
		deliver(businessId, stream, SseEmitter.event().name("ready").data("{}", MediaType.APPLICATION_JSON));
		return stream;
	}

	/** Whether anyone is watching the business: when no one is, there is nothing to build or send. */
	public boolean watching(long businessId) {
		Set<SseEmitter> open = this.streams.get(businessId);
		return open != null && !open.isEmpty();
	}

	/** Sends {@code data} as JSON, as event {@code name}, to every stream of the business. */
	public void send(long businessId, String name, Object data) {
		Set<SseEmitter> open = this.streams.get(businessId);
		if (open == null) {
			return;
		}
		for (SseEmitter stream : open) {
			deliver(businessId, stream, SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
		}
	}

	private void deliver(long businessId, SseEmitter stream, SseEmitter.SseEventBuilder event) {
		try {
			stream.send(event);
		}
		catch (IOException | IllegalStateException ex) {
			// The tab went away. Its stream is dropped; the browser makes a new one if it comes back.
			Set<SseEmitter> open = this.streams.get(businessId);
			if (open != null) {
				open.remove(stream);
			}
			stream.completeWithError(ex);
		}
	}

	@Override
	public void start() {
		this.heartbeat = Thread.ofVirtual().name("live-heartbeat").start(() -> {
			while (!Thread.currentThread().isInterrupted()) {
				try {
					Thread.sleep(HEARTBEAT);
				}
				catch (InterruptedException ex) {
					return;
				}
				this.streams.forEach((businessId, open) -> open
					.forEach((stream) -> deliver(businessId, stream, SseEmitter.event().comment("keepalive"))));
			}
		});
		log.info("Live updates ready");
	}

	@Override
	public void stop() {
		Thread running = this.heartbeat;
		if (running != null) {
			running.interrupt();
		}
		this.streams.values().forEach((open) -> open.forEach(SseEmitter::complete));
		this.streams.clear();
		this.heartbeat = null;
	}

	@Override
	public boolean isRunning() {
		return this.heartbeat != null;
	}

}
