package com.paynudge.auth;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.JsonPath;
import com.paynudge.IntegrationTest;
import com.paynudge.channel.EmailMessage;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

class EmailCodeApiTest extends IntegrationTest {

	private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

	@Autowired
	private JsonMapper json;

	@Test
	void aNewEmailGetsACodeThenNamesItsBusinessAndIsSignedIn() {
		MvcTestResult sent = start(" Asha@Example.com ");
		assertThat(sent).hasStatus(HttpStatus.ACCEPTED);
		assertThat(sent).bodyJson().isStrictlyEqualTo("""
				{"expiresInSeconds":600,"resendAfterSeconds":60}""");

		EmailMessage email = this.emails.last();
		assertThat(email.to()).isEqualTo("asha@example.com");
		assertThat(email.subject()).isEqualTo("Your PayNudge sign-in code").doesNotContainPattern("\\d{6}");
		String code = codeFrom(email);

		MvcTestResult needsName = verify("asha@example.com", code, null);
		assertThat(needsName).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{"status":"NEEDS_BUSINESS_NAME","email":"asha@example.com"}""");
		assertThat(count("users")).isZero();

		MvcTestResult signedIn = verify("asha@example.com", code, "Bright Tuition");
		assertThat(signedIn).hasStatusOk();
		assertThat(signedIn).bodyJson().extractingPath("$.status").isEqualTo("SIGNED_IN");
		assertThat(signedIn).bodyJson().extractingPath("$.me.business.name").isEqualTo("Bright Tuition");
		assertThat(me(tokenOf(signedIn))).hasStatusOk().bodyJson().extractingPath("$.email").isEqualTo("asha@example.com");
		assertThat(this.jdbc.queryForObject("SELECT password_hash FROM users", String.class)).isNull();

		assertThat(verify("asha@example.com", code, "Bright Tuition")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("INVALID_CODE");
	}

	@Test
	void anExistingPasswordOwnerSignsIntoTheSameAccountWithACode() {
		post("/api/auth/signup",
				Map.of("email", "asha@example.com", "password", "correct-horse-battery", "businessName", "Bright"));
		long userId = this.jdbc.queryForObject("SELECT id FROM users", Long.class);

		start("asha@example.com");
		MvcTestResult signedIn = verify("asha@example.com", codeFrom(this.emails.last()), null);

		assertThat(signedIn).hasStatusOk().bodyJson().extractingPath("$.me.userId").isEqualTo((int) userId);
		assertThat(count("users")).isEqualTo(1);
	}

	@Test
	void codesAreStoredOnlyAsKeyedHashes() {
		start("asha@example.com");
		String code = codeFrom(this.emails.last());

		String stored = this.jdbc.queryForObject("SELECT code_hash FROM login_codes", String.class);
		assertThat(stored).hasSize(64).doesNotContain(code);
	}

	@Test
	void fiveWrongGuessesLockTheCodeEvenForTheRightAnswer() {
		start("asha@example.com");
		String code = codeFrom(this.emails.last());
		String wrong = code.equals("000000") ? "111111" : "000000";

		for (int i = 0; i < EmailCodeService.MAX_WRONG_GUESSES; i++) {
			assertThat(verify("asha@example.com", wrong, "Bright")).hasStatus(HttpStatus.BAD_REQUEST);
		}

		assertThat(verify("asha@example.com", code, "Bright")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(this.jdbc.queryForObject("SELECT attempts FROM login_codes", Integer.class)).isEqualTo(5);
		assertThat(count("users")).isZero();
	}

	@Test
	void codesExpireAfterTenMinutes() {
		start("asha@example.com");
		String code = codeFrom(this.emails.last());

		this.clock.advance(Duration.ofMinutes(10).plusSeconds(1));

		assertThat(verify("asha@example.com", code, "Bright")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void resendingWaitsAMinuteAndOnlyTheNewestCodeWorks() {
		start("asha@example.com");
		String first = codeFrom(this.emails.last());

		MvcTestResult tooSoon = start("asha@example.com");
		assertThat(tooSoon).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(tooSoon).bodyJson().extractingPath("$.code").isEqualTo("CODE_RECENTLY_SENT");
		assertThat(tooSoon).bodyJson().extractingPath("$.details.retryAfterSeconds").isEqualTo(60);
		assertThat(this.emails.sent()).hasSize(1);

		this.clock.advance(Duration.ofSeconds(61));
		assertThat(start("asha@example.com")).hasStatus(HttpStatus.ACCEPTED);
		String second = codeFrom(this.emails.last());

		assumeThat(second).isNotEqualTo(first);
		assertThat(verify("asha@example.com", first, "Bright")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(verify("asha@example.com", second, "Bright")).hasStatusOk();
	}

	@Test
	void atMostFiveCodesAnHourPerEmail() {
		for (int i = 0; i < EmailCodeService.MAX_CODES_PER_HOUR; i++) {
			assertThat(start("asha@example.com")).hasStatus(HttpStatus.ACCEPTED);
			this.clock.advance(Duration.ofSeconds(61));
		}

		assertThat(start("asha@example.com")).hasStatus(HttpStatus.TOO_MANY_REQUESTS)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("TOO_MANY_CODES");

		this.clock.advance(Duration.ofHours(1));
		assertThat(start("asha@example.com")).hasStatus(HttpStatus.ACCEPTED);
	}

	@Test
	void whenTheEmailCannotBeSentNoCodeIsKept() {
		this.emails.failNextSend();

		MvcTestResult result = start("asha@example.com");

		assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("EMAIL_UNAVAILABLE");
		assertThat(count("login_codes")).isZero();
		assertThat(start("asha@example.com")).hasStatus(HttpStatus.ACCEPTED);
	}

	@Test
	void aCodeOnlyWorksForTheEmailItWasSentTo() {
		start("asha@example.com");
		String ashasCode = codeFrom(this.emails.last());

		assertThat(verify("ravi@example.com", ashasCode, "Iron Gym")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(count("users")).isZero();
	}

	@Test
	void passwordlessOwnersCannotSignInWithAPassword() {
		start("asha@example.com");
		verify("asha@example.com", codeFrom(this.emails.last()), "Bright Tuition");

		MvcTestResult login = post("/api/auth/login", Map.of("email", "asha@example.com", "password", "anything-at-all"));

		assertThat(login).hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("INVALID_CREDENTIALS");
	}

	@Test
	void malformedCodesAreRejectedBeforeAnyLookup() {
		MvcTestResult result = verify("asha@example.com", "12ab56", null);

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.details.code").isEqualTo("must be the 6-digit code");
	}

	@Test
	void theWebAppIsToldWhichSignInMethodsExist() {
		assertThat(this.mvc.get().uri("/api/auth/providers").exchange()).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{"emailCode":true,"googleClientId":"test-client.apps.googleusercontent.com"}""");
	}

	private MvcTestResult start(String email) {
		return post("/api/auth/email/start", Map.of("email", email));
	}

	private MvcTestResult verify(String email, String code, String businessName) {
		Map<String, String> body = (businessName == null) ? Map.of("email", email, "code", code)
				: Map.of("email", email, "code", code, "businessName", businessName);
		return post("/api/auth/email/verify", body);
	}

	private MvcTestResult post(String uri, Map<String, String> body) {
		return this.mvc.post()
			.uri(uri)
			.contentType(MediaType.APPLICATION_JSON)
			.content(this.json.writeValueAsString(body))
			.exchange();
	}

	private MvcTestResult me(String token) {
		return this.mvc.get().uri("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
	}

	private long count(String table) {
		return this.jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
	}

	private static String codeFrom(EmailMessage email) {
		Matcher matcher = CODE.matcher(email.text());
		assertThat(matcher.find()).as("a six-digit code in the email").isTrue();
		return matcher.group(1);
	}

	private static String tokenOf(MvcTestResult result) {
		return JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
				"$.token");
	}

}
