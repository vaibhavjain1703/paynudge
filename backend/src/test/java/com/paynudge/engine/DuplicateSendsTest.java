package com.paynudge.engine;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.paynudge.channel.Channel.OutboundMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Eight workers drain thousands of steps at once: every step ends DONE on its first try, every reminder is sent
 * exactly once, and each customer gets their emails in the policy's order.
 */
class DuplicateSendsTest extends EngineTestSupport {

	/** Five steps each ("Friendly then firm"): 2,500 steps. */
	private static final int DUES = 500;

	private static final int WORKERS = 8;

	/** The policy's five step days for a due on 20 January, at 10:00 Kolkata time. */
	private static final List<String> STEP_DAYS = List.of("2026-01-18", "2026-01-20", "2026-01-23", "2026-01-27",
			"2026-01-30");

	@Test
	void eightWorkersRunThousandsOfStepsAndSendEachReminderExactlyOnce() throws Exception {
		bulkDues(DUES, "2026-01-20");

		for (String day : STEP_DAYS) {
			this.clock.set(Instant.parse(day + "T04:30:00Z"));
			assertThat(drainWith(WORKERS)).as("steps run on %s", day).isEqualTo(DUES);
		}

		assertThat(this.jdbc.queryForList("""
				SELECT status || ' ' || attempt || ' x' || n
				FROM (SELECT status, attempt, count(*) AS n FROM due_steps GROUP BY status, attempt) steps""",
				String.class))
			.as("every step done, on its first try")
			.containsExactly("DONE 1 x" + DUES * STEP_DAYS.size());

		List<OutboundMessage> sent = this.channel.sent();
		assertThat(sent).as("four customer emails per due").hasSize(DUES * 4);
		assertThat(sent).extracting(OutboundMessage::reference).as("no (due, step) sent twice").doesNotHaveDuplicates();
		assertThat(this.emails.sent()).as("one owner notice per due").hasSize(DUES);
		assertThat(messageCount()).as("one message row per step").isEqualTo(DUES * STEP_DAYS.size());

		Map<String, List<String>> stepsByDue = sent.stream()
			.map(OutboundMessage::reference)
			.collect(Collectors.groupingBy((reference) -> reference.substring(0, reference.indexOf('.')),
					LinkedHashMap::new,
					Collectors.mapping((reference) -> reference.substring(reference.indexOf('.') + 1),
							Collectors.toList())));
		assertThat(stepsByDue).hasSize(DUES)
			.allSatisfy((due, steps) -> assertThat(steps).as(due)
				.containsExactly("remind_1", "remind_2", "remind_3", "final"));
	}

}
