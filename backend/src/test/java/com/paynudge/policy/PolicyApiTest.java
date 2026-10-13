package com.paynudge.policy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import com.paynudge.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyApiTest extends IntegrationTest {

	private static final String BODY = "Hi {name}, {amount} is due on {due_date}. Pay here: {pay_link}";

	@Test
	void everyBusinessStartsWithTheThreeStarterPolicies() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");

		MvcTestResult result = getAs(token, "/api/policies");

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$[*].name")
			.isEqualTo(List.of("Friendly then firm", "Gentle", "Firm"));
		assertThat(result).bodyJson().extractingPath("$[*].starter").isEqualTo(List.of(true, true, true));
		assertThat(result).bodyJson().extractingPath("$[0].steps[*].offsetDays").isEqualTo(List.of(-2, 0, 3, 7, 10));
		assertThat(result).bodyJson().extractingPath("$[0].steps[3].action").isEqualTo("NOTIFY_OWNER");

		// Listing again does not create them twice.
		getAs(token, "/api/policies");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM policies", Long.class)).isEqualTo(3);
		assertThat(this.jdbc.queryForObject("SELECT definition ->> 'schemaVersion' FROM policies LIMIT 1", String.class))
			.isEqualTo("1");
	}

	@Test
	void anOwnerCreatesEditsAndDeletesTheirOwnPolicy() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");

		MvcTestResult created = postAs(token, "/api/policies", policy(" Two nudges ", List.of(email("first", -1),
				email("second", 2))));
		assertThat(created).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.name").isEqualTo("Two nudges");
		long id = idOf(created);
		assertThat(getAs(token, "/api/policies")).bodyJson().extractingPath("$[3].name").isEqualTo("Two nudges");

		MvcTestResult edited = putAs(token, "/api/policies/" + id, policy("Three nudges",
				List.of(email("first", -1), email("second", 2), email("third", 5))));
		assertThat(edited).hasStatusOk().bodyJson().extractingPath("$.steps[*].key")
			.isEqualTo(List.of("first", "second", "third"));

		assertThat(deleteAs(token, "/api/policies/" + id)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(getAs(token, "/api/policies")).bodyJson().extractingPath("$[*].name")
			.isEqualTo(List.of("Friendly then firm", "Gentle", "Firm"));
	}

	@Test
	void badPoliciesNameEveryProblem() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");
		Map<String, Object> vague = email("vague", 0);
		vague.put("body", "Please pay.");

		MvcTestResult result = postAs(token, "/api/policies", policy("", List.of(vague, email("rush", 0))));

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().isStrictlyEqualTo("""
				{"code":"VALIDATION_FAILED","message":"Request validation failed","details":{
				  "name":"Enter a name",
				  "steps[0].body":"The message must include {amount}, {name}, {pay_link}",
				  "steps[1].offsetDays":"Leave at least a day between emails to the customer"}}""");
	}

	@Test
	void namesAreUniqueWithinABusiness() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");

		MvcTestResult clash = postAs(token, "/api/policies", policy("Gentle", List.of(email("only", 0))));

		assertThat(clash).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code")
			.isEqualTo("POLICY_NAME_TAKEN");
	}

	@Test
	void starterPoliciesCannotBeChangedOrDeleted() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");
		long starter = ((Number) JsonPath.read(bodyOf(getAs(token, "/api/policies")), "$[0].id")).longValue();

		assertThat(putAs(token, "/api/policies/" + starter, policy("Mine now", List.of(email("only", 0)))))
			.hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("STARTER_READ_ONLY");
		assertThat(deleteAs(token, "/api/policies/" + starter)).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void policiesNeedASignedInOwner() {
		assertThat(this.mvc.get().uri("/api/policies").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void anOwnerCanNeverSeeOrTouchAnotherOwnersPolicies() {
		String asha = signUpOwner("asha@example.com", "Bright Tuition");
		String ravi = signUpOwner("ravi@example.com", "Iron Gym");
		long ashas = idOf(postAs(asha, "/api/policies", policy("Asha's way", List.of(email("only", 0)))));

		assertThat(getAs(ravi, "/api/policies")).bodyJson().extractingPath("$[*].name")
			.isEqualTo(List.of("Friendly then firm", "Gentle", "Firm"));
		assertThat(putAs(ravi, "/api/policies/" + ashas, policy("Hijacked", List.of(email("only", 0)))))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(deleteAs(ravi, "/api/policies/" + ashas)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(getAs(asha, "/api/policies")).bodyJson().extractingPath("$[3].name").isEqualTo("Asha's way");
	}

	private static Map<String, Object> policy(String name, List<Map<String, Object>> steps) {
		return Map.of("name", name, "steps", steps);
	}

	private static Map<String, Object> email(String key, int offsetDays) {
		Map<String, Object> step = new HashMap<>();
		step.put("key", key);
		step.put("offsetDays", offsetDays);
		step.put("action", "SEND_EMAIL");
		step.put("tone", "neutral");
		step.put("subject", "About {amount}");
		step.put("body", BODY);
		return step;
	}

	private static long idOf(MvcTestResult result) {
		return ((Number) JsonPath.read(bodyOf(result), "$.id")).longValue();
	}

}
