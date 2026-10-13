package com.paynudge.auth;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import com.jayway.jsonpath.JsonPath;
import com.paynudge.IntegrationTest;
import com.paynudge.security.TokenService;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

class AuthApiTest extends IntegrationTest {

	private static final String PASSWORD = "correct-horse-battery";

	@Autowired
	private TokenService tokens;

	@Test
	void signupCreatesAnOwnerAndBusinessAndSignsThemIn() {
		MvcTestResult result = signup("asha@example.com", PASSWORD, "Bright Tuition");

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).bodyJson().extractingPath("$.expiresAt").isEqualTo("2026-01-05T06:30:00Z");
		assertThat(result).bodyJson().extractingPath("$.me.email").isEqualTo("asha@example.com");
		assertThat(result).bodyJson().extractingPath("$.me.business.name").isEqualTo("Bright Tuition");
		assertThat(result).bodyJson().extractingPath("$.me.business.timezone").isEqualTo("Asia/Kolkata");
		assertThat(result).bodyJson().extractingPath("$.me.business.quietStart").isEqualTo("21:00:00");
		assertThat(result).bodyJson().extractingPath("$.me.business.quietEnd").isEqualTo("09:00:00");

		MvcTestResult me = me(tokenOf(result));
		assertThat(me).hasStatusOk();
		assertThat(me).bodyJson().extractingPath("$.email").isEqualTo("asha@example.com");
		assertThat(me).bodyJson().extractingPath("$.business.name").isEqualTo("Bright Tuition");
	}

	@Test
	void passwordsAreStoredOnlyAsBcryptHashes() {
		signup("asha@example.com", PASSWORD, "Bright Tuition");

		String hash = this.jdbc.queryForObject("SELECT password_hash FROM users", String.class);
		assertThat(hash).startsWith("$2").doesNotContain(PASSWORD);
	}

	@Test
	void emailsIgnoreCaseAndSurroundingSpaces() {
		MvcTestResult result = signup("  Asha@Example.COM ", PASSWORD, "Bright Tuition");
		assertThat(result).bodyJson().extractingPath("$.me.email").isEqualTo("asha@example.com");

		assertThat(login("ASHA@example.com", PASSWORD)).hasStatusOk();
	}

	@Test
	void anEmailCanOnlyBeRegisteredOnce() {
		signup("asha@example.com", PASSWORD, "Bright Tuition");

		MvcTestResult again = signup("ASHA@example.com", "another-password", "Other Gym");

		assertThat(again).hasStatus(HttpStatus.CONFLICT);
		assertThat(again).bodyJson().isStrictlyEqualTo("""
				{"code":"EMAIL_TAKEN","message":"An account with this email already exists","details":{}}""");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM users", Long.class)).isEqualTo(1);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM businesses", Long.class)).isEqualTo(1);
	}

	@Test
	void invalidSignupsNameEveryBadField() {
		MvcTestResult result = signup("not-an-email", "short", " ");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("VALIDATION_FAILED");
		assertThat(result).bodyJson().extractingPath("$.details").asMap()
			.containsOnlyKeys("email", "password", "businessName");
	}

	@Test
	void passwordsLongerThanBcryptCanHashAreRefused() {
		String eightyBytes = "é".repeat(40);

		MvcTestResult result = signup("asha@example.com", eightyBytes, "Bright Tuition");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.details.password").isEqualTo("must be at most 72 bytes");
	}

	@Test
	void aWrongPasswordAndAnUnknownEmailFailIdentically() {
		signup("asha@example.com", PASSWORD, "Bright Tuition");

		MvcTestResult wrongPassword = login("asha@example.com", "not-the-password");
		MvcTestResult unknownEmail = login("nobody@example.com", PASSWORD);

		String expected = """
				{"code":"INVALID_CREDENTIALS","message":"Email or password is incorrect","details":{}}""";
		assertThat(wrongPassword).hasStatus(HttpStatus.UNAUTHORIZED).bodyJson().isStrictlyEqualTo(expected);
		assertThat(unknownEmail).hasStatus(HttpStatus.UNAUTHORIZED).bodyJson().isStrictlyEqualTo(expected);
	}

	@Test
	void protectedRoutesNeedAToken() {
		MvcTestResult result = this.mvc.get().uri("/api/me").exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(result).bodyJson().isStrictlyEqualTo("""
				{"code":"UNAUTHORIZED","message":"Authentication required","details":{}}""");
	}

	@Test
	void garbageAndForeignlySignedTokensAreRejected() {
		long userId = createOwner("asha@example.com");

		assertThat(me("not.a.jwt")).hasStatus(HttpStatus.UNAUTHORIZED)
			.bodyJson()
			.extractingPath("$.message")
			.isEqualTo("Invalid or expired token");
		assertThat(me(signedWithAnotherKey(userId))).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void tokensExpireAfterTheirTtl() {
		String token = tokenOf(signup("asha@example.com", PASSWORD, "Bright Tuition"));

		this.clock.advance(Duration.ofHours(2).minusMinutes(1));
		assertThat(me(token)).hasStatusOk();

		// past expiry plus the validator's 60 second clock-skew allowance
		this.clock.advance(Duration.ofMinutes(3));
		assertThat(me(token)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void anOwnerOnlyEverSeesTheirOwnAccount() {
		String asha = tokenOf(signup("asha@example.com", PASSWORD, "Bright Tuition"));
		String ravi = tokenOf(signup("ravi@example.com", PASSWORD, "Iron Gym"));

		assertThat(me(asha)).bodyJson().extractingPath("$.business.name").isEqualTo("Bright Tuition");
		assertThat(me(ravi)).bodyJson().extractingPath("$.business.name").isEqualTo("Iron Gym");

		// Even a validly signed token pairing Asha with Ravi's business gets nothing: lookups are owner-scoped.
		long ashaId = userId("asha@example.com");
		long raviBusinessId = businessIdOf("ravi@example.com");
		String crossed = this.tokens.issue(ashaId, raviBusinessId).value();
		assertThat(me(crossed)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void malformedBodiesAndUnknownRoutesUseTheSameErrorShape() {
		MvcTestResult malformed = this.mvc.post()
			.uri("/api/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{not json")
			.exchange();
		assertThat(malformed).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().isStrictlyEqualTo("""
				{"code":"MALFORMED_REQUEST","message":"Request body is missing or is not valid JSON","details":{}}""");

		String token = tokenOf(signup("asha@example.com", PASSWORD, "Bright Tuition"));
		MvcTestResult unknown = this.mvc.get()
			.uri("/api/does-not-exist")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.exchange();
		assertThat(unknown).hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
	}

	@Test
	void corsAllowsOnlyTheConfiguredFrontendOrigin() {
		MvcTestResult allowed = preflight("http://localhost:3000");
		assertThat(allowed).hasStatusOk()
			.hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000");

		assertThat(preflight("https://evil.example")).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void healthIsPublic() {
		assertThat(this.mvc.get().uri("/actuator/health").exchange()).hasStatusOk()
			.bodyJson()
			.extractingPath("$.status")
			.isEqualTo("UP");
	}

	private MvcTestResult signup(String email, String password, String businessName) {
		return post("/api/auth/signup", Map.of("email", email, "password", password, "businessName", businessName));
	}

	private MvcTestResult login(String email, String password) {
		return post("/api/auth/login", Map.of("email", email, "password", password));
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

	private MvcTestResult preflight(String origin) {
		return this.mvc.options()
			.uri("/api/auth/login")
			.header(HttpHeaders.ORIGIN, origin)
			.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
			.exchange();
	}

	private long createOwner(String email) {
		signup(email, PASSWORD, "Bright Tuition");
		return userId(email);
	}

	private long userId(String email) {
		return this.jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
	}

	private long businessIdOf(String email) {
		return this.jdbc.queryForObject(
				"SELECT b.id FROM businesses b JOIN users u ON u.id = b.owner_id WHERE u.email = ?", Long.class, email);
	}

	private static String tokenOf(MvcTestResult result) {
		return JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
				"$.token");
	}

	private String signedWithAnotherKey(long userId) {
		byte[] otherKey = new byte[32];
		new SecureRandom().nextBytes(otherKey);
		NimbusJwtEncoder encoder = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(otherKey, "HmacSHA256"))
			.algorithm(MacAlgorithm.HS256)
			.build();
		Instant now = this.clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer("paynudge-api")
			.subject(Long.toString(userId))
			.issuedAt(now)
			.expiresAt(now.plus(Duration.ofHours(1)))
			.claim("bid", 1L)
			.build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
			.getTokenValue();
	}

}
