package com.paynudge.business;

import java.util.HashMap;
import java.util.Map;

import com.paynudge.IntegrationTest;
import com.paynudge.security.TokenService;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessApiTest extends IntegrationTest {

	@Autowired
	private TokenService tokens;

	@Test
	void anOwnerReadsTheirBusinessWithItsDefaults() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");

		MvcTestResult result = getAs(token, "/api/business");

		assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"name":"Bright Tuition","timezone":"Asia/Kolkata","quietStart":"21:00:00","quietEnd":"09:00:00",
				 "senderName":null,"dailySendCap":500}""");
	}

	@Test
	void anOwnerUpdatesEverySetting() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");

		MvcTestResult result = putAs(token, "/api/business", settings(Map.of("name", "  Bright Tuition Centre ",
				"timezone", "Asia/Dubai", "quietStart", "22:30", "quietEnd", "07:00", "senderName", " Asha Ma'am ",
				"dailySendCap", 120)));

		String expected = """
				{"name":"Bright Tuition Centre","timezone":"Asia/Dubai","quietStart":"22:30:00","quietEnd":"07:00:00",
				 "senderName":"Asha Ma'am","dailySendCap":120}""";
		assertThat(result).hasStatusOk().bodyJson().isLenientlyEqualTo(expected);
		assertThat(getAs(token, "/api/business")).bodyJson().isLenientlyEqualTo(expected);
		// The engine reads quiet hours with plain SQL: they must be stored as the owner's wall-clock times, not
		// shifted by the JVM's own time zone (tests run on India time to catch that).
		assertThat(this.jdbc.queryForObject("SELECT quiet_start || '-' || quiet_end FROM businesses", String.class))
			.isEqualTo("22:30:00-07:00:00");
		assertThat(getAs(token, "/api/me")).bodyJson()
			.extractingPath("$.business.name")
			.isEqualTo("Bright Tuition Centre");
	}

	@Test
	void quietHoursMayCrossMidnightOrNotButMustNotBeEmpty() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");

		assertThat(putAs(token, "/api/business", settings(Map.of("quietStart", "22:00", "quietEnd", "06:00"))))
			.hasStatusOk();
		assertThat(putAs(token, "/api/business", settings(Map.of("quietStart", "13:00", "quietEnd", "15:00"))))
			.hasStatusOk();

		MvcTestResult empty = putAs(token, "/api/business", settings(Map.of("quietStart", "09:00", "quietEnd", "09:00")));
		assertThat(empty).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.details.quietEnd")
			.isEqualTo("must be different from the start of quiet hours");
	}

	@Test
	void invalidSettingsNameEveryBadField() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");
		Map<String, Object> bad = new HashMap<>();
		bad.put("name", " ");
		bad.put("timezone", "");
		bad.put("quietStart", null);
		bad.put("senderName", "x".repeat(101));
		bad.put("dailySendCap", 0);

		MvcTestResult result = putAs(token, "/api/business", settings(bad));

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
		assertThat(result).bodyJson()
			.extractingPath("$.details")
			.asMap()
			.containsOnlyKeys("name", "timezone", "quietStart", "senderName", "dailySendCap");
	}

	@Test
	void theTimezoneMustBeARealRegion() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");

		for (String timezone : new String[] { "Mars/Olympus", "+05:30", "IST" }) {
			assertThat(putAs(token, "/api/business", settings(Map.of("timezone", timezone))))
				.hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.extractingPath("$.details.timezone")
				.isEqualTo("must be a timezone like Asia/Kolkata");
		}
	}

	@Test
	void theDailyCapIsAtMostFiveHundred() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");

		assertThat(putAs(token, "/api/business", settings(Map.of("dailySendCap", 500)))).hasStatusOk();
		assertThat(putAs(token, "/api/business", settings(Map.of("dailySendCap", 501))))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.details")
			.asMap()
			.containsOnlyKeys("dailySendCap");
	}

	@Test
	void aBlankSenderNameIsCleared() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");
		putAs(token, "/api/business", settings(Map.of("senderName", "Asha")));

		MvcTestResult result = putAs(token, "/api/business", settings(Map.of("senderName", "   ")));

		assertThat(result).hasStatusOk().bodyJson().extractingPath("$.senderName").isNull();
	}

	@Test
	void settingsNeedASignedInOwner() {
		assertThat(this.mvc.get().uri("/api/business").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(this.mvc.put().uri("/api/business").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void anOwnerCanNeverReadOrChangeAnotherBusiness() {
		signUpOwner("asha@example.com", "Bright Tuition");
		String ravi = signUpOwner("ravi@example.com", "Iron Gym");

		// A validly signed token pairing Asha with Ravi's business still reaches nothing: lookups check the owner.
		String crossed = this.tokens.issue(idOf("users", "asha@example.com"), businessIdOf("ravi@example.com"))
			.value();
		assertThat(getAs(crossed, "/api/business")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(putAs(crossed, "/api/business", settings(Map.of("name", "Hijacked")))).hasStatus(HttpStatus.NOT_FOUND);

		assertThat(getAs(ravi, "/api/business")).bodyJson().extractingPath("$.name").isEqualTo("Iron Gym");
	}

	/** A full, valid settings body with the given fields replaced. */
	private static Map<String, Object> settings(Map<String, Object> overrides) {
		Map<String, Object> body = new HashMap<>(Map.of("name", "Bright Tuition", "timezone", "Asia/Kolkata",
				"quietStart", "21:00", "quietEnd", "09:00", "dailySendCap", 500));
		body.putAll(overrides);
		return body;
	}

	private long idOf(String table, String email) {
		return this.jdbc.queryForObject("SELECT id FROM " + table + " WHERE email = ?", Long.class, email);
	}

	private long businessIdOf(String ownerEmail) {
		return this.jdbc.queryForObject(
				"SELECT b.id FROM businesses b JOIN users u ON u.id = b.owner_id WHERE u.email = ?", Long.class,
				ownerEmail);
	}

}
