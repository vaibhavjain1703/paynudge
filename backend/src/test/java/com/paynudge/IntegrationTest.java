package com.paynudge;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for tests that need the full application against a real Postgres. Every subclass shares one Spring
 * context (and so one container); each test starts with empty tables and the clock at
 * {@link MutableClock#DEFAULT_START}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class, TestGoogleConfiguration.class,
		TestChannelConfiguration.class })
public abstract class IntegrationTest {

	protected static final String OWNER_PASSWORD = "correct-horse-battery";

	@Autowired
	protected MockMvcTester mvc;

	@Autowired
	protected MutableClock clock;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected RecordingEmailSender emails;

	@Autowired
	protected RecordingChannel channel;

	@Autowired
	protected JsonMapper json;

	private List<String> tables;

	@BeforeEach
	void resetState() {
		this.clock.reset();
		this.emails.reset();
		this.channel.reset();
		if (this.tables == null) {
			this.tables = this.jdbc.queryForList(
					"SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'",
					String.class);
		}
		this.jdbc.execute("TRUNCATE TABLE " + String.join(", ", this.tables) + " RESTART IDENTITY CASCADE");
	}

	/** Signs up a new owner (and their business) and returns their bearer token. */
	protected String signUpOwner(String email, String businessName) {
		MvcTestResult result = this.mvc.post()
			.uri("/api/auth/signup")
			.contentType(MediaType.APPLICATION_JSON)
			.content(this.json.writeValueAsString(
					Map.of("email", email, "password", OWNER_PASSWORD, "businessName", businessName)))
			.exchange();
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return JsonPath.read(bodyOf(result), "$.token");
	}

	protected MvcTestResult getAs(String token, String uri) {
		return this.mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
	}

	protected MvcTestResult postAs(String token, String uri, Object body) {
		return this.mvc.post()
			.uri(uri)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(this.json.writeValueAsString(body))
			.exchange();
	}

	protected MvcTestResult putAs(String token, String uri, Object body) {
		return this.mvc.put()
			.uri(uri)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.contentType(MediaType.APPLICATION_JSON)
			.content(this.json.writeValueAsString(body))
			.exchange();
	}

	protected MvcTestResult deleteAs(String token, String uri) {
		return this.mvc.delete().uri(uri).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).exchange();
	}

	protected static String bodyOf(MvcTestResult result) {
		return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
	}

}
