package com.paynudge.security;

import java.time.Duration;
import java.util.Map;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.paynudge.common.ApiException;
import com.paynudge.common.ErrorCodes;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Token buckets (Bucket4j), one per rule and client: an address for public routes, an owner for the AI features. A
 * bucket refills gradually, so a client who waits a little gets a request back. Buckets live in this process's
 * memory (the API runs as one process) and are forgotten after two hours unused. {@code RATE_LIMITS_ENABLED=false}
 * turns every limit off (tests and the e2e suite, which sign up many owners from one address).
 */
@Component
public class RateLimits {

	public enum Rule {

		/** Password, Google and email-code sign-in and sign-up. */
		SIGN_IN(10, Duration.ofMinutes(1)),
		/** Sending a sign-in code (each email address has its own limits on top). */
		EMAIL_CODE(5, Duration.ofMinutes(10)),
		/** "Try the demo": each one fills a business with sample data. */
		DEMO(5, Duration.ofHours(1)),
		/** Making a Razorpay payment link: a call to Razorpay. */
		PAY_LINK(10, Duration.ofMinutes(1)),
		/** Other customer pages: pay and unsubscribe. */
		PUBLIC(60, Duration.ofMinutes(1)),
		/** Razorpay's events (it retries, so this is generous). */
		WEBHOOK(300, Duration.ofMinutes(1)),
		/** Each owner's AI requests (drafting, column suggestions, questions): they cost money. */
		AI(30, Duration.ofHours(1));

		private final long requests;

		private final Duration period;

		Rule(long requests, Duration period) {
			this.requests = requests;
			this.period = period;
		}

		Bucket newBucket() {
			return Bucket.builder()
				.addLimit((limit) -> limit.capacity(this.requests).refillGreedy(this.requests, this.period))
				.build();
		}

	}

	private static final Logger log = LoggerFactory.getLogger(RateLimits.class);

	private final boolean enabled;

	private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
		.expireAfterAccess(Duration.ofHours(2))
		.maximumSize(200_000)
		.build();

	RateLimits(@Value("${paynudge.rate-limits.enabled:true}") boolean enabled) {
		this.enabled = enabled;
	}

	/**
	 * Takes one request from the client's bucket for this rule.
	 * @return how long to wait before trying again, or {@link Duration#ZERO} when the request may go ahead
	 */
	public Duration take(Rule rule, String client) {
		if (!this.enabled) {
			return Duration.ZERO;
		}
		ConsumptionProbe probe = this.buckets.get(rule + ":" + client, (key) -> rule.newBucket())
			.tryConsumeAndReturnRemaining(1);
		if (probe.isConsumed()) {
			return Duration.ZERO;
		}
		log.info("Rate limited rule={} client={}", rule, client);
		return Duration.ofNanos(probe.getNanosToWaitForRefill());
	}

	/** {@link #take}, for code that answers with an exception: a 429 when the bucket is empty. */
	public void check(Rule rule, String client) {
		Duration wait = take(rule, client);
		if (!wait.isZero()) {
			throw tooMany(wait);
		}
	}

	static ApiException tooMany(Duration wait) {
		long seconds = Math.max(1, (wait.toMillis() + 999) / 1000);
		return new ApiException(HttpStatus.TOO_MANY_REQUESTS, ErrorCodes.RATE_LIMITED,
				"Too many requests. Try again in " + describe(seconds) + ".", Map.of("retryAfterSeconds", seconds));
	}

	private static String describe(long seconds) {
		if (seconds < 90) {
			return seconds + (seconds == 1 ? " second" : " seconds");
		}
		long minutes = (seconds + 59) / 60;
		return minutes + " minutes";
	}

}
