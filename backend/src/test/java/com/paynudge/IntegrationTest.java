package com.paynudge;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Base class for tests that need the full application against a real Postgres. Every subclass shares one Spring
 * context (and so one container); each test starts with empty tables and the clock at
 * {@link MutableClock#DEFAULT_START}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestcontainersConfiguration.class, TestClockConfiguration.class, TestGoogleConfiguration.class })
public abstract class IntegrationTest {

	@Autowired
	protected MockMvcTester mvc;

	@Autowired
	protected MutableClock clock;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected RecordingEmailSender emails;

	private List<String> tables;

	@BeforeEach
	void resetState() {
		this.clock.reset();
		this.emails.reset();
		if (this.tables == null) {
			this.tables = this.jdbc.queryForList(
					"SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'",
					String.class);
		}
		this.jdbc.execute("TRUNCATE TABLE " + String.join(", ", this.tables) + " RESTART IDENTITY CASCADE");
	}

}
