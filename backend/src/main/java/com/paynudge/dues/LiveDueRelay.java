package com.paynudge.dues;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.paynudge.common.DueEvents.DueChanged;
import com.paynudge.common.DueEvents.DuesChanged;
import com.paynudge.realtime.LiveUpdates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells open pages what changed, once the change has committed (never what a rolled-back transaction did). The work
 * is handed to a virtual thread, so a worker that just sent a reminder never waits on someone's browser.
 * <ul>
 * <li>one due: event "due" with the due as its page shows it, which the page puts straight into its cache;</li>
 * <li>many dues: event "dues", and pages refetch what they show.</li>
 * </ul>
 */
@Component
class LiveDueRelay implements DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(LiveDueRelay.class);

	private final LiveUpdates live;

	private final DueQueries queries;

	private final JdbcTemplate jdbc;

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	LiveDueRelay(LiveUpdates live, DueQueries queries, JdbcTemplate jdbc) {
		this.live = live;
		this.queries = queries;
		this.jdbc = jdbc;
	}

	@TransactionalEventListener(fallbackExecution = true)
	void dueChanged(DueChanged event) {
		this.executor.execute(() -> {
			try {
				List<Long> business = this.jdbc.queryForList("SELECT business_id FROM dues WHERE id = ?", Long.class,
						event.dueId());
				if (business.isEmpty() || !this.live.watching(business.get(0))) {
					return;
				}
				this.queries.detail(business.get(0), event.dueId())
					.ifPresent((due) -> this.live.send(business.get(0), "due", due));
			}
			catch (RuntimeException ex) {
				log.warn("Live update for due {} not sent: {}", event.dueId(), ex.getMessage());
			}
		});
	}

	@TransactionalEventListener(fallbackExecution = true)
	void duesChanged(DuesChanged event) {
		if (this.live.watching(event.businessId())) {
			this.executor.execute(() -> this.live.send(event.businessId(), "dues", Map.of()));
		}
	}

	@Override
	public void destroy() {
		this.executor.shutdownNow();
	}

}
