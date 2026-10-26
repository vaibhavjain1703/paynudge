package com.paynudge.security;

import java.util.Map;

import com.paynudge.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/** The limits in front of public routes, switched on (the other tests run without them). */
@TestPropertySource(properties = "paynudge.rate-limits.enabled=true")
class RateLimitsApiTest extends IntegrationTest {

	@Test
	void theDemoCanBeStartedFiveTimesAnHourFromOneAddress() {
		for (int i = 0; i < 5; i++) {
			assertThat(demoFrom("203.0.113.7")).hasStatus(HttpStatus.CREATED);
		}

		MvcTestResult refused = demoFrom("203.0.113.7");

		assertThat(refused).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(refused).bodyJson().extractingPath("$.code").isEqualTo("RATE_LIMITED");
		assertThat(refused).bodyJson().extractingPath("$.message").asString().startsWith("Too many requests. Try again in ");
		long retryAfter = Long.parseLong(refused.getResponse().getHeader(HttpHeaders.RETRY_AFTER));
		assertThat(retryAfter).isBetween(60L, 12 * 60L);
		assertThat(refused).bodyJson().extractingPath("$.details.retryAfterSeconds").isEqualTo((int) retryAfter);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM businesses WHERE demo", Long.class)).isEqualTo(5);

		assertThat(demoFrom("198.51.100.20")).as("another address has its own allowance").hasStatus(HttpStatus.CREATED);
	}

	@Test
	void tenSignInsAMinuteThenAPause() {
		signUpOwner("asha@example.com", "Bright Tuition");
		for (int i = 0; i < 10; i++) {
			assertThat(loginFrom("203.0.113.8", "wrong-password")).hasStatus(HttpStatus.UNAUTHORIZED);
		}

		assertThat(loginFrom("203.0.113.8", OWNER_PASSWORD)).as("the right password is refused too while limited")
			.hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(loginFrom("203.0.113.9", OWNER_PASSWORD)).hasStatusOk();
	}

	@Test
	void corsPreflightsAreNotCounted() {
		for (int i = 0; i < 10; i++) {
			assertThat(this.mvc.options()
				.uri("/api/auth/demo")
				.header(HttpHeaders.ORIGIN, "http://localhost:3000")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
				.with((request) -> {
					request.setRemoteAddr("203.0.113.10");
					return request;
				})
				.exchange()).hasStatusOk();
		}
		assertThat(demoFrom("203.0.113.10")).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void anOversizedJsonBodyIsRefusedBeforeItIsRead() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");

		MvcTestResult result = postAs(token, "/api/customers",
				Map.of("name", "A".repeat(300 * 1024), "email", "aarav@example.com"));

		assertThat(result).hasStatus(HttpStatus.CONTENT_TOO_LARGE);
		assertThat(result).bodyJson().isStrictlyEqualTo("""
				{"code":"BODY_TOO_LARGE","message":"Request body is larger than 256 KB","details":{}}""");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM customers", Long.class)).isZero();
	}

	private MvcTestResult demoFrom(String address) {
		return this.mvc.post().uri("/api/auth/demo").with((request) -> {
			request.setRemoteAddr(address);
			return request;
		}).exchange();
	}

	private MvcTestResult loginFrom(String address, String password) {
		return this.mvc.post()
			.uri("/api/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content(this.json.writeValueAsString(Map.of("email", "asha@example.com", "password", password)))
			.with((request) -> {
				request.setRemoteAddr(address);
				return request;
			})
			.exchange();
	}

}
