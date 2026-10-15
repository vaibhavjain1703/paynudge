package com.paynudge.customer;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import com.paynudge.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/** The link at the foot of every reminder, used by a customer who has no account. */
class UnsubscribeApiTest extends IntegrationTest {

	private String token;

	private long aarav;

	private long aaravsDue;

	private long diyasDue;

	@BeforeEach
	void twoCustomersWithADueEach() {
		this.token = signUpOwner("asha@example.com", "Bright Tuition");
		this.aarav = customer("Aarav", "aarav@example.com");
		this.aaravsDue = due(this.aarav);
		this.diyasDue = due(customer("Diya", "diya@example.com"));
	}

	@Test
	void theLinkShowsWhoseRemindersItStopsWithoutSigningIn() {
		MvcTestResult result = this.mvc.get().uri("/api/public/unsubscribe/" + unsubscribeToken(this.aarav)).exchange();

		assertThat(result).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{"businessName":"Bright Tuition","optedOut":false}""");
	}

	@Test
	void unsubscribingStopsEveryReminderToThatCustomerOnly() {
		MvcTestResult result = this.mvc.post().uri("/api/public/unsubscribe/" + unsubscribeToken(this.aarav)).exchange();

		assertThat(result).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{"businessName":"Bright Tuition","optedOut":true}""");
		assertThat(stepStatuses(this.aaravsDue)).containsOnly("CANCELLED Customer unsubscribed");
		assertThat(stepStatuses(this.diyasDue)).containsOnly("PENDING null");
		assertThat(optedOutAt()).isEqualTo(this.clock.instant().atOffset(ZoneOffset.UTC));
		assertThat(getAs(this.token, "/api/dues/" + this.aaravsDue)).bodyJson()
			.extractingPath("$.customer.optedOut")
			.isEqualTo(true);

		// A second click (or a mail client retrying) changes nothing, not even the date.
		this.clock.advance(Duration.ofDays(1));
		assertThat(this.mvc.post().uri("/api/public/unsubscribe/" + unsubscribeToken(this.aarav)).exchange())
			.hasStatusOk();
		assertThat(optedOutAt()).isEqualTo(this.clock.instant().minus(Duration.ofDays(1)).atOffset(ZoneOffset.UTC));
	}

	@Test
	void aMailClientsOneClickUnsubscribeWorks() {
		// RFC 8058: the client POSTs a form with List-Unsubscribe=One-Click to the URL in the List-Unsubscribe header.
		MvcTestResult result = this.mvc.post()
			.uri("/api/public/unsubscribe/" + unsubscribeToken(this.aarav))
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.content("List-Unsubscribe=One-Click")
			.exchange();

		assertThat(result).hasStatusOk().bodyJson().extractingPath("$.optedOut").isEqualTo(true);
	}

	@Test
	void anUnknownLinkIsNotFound() {
		for (MvcTestResult result : List.of(this.mvc.get().uri("/api/public/unsubscribe/not-a-real-token").exchange(),
				this.mvc.post().uri("/api/public/unsubscribe/not-a-real-token").exchange())) {
			assertThat(result).hasStatus(HttpStatus.NOT_FOUND)
				.bodyJson()
				.extractingPath("$.message")
				.isEqualTo("This unsubscribe link is not valid");
		}
	}

	private long customer(String name, String email) {
		return idOf(postAs(this.token, "/api/customers", Map.of("name", name, "email", email)));
	}

	private long due(long customerId) {
		long policy = ((Number) JsonPath.read(bodyOf(getAs(this.token, "/api/policies")), "$[0].id")).longValue();
		return idOf(postAs(this.token, "/api/dues",
				Map.of("customerId", customerId, "amountPaise", 150000, "dueDate", "2026-01-20", "policyId", policy)));
	}

	private String unsubscribeToken(long customerId) {
		return this.jdbc.queryForObject("SELECT unsub_token FROM customers WHERE id = ?", String.class, customerId);
	}

	private OffsetDateTime optedOutAt() {
		return this.jdbc.queryForObject("SELECT opted_out_at FROM customers WHERE id = ?", OffsetDateTime.class,
				this.aarav);
	}

	private List<String> stepStatuses(long due) {
		return this.jdbc.queryForList("SELECT status || ' ' || coalesce(error, 'null') FROM due_steps WHERE due_id = ?",
				String.class, due);
	}

	private static long idOf(MvcTestResult result) {
		return ((Number) JsonPath.read(bodyOf(result), "$.id")).longValue();
	}

}
