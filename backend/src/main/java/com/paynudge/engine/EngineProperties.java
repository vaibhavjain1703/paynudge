package com.paynudge.engine;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param workers worker loops (virtual threads); 0 switches the engine off, as tests do to drive it by hand
 * @param lease how long a claimed step belongs to its worker before it may be taken back
 * @param idleSleep how long a worker waits when nothing is due (plus jitter)
 * @param maxAttempts tries before a step is marked FAILED (a wait for quiet hours or the daily cap is not a try)
 * @param retryDelay the first retry's delay; each later one doubles it, plus up to a tenth more at random
 * @param reaperInterval how often expired leases are looked for
 * @param minGap the least time between two emails to the same customer, across all their dues
 */
@ConfigurationProperties("paynudge.engine")
public record EngineProperties(@DefaultValue("4") int workers, @DefaultValue("60s") Duration lease,
		@DefaultValue("250ms") Duration idleSleep, @DefaultValue("5") int maxAttempts,
		@DefaultValue("30s") Duration retryDelay, @DefaultValue("15s") Duration reaperInterval,
		@DefaultValue("24h") Duration minGap) {
}
