package com.paynudge.demo;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.JsonPath;
import com.paynudge.IntegrationTest;
import com.paynudge.channel.Channel.OutboundMessage;
import com.paynudge.engine.StepWorker;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/** "Try the demo": a business full of believable history, live from the first second, gone a day later. */
class DemoApiTest extends IntegrationTest {

	private static final Pattern PAY_LINK = Pattern.compile("/pay/([\\w-]{43})");

	@Autowired
	private StepWorker worker;

	@Autowired
	private DemoCleanup cleanup;

	@Test
	void theDemoOpensABusinessWithAMonthOfHistory() {
		String token = startDemo();

		MvcTestResult me = getAs(token, "/api/me");
		assertThat(me).bodyJson().extractingPath("$.business.name").isEqualTo("Sunrise Tuition Centre");
		assertThat(me).bodyJson().extractingPath("$.business.demo").isEqualTo(true);
		assertThat(me).bodyJson().extractingPath("$.email").asString().endsWith("@demo.paynudge.invalid");

		// Ten students; this month's fees in every state, and two earlier months all paid.
		assertThat(getAs(token, "/api/customers")).bodyJson().extractingPath("$.totalItems").isEqualTo(10);
		assertThat(getAs(token, "/api/dues")).bodyJson().extractingPath("$.counts").isEqualTo(Map.of("all", 30,
				"open", 7, "needsYou", 1, "overdue", 3, "paid", 22, "cancelled", 1));
		MvcTestResult dashboard = getAs(token, "/api/dashboard");
		assertThat(dashboard).bodyJson().extractingPath("$.needsYou").isEqualTo(1);
		List<Integer> weekly = JsonPath.read(bodyOf(dashboard), "$.collections[*].paise");
		assertThat(weekly.stream().filter((paise) -> paise > 0)).as("weeks with money collected").hasSizeGreaterThan(5);
	}

	@Test
	void pastRemindersCarryTheEnginesWordsAndWorkingPayLinks() {
		String token = startDemo();
		long kabir = dueOf(token, "Kabir Mehta", "OPEN");

		MvcTestResult due = getAs(token, "/api/dues/" + kabir);

		assertThat(due).bodyJson().extractingPath("$.status").isEqualTo("ESCALATED");
		assertThat(due).bodyJson().extractingPath("$.steps[*].status")
			.isEqualTo(List.of("DONE", "DONE", "DONE", "DONE", "PENDING"));
		assertThat(due).bodyJson().extractingPath("$.messages[*].channel")
			.isEqualTo(List.of("EMAIL", "EMAIL", "EMAIL", "OWNER_EMAIL"));
		String overdue = JsonPath.read(bodyOf(due), "$.messages[2].body");
		assertThat(overdue).startsWith("Hi Kabir Mehta,").contains("₹4,500").contains("Stop these reminders:");
		Matcher link = PAY_LINK.matcher(overdue);
		assertThat(link.find()).isTrue();
		assertThat(this.mvc.get().uri("/api/public/pay/" + link.group(1)).exchange()).bodyJson()
			.extractingPath("$.status")
			.isEqualTo("OPEN");
	}

	@Test
	void aReminderGoesOutTheMomentTheDemoOpens() {
		startDemo();

		this.worker.drain();

		// Meera's first reminder was due as the demo opened; Rohan's fee is due today at 10:00, which is now.
		assertThat(this.channel.sent()).extracting(OutboundMessage::to)
			.containsExactlyInAnyOrder("meera.iyer@example.com", "rohan.gupta@example.com");
	}

	@Test
	void aDemoOwnerIsNeverEmailed() {
		String token = startDemo();
		long diya = dueOf(token, "Diya Rao", "OPEN");

		// Diya's fee is five days overdue; the owner hears seven days after, two days from now.
		this.clock.advance(Duration.ofDays(2));
		this.worker.drain();

		assertThat(this.jdbc.queryForList("SELECT channel FROM messages WHERE due_id = ?", String.class, diya))
			.contains("OWNER_EMAIL");
		assertThat(this.emails.sent()).as("the notice is kept on the timeline, not emailed").isEmpty();
	}

	@Test
	void eachDemoIsItsOwnBusinessAndGoneADayLater() {
		String first = startDemo();
		String second = startDemo();
		signUpOwner("asha@example.com", "Bright Tuition");
		assertThat(JsonPath.<Integer>read(bodyOf(getAs(first, "/api/me")), "$.business.id"))
			.isNotEqualTo(JsonPath.<Integer>read(bodyOf(getAs(second, "/api/me")), "$.business.id"));

		this.clock.advance(Duration.ofHours(23));
		assertThat(this.cleanup.deleteExpired()).as("a demo younger than a day stays").isZero();

		this.clock.advance(Duration.ofHours(2));
		assertThat(this.cleanup.deleteExpired()).isEqualTo(2);

		assertThat(this.jdbc.queryForList("SELECT name FROM businesses", String.class)).containsExactly("Bright Tuition");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM users", Long.class)).isEqualTo(1);
		for (String table : List.of("customers", "dues", "due_steps", "messages", "payments")) {
			assertThat(this.jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class)).as(table).isZero();
		}
	}

	private String startDemo() {
		MvcTestResult result = this.mvc.post().uri("/api/auth/demo").exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return JsonPath.read(bodyOf(result), "$.token");
	}

	/** The id of the customer's due in the given list filter (open, paid...). */
	private long dueOf(String token, String customer, String filter) {
		List<Integer> ids = JsonPath.read(bodyOf(getAs(token, "/api/dues?filter=" + filter.toLowerCase())),
				"$.items[?(@.customer.name == '" + customer + "')].id");
		return ids.get(0);
	}

}
