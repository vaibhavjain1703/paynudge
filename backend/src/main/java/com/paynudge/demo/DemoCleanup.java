package com.paynudge.demo;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Deletes demo businesses a day after they were made, with their owner and everything in them. A demo session lasts
 * two hours, so nobody is still using one by then.
 */
@Component
class DemoCleanup {

	static final Duration LIFETIME = Duration.ofDays(1);

	/** The most demo businesses one run deletes; the next run picks up the rest. */
	private static final int BATCH = 100;

	private static final Logger log = LoggerFactory.getLogger(DemoCleanup.class);

	private final JdbcTemplate jdbc;

	private final TransactionTemplate transactions;

	private final Clock clock;

	DemoCleanup(JdbcTemplate jdbc, TransactionTemplate transactions, Clock clock) {
		this.jdbc = jdbc;
		this.transactions = transactions;
		this.clock = clock;
	}

	@Scheduled(initialDelayString = "PT5M", fixedDelayString = "PT1H")
	void onSchedule() {
		int deleted = deleteExpired();
		if (deleted > 0) {
			log.info("Deleted {} expired demo businesses", deleted);
		}
	}

	/** @return how many demo businesses were deleted */
	int deleteExpired() {
		return this.transactions.execute((status) -> {
			List<Map<String, Object>> expired = this.jdbc.queryForList("""
					SELECT id, owner_id FROM businesses WHERE demo AND created_at < ?
					ORDER BY id LIMIT ? FOR UPDATE SKIP LOCKED""",
					this.clock.instant().minus(LIFETIME).atOffset(ZoneOffset.UTC), BATCH);
			for (Map<String, Object> business : expired) {
				long id = ((Number) business.get("id")).longValue();
				// Steps, messages and payments go with their dues; the daily send counts with the business.
				this.jdbc.update("DELETE FROM dues WHERE business_id = ?", id);
				this.jdbc.update("DELETE FROM customers WHERE business_id = ?", id);
				this.jdbc.update("DELETE FROM policies WHERE business_id = ?", id);
				this.jdbc.update("DELETE FROM businesses WHERE id = ?", id);
				this.jdbc.update("DELETE FROM users WHERE id = ?", ((Number) business.get("owner_id")).longValue());
			}
			return expired.size();
		});
	}

}
