package com.paynudge.realtime;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.jayway.jsonpath.JsonPath;
import com.paynudge.IntegrationTest;
import com.paynudge.engine.StepWorker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The owner's live stream: what an open page hears, once the change has committed, and that no other business hears
 * it. Streams are read as the test's MockMvc writes them, without waiting for them to end.
 */
class LiveUpdatesApiTest extends IntegrationTest {

	@Autowired
	private MockMvc streams;

	@Autowired
	private LiveUpdates live;

	@Autowired
	private StepWorker worker;

	private String asha;

	private long due;

	@BeforeEach
	void ashaWithADueWhoseFirstReminderIsDue() {
		this.asha = signUpOwner("asha@example.com", "Bright Tuition");
		long customer = idOf(
				postAs(this.asha, "/api/customers", Map.of("name", "Aarav", "email", "aarav@example.com")));
		long policy = ((Number) JsonPath.read(bodyOf(getAs(this.asha, "/api/policies")), "$[0].id")).longValue();
		// Due on 7 January: the first email (two days before) is due now, on 5 January.
		this.due = idOf(postAs(this.asha, "/api/dues",
				Map.of("customerId", customer, "amountPaise", 150000, "dueDate", "2026-01-07", "policyId", policy)));
	}

	@AfterEach
	void closeStreams() {
		this.live.stop();
		this.live.start();
	}

	@Test
	void aStreamStartsReady() throws Exception {
		MvcResult stream = open(this.asha);

		assertThat(read(stream)).startsWith("event:ready\ndata:{}");
	}

	@Test
	void aPaidDueReachesTheOwnersOpenPagesAndNoOneElses() throws Exception {
		String ravi = signUpOwner("ravi@example.com", "Ravi Gym");
		MvcResult ashas = open(this.asha);
		MvcResult ravis = open(ravi);

		postAs(this.asha, "/api/dues/" + this.due + "/mark-paid", Map.of());

		String event = eventually(() -> read(ashas), "event:due");
		String data = event.substring(event.indexOf("event:due")).split("\n")[1].substring("data:".length());
		Object due = JsonPath.read(data, "$");
		assertThat(JsonPath.<Integer>read(due, "$.id")).isEqualTo((int) this.due);
		assertThat(JsonPath.<String>read(due, "$.status")).isEqualTo("PAID");
		assertThat(JsonPath.<List<String>>read(due, "$.steps[*].status")).containsOnly("CANCELLED");
		Thread.sleep(200);
		assertThat(read(ravis)).as("another business hears nothing").isEqualTo("event:ready\ndata:{}\n\n");
	}

	@Test
	void aReminderGoingOutReachesTheTimeline() throws Exception {
		MvcResult stream = open(this.asha);

		this.worker.drain();

		String events = eventually(() -> read(stream), "A quick reminder from Bright Tuition");
		assertThat(events).contains("event:due").contains("\"stepKey\":\"remind_1\"");
	}

	@Test
	void manyDuesChangingAtOnceTellPagesToRefetch() throws Exception {
		MvcResult stream = open(this.asha);

		postAs(this.asha, "/api/dues/bulk", Map.of("ids", List.of(this.due), "action", "cancel"));

		eventually(() -> read(stream), "event:dues");
	}

	@Test
	void nobodyWatchingMeansNothingIsBuiltOrSent() {
		long business = this.jdbc.queryForObject("SELECT id FROM businesses", Long.class);

		assertThat(this.live.watching(business)).isFalse();
		postAs(this.asha, "/api/dues/" + this.due + "/mark-paid", Map.of());
		assertThat(this.live.watching(business)).isFalse();
	}

	@Test
	void theStreamNeedsASignedInOwner() throws Exception {
		this.streams.perform(get("/api/events")).andExpect(status().isUnauthorized());
	}

	private MvcResult open(String token) throws Exception {
		return this.streams.perform(get("/api/events").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
			.andExpect(request().asyncStarted())
			.andReturn();
	}

	private static String read(MvcResult stream) {
		return new String(stream.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
	}

	/** Waits up to five seconds for the stream to contain {@code expected}. */
	private static String eventually(Supplier<String> stream, String expected) throws InterruptedException {
		long deadline = System.nanoTime() + 5_000_000_000L;
		String seen = stream.get();
		while (!seen.contains(expected) && System.nanoTime() < deadline) {
			Thread.sleep(20);
			seen = stream.get();
		}
		assertThat(seen).contains(expected);
		return seen;
	}

	private static long idOf(MvcTestResult result) {
		return ((Number) JsonPath.read(bodyOf(result), "$.id")).longValue();
	}

}
