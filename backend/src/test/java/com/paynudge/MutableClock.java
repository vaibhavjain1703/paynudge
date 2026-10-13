package com.paynudge;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** A {@link Clock} that tests set and advance by hand. Thread-safe so worker threads see the same time. */
public final class MutableClock extends Clock {

	/** Monday 2026-01-05 10:00 IST: inside business hours, away from any month or DST edge. */
	public static final Instant DEFAULT_START = Instant.parse("2026-01-05T04:30:00Z");

	private final AtomicReference<Instant> now;

	private final ZoneId zone;

	public MutableClock() {
		this(new AtomicReference<>(DEFAULT_START), ZoneOffset.UTC);
	}

	private MutableClock(AtomicReference<Instant> now, ZoneId zone) {
		this.now = now;
		this.zone = zone;
	}

	@Override
	public ZoneId getZone() {
		return this.zone;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return new MutableClock(this.now, zone);
	}

	@Override
	public Instant instant() {
		return this.now.get();
	}

	public void set(Instant instant) {
		this.now.set(instant);
	}

	public void advance(Duration duration) {
		this.now.updateAndGet(current -> current.plus(duration));
	}

	public void reset() {
		set(DEFAULT_START);
	}

}
