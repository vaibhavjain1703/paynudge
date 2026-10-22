package com.paynudge.dues;

import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import com.jayway.jsonpath.JsonPath;
import com.paynudge.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/** The dues list: filter chips with counts, search, and acting on many dues at once. Today is 5 January 2026. */
class DueListApiTest extends IntegrationTest {

	private String token;

	private long aarav;

	private long bilal;

	private long chitra;

	private long dev;

	private long esha;

	@BeforeEach
	void fiveDuesInEveryState() {
		this.token = signUpOwner("asha@example.com", "Bright Tuition");
		this.aarav = due("Aarav Shah", "2026-01-20"); // open
		this.bilal = due("Bilal Khan", "2026-01-02"); // open and overdue
		this.chitra = due("Chitra Rao", "2026-01-03");
		this.dev = due("Dev Mehta", "2026-01-10");
		this.esha = due("Esha Nair", "2026-01-01"); // overdue, and needs the owner
		postAs(this.token, "/api/dues/" + this.chitra + "/mark-paid", Map.of());
		postAs(this.token, "/api/dues/" + this.dev + "/cancel", Map.of());
		this.jdbc.update("UPDATE dues SET status = 'ESCALATED' WHERE id = ?", this.esha);
	}

	@Test
	void everyChipShowsItsCountAndItsDues() {
		assertThat(list("")).hasStatusOk().bodyJson().extractingPath("$.counts").isEqualTo(
				Map.of("all", 5, "open", 3, "needsYou", 1, "overdue", 2, "paid", 1, "cancelled", 1));

		assertThat(names("")).containsExactly("Esha Nair", "Bilal Khan", "Chitra Rao", "Dev Mehta", "Aarav Shah");
		assertThat(names("?filter=open")).containsExactly("Esha Nair", "Bilal Khan", "Aarav Shah");
		assertThat(names("?filter=overdue")).containsExactly("Esha Nair", "Bilal Khan");
		assertThat(names("?filter=needs-you")).containsExactly("Esha Nair");
		assertThat(names("?filter=paid")).containsExactly("Chitra Rao");
		assertThat(names("?filter=cancelled")).containsExactly("Dev Mehta");
		assertThat(list("?filter=overdue")).bodyJson().extractingPath("$.totalItems").isEqualTo(2);
	}

	@Test
	void searchNarrowsTheListAndTheCounts() {
		MvcTestResult result = list("?q=  KHAN ");

		assertThat(names("?q=  KHAN ")).containsExactly("Bilal Khan");
		assertThat(result).bodyJson().extractingPath("$.counts.all").isEqualTo(1);
		assertThat(result).bodyJson().extractingPath("$.counts.overdue").isEqualTo(1);
		assertThat(names("?q=chitra.rao@")).containsExactly("Chitra Rao");
		assertThat(names("?q=%")).as("a % is looked for literally, not as a wildcard").isEmpty();
		assertThat(names("?filter=paid&q=khan")).isEmpty();
	}

	@Test
	void anUnknownFilterIsABadRequest() {
		assertThat(list("?filter=late")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.details.filter")
			.isEqualTo("Use one of: all, open, needs-you, overdue, paid, cancelled");
	}

	@Test
	void manyDuesAreMarkedPaidAtOnceSkippingThoseThatCannotBe() {
		MvcTestResult result = postAs(this.token, "/api/dues/bulk", Map.of("ids",
				List.of(this.aarav, this.bilal, this.chitra, this.dev, 999_999L), "action", "mark-paid"));

		assertThat(result).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{"updated":2,"skipped":3}""");
		assertThat(names("?filter=paid")).containsExactly("Bilal Khan", "Chitra Rao", "Aarav Shah");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM due_steps WHERE due_id IN (?, ?) AND status = 'PENDING'",
				Long.class, this.aarav, this.bilal))
			.as("their reminders stopped")
			.isZero();
		assertThat(names("?filter=cancelled")).as("a cancelled due stays cancelled").containsExactly("Dev Mehta");
	}

	@Test
	void manyDuesAreCancelledAtOnce() {
		MvcTestResult result = postAs(this.token, "/api/dues/bulk",
				Map.of("ids", List.of(this.aarav, this.esha, this.chitra), "action", "cancel"));

		assertThat(result).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{"updated":2,"skipped":1}""");
		assertThat(names("?filter=cancelled")).containsExactly("Esha Nair", "Dev Mehta", "Aarav Shah");
		assertThat(names("?filter=paid")).as("a paid due stays paid").containsExactly("Chitra Rao");
	}

	@Test
	void aBulkActionNeedsAKnownActionAndAHundredDuesAtMost() {
		assertThat(postAs(this.token, "/api/dues/bulk", Map.of("ids", List.of(this.aarav), "action", "delete")))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.details.action")
			.isEqualTo("Use mark-paid or cancel");
		assertThat(postAs(this.token, "/api/dues/bulk", Map.of("ids", List.of(), "action", "cancel")))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(postAs(this.token, "/api/dues/bulk",
				Map.of("ids", LongStream.rangeClosed(1, 101).boxed().toList(), "action", "cancel")))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.details.ids")
			.isEqualTo("Choose between 1 and 100 dues");
	}

	private MvcTestResult list(String query) {
		return getAs(this.token, "/api/dues" + query);
	}

	private List<String> names(String query) {
		return JsonPath.read(bodyOf(list(query)), "$.items[*].customer.name");
	}

	private long due(String name, String dueDate) {
		long policy = ((Number) JsonPath.read(bodyOf(getAs(this.token, "/api/policies")), "$[0].id")).longValue();
		String email = name.toLowerCase().replace(' ', '.') + "@example.com";
		long customer = idOf(postAs(this.token, "/api/customers", Map.of("name", name, "email", email)));
		return idOf(postAs(this.token, "/api/dues",
				Map.of("customerId", customer, "amountPaise", 150000, "dueDate", dueDate, "policyId", policy)));
	}

	private static long idOf(MvcTestResult result) {
		return ((Number) JsonPath.read(bodyOf(result), "$.id")).longValue();
	}

}
