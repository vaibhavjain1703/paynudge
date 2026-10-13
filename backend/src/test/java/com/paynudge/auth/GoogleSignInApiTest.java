package com.paynudge.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.paynudge.IntegrationTest;
import com.paynudge.TestGoogleConfiguration.GoogleIdTokens;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleSignInApiTest extends IntegrationTest {

	@Autowired
	private GoogleIdTokens google;

	@Test
	void aNewGoogleUserNamesTheirBusinessAndIsSignedIn() {
		String idToken = this.google.forEmail("Asha@Gmail.com");

		assertThat(signIn(idToken, null)).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{"status":"NEEDS_BUSINESS_NAME","email":"asha@gmail.com"}""");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM users", Long.class)).isZero();

		MvcTestResult signedIn = signIn(idToken, "Bright Tuition");
		assertThat(signedIn).hasStatusOk();
		assertThat(signedIn).bodyJson().extractingPath("$.status").isEqualTo("SIGNED_IN");
		assertThat(signedIn).bodyJson().extractingPath("$.me.email").isEqualTo("asha@gmail.com");
		assertThat(signedIn).bodyJson().extractingPath("$.me.business.name").isEqualTo("Bright Tuition");
		assertThat(this.jdbc.queryForObject("SELECT password_hash FROM users", String.class)).isNull();
	}

	@Test
	void aGoogleAccountWithAKnownEmailSignsIntoThatAccount() {
		post("/api/auth/signup",
				Map.of("email", "asha@gmail.com", "password", "correct-horse-battery", "businessName", "Bright"));
		long userId = this.jdbc.queryForObject("SELECT id FROM users", Long.class);

		MvcTestResult signedIn = signIn(this.google.forEmail("ASHA@gmail.com"), null);

		assertThat(signedIn).hasStatusOk().bodyJson().extractingPath("$.me.userId").isEqualTo((int) userId);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM users", Long.class)).isEqualTo(1);
	}

	@Test
	void tokensMeantForAnotherAppAreRejected() {
		assertRejected(this.google.forEmail("asha@gmail.com",
				(claims) -> claims.audience(List.of("someone-else.apps.googleusercontent.com"))));
	}

	@Test
	void tokensNotIssuedByGoogleAreRejected() {
		assertRejected(this.google.forEmail("asha@gmail.com", (claims) -> claims.issuer("https://evil.example")));
	}

	@Test
	void unverifiedEmailsAreRejected() {
		assertRejected(this.google.forEmail("asha@gmail.com", (claims) -> claims.claim("email_verified", false)));
	}

	@Test
	void expiredTokensAreRejected() {
		String idToken = this.google.forEmail("asha@gmail.com");

		this.clock.advance(Duration.ofHours(1).plusMinutes(2));

		assertRejected(idToken);
	}

	@Test
	void tamperedAndGarbageTokensAreRejected() {
		String idToken = this.google.forEmail("asha@gmail.com");
		String[] parts = idToken.split("\\.");
		String forgedPayload = Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString("{\"email\":\"victim@gmail.com\"}".getBytes(StandardCharsets.UTF_8));

		assertRejected(parts[0] + "." + forgedPayload + "." + parts[2]);
		assertRejected("not-a-token");
	}

	private void assertRejected(String idToken) {
		MvcTestResult result = signIn(idToken, "Bright Tuition");
		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_GOOGLE_TOKEN");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM users", Long.class)).isZero();
	}

	private MvcTestResult signIn(String idToken, String businessName) {
		Map<String, String> body = (businessName == null) ? Map.of("idToken", idToken)
				: Map.of("idToken", idToken, "businessName", businessName);
		return post("/api/auth/google", body);
	}

	private MvcTestResult post(String uri, Map<String, String> body) {
		return this.mvc.post()
			.uri(uri)
			.contentType(MediaType.APPLICATION_JSON)
			.content(this.json.writeValueAsString(body))
			.exchange();
	}

}
