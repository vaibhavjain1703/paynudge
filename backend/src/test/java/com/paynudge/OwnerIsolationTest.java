package com.paynudge;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One owner can never read or change another's data, through any endpoint. Every endpoint the app serves must either
 * have a check here (run as Ravi against Asha's data) or a stated reason it holds no business's data; a new endpoint
 * with neither fails {@link #everyEndpointIsCheckedOrExplained}. The per-feature API tests check the same in more
 * depth; this is the net that catches a forgotten one.
 */
class OwnerIsolationTest extends IntegrationTest {

	/** Endpoints with no business's data to keep apart, and why. */
	private static final Map<String, String> NOT_OWNER_SCOPED = Map.of(
			"POST /api/auth/signup", "creates a new owner and business; reads nothing that exists",
			"POST /api/auth/login", "sign-in: returns only the caller's own session",
			"POST /api/auth/email/start", "sign-in: emails a code to the address given",
			"POST /api/auth/email/verify", "sign-in: returns only the caller's own session",
			"POST /api/auth/google", "sign-in: returns only the caller's own session",
			"GET /api/auth/providers", "which sign-in methods are on",
			"GET /api/public/unsubscribe/{token}", "the customer's own secret link, not an owner (UnsubscribeApiTest)",
			"POST /api/public/unsubscribe/{token}", "the customer's own secret link, not an owner (UnsubscribeApiTest)",
			"POST /webhooks/razorpay", "Razorpay's signed events; a payment finds its due by its link",
			"* /error", "renders errors");

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping mappings;

	private String asha;

	private String ravi;

	private long ashasCustomer;

	private long ashasPolicy;

	private long ashasDue;

	@BeforeEach
	void twoOwners() {
		this.asha = signUpOwner("asha@example.com", "Bright Tuition");
		this.ravi = signUpOwner("ravi@example.com", "Ravi Gym");
		this.ashasCustomer = idOf(
				postAs(this.asha, "/api/customers", Map.of("name", "Aarav", "email", "aarav@example.com")));
		postAs(this.asha, "/api/customers", Map.of("name", "Kabir", "email", "kabir@example.com"));
		this.ashasPolicy = idOf(postAs(this.asha, "/api/policies", policy("Asha's way")));
		this.ashasDue = idOf(postAs(this.asha, "/api/dues", Map.of("customerId", this.ashasCustomer, "amountPaise",
				150000, "dueDate", "2026-01-20", "policyId", this.ashasPolicy)));
	}

	@Test
	void everyEndpointIsCheckedOrExplained() {
		Set<String> served = new TreeSet<>();
		this.mappings.getHandlerMethods().keySet().forEach((info) -> info.getPatternValues().forEach((pattern) -> {
			if (info.getMethodsCondition().getMethods().isEmpty()) {
				served.add("* " + pattern);
			}
			info.getMethodsCondition().getMethods().forEach((method) -> served.add(method + " " + pattern));
		}));
		Set<String> accounted = new TreeSet<>(isolationChecks().keySet());
		accounted.addAll(NOT_OWNER_SCOPED.keySet());

		Set<String> unaccounted = new TreeSet<>(served);
		unaccounted.removeAll(accounted);
		Set<String> gone = new TreeSet<>(accounted);
		gone.removeAll(served);
		assertThat(unaccounted).as("endpoints with no isolation check: add one to OwnerIsolationTest").isEmpty();
		assertThat(gone).as("checks for endpoints that no longer exist").isEmpty();
	}

	@Test
	void anotherOwnerCanNeverSeeOrChangeABusinesssData() {
		isolationChecks().forEach((endpoint, check) -> {
			try {
				check.run();
			}
			catch (AssertionError failure) {
				throw new AssertionError(endpoint + ": " + failure.getMessage(), failure);
			}
		});

		// After all of that, Asha's business is exactly as she left it.
		assertThat(getAs(this.asha, "/api/business")).bodyJson().extractingPath("$.name").isEqualTo("Bright Tuition");
		assertThat(getAs(this.asha, "/api/customers")).bodyJson()
			.extractingPath("$.items[*].name")
			.isEqualTo(List.of("Aarav", "Kabir"));
		assertThat(getAs(this.asha, "/api/policies")).bodyJson().extractingPath("$[3].name").isEqualTo("Asha's way");
		assertThat(getAs(this.asha, "/api/dues/" + this.ashasDue)).bodyJson()
			.extractingPath("$.status")
			.isEqualTo("SCHEDULED");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM due_steps WHERE status = 'PENDING'", Long.class))
			.isEqualTo(1);
	}

	/** One check per endpoint that holds a business's data, run as Ravi against Asha's. Reads come before writes. */
	private Map<String, Runnable> isolationChecks() {
		Map<String, Runnable> checks = new LinkedHashMap<>();
		checks.put("GET /api/me", () -> assertThat(getAs(this.ravi, "/api/me")).bodyJson()
			.extractingPath("$.business.name")
			.isEqualTo("Ravi Gym"));
		checks.put("GET /api/business", () -> assertThat(getAs(this.ravi, "/api/business")).bodyJson()
			.extractingPath("$.name")
			.isEqualTo("Ravi Gym"));
		checks.put("GET /api/customers", () -> {
			assertThat(getAs(this.ravi, "/api/customers")).bodyJson().extractingPath("$.totalItems").isEqualTo(0);
			assertThat(getAs(this.ravi, "/api/customers?q=Aarav")).bodyJson()
				.extractingPath("$.totalItems")
				.isEqualTo(0);
		});
		checks.put("GET /api/dues", () -> assertThat(getAs(this.ravi, "/api/dues")).bodyJson()
			.extractingPath("$.totalItems")
			.isEqualTo(0));
		checks.put("GET /api/dues/{id}", () -> notFound(getAs(this.ravi, "/api/dues/" + this.ashasDue)));
		checks.put("GET /api/policies", () -> assertThat(getAs(this.ravi, "/api/policies")).bodyJson()
			.extractingPath("$[*].name")
			.isEqualTo(List.of("Friendly then firm", "Gentle", "Firm")));
		checks.put("GET /api/dashboard", () -> {
			MvcTestResult dashboard = getAs(this.ravi, "/api/dashboard");
			assertThat(dashboard).bodyJson().extractingPath("$.outstanding.dues").isEqualTo(0);
			assertThat(dashboard).bodyJson().extractingPath("$.upcoming").asArray().isEmpty();
		});
		checks.put("POST /api/dues/bulk", () -> assertThat(postAs(this.ravi, "/api/dues/bulk",
				Map.of("ids", List.of(this.ashasDue), "action", "cancel")))
			.hasStatusOk()
			.bodyJson()
			.isStrictlyEqualTo("""
					{"updated":0,"skipped":1}"""));
		checks.put("POST /api/import/preview", () -> {
			// Asha has a customer with this email; for Ravi it is someone new.
			MvcTestResult preview = upload("/api/import/preview",
					"Name,Email,Amount,Due Date\nKabir,kabir@example.com,1500,05/11/2026\n", null);
			assertThat(preview).hasStatusOk().bodyJson().extractingPath("$.rows[0].existingCustomer").isEqualTo(false);
		});
		checks.put("POST /api/import/commit", () -> assertThat(upload("/api/import/commit",
				"Name,Email,Amount,Due Date\nKabir,kabir@example.com,1500,05/11/2026\n", this.ashasPolicy))
			.hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.details.policyId")
			.isNotNull());
		checks.put("POST /api/dues", () -> {
			MvcTestResult withHerCustomer = postAs(this.ravi, "/api/dues", Map.of("customerId", this.ashasCustomer,
					"amountPaise", 1000, "dueDate", "2026-02-01", "policyId", starterId(this.ravi)));
			assertThat(withHerCustomer).hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.extractingPath("$.details.customerId")
				.isNotNull();
			long his = idOf(postAs(this.ravi, "/api/customers", Map.of("name", "Meera", "email", "meera@example.com")));
			MvcTestResult withHerPolicy = postAs(this.ravi, "/api/dues", Map.of("customerId", his, "amountPaise", 1000,
					"dueDate", "2026-02-01", "policyId", this.ashasPolicy));
			assertThat(withHerPolicy).hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.extractingPath("$.details.policyId")
				.isNotNull();
		});
		checks.put("POST /api/dues/{id}/mark-paid",
				() -> notFound(postAs(this.ravi, "/api/dues/" + this.ashasDue + "/mark-paid", Map.of())));
		checks.put("POST /api/dues/{id}/cancel",
				() -> notFound(postAs(this.ravi, "/api/dues/" + this.ashasDue + "/cancel", Map.of())));
		checks.put("PUT /api/customers/{id}", () -> notFound(putAs(this.ravi, "/api/customers/" + this.ashasCustomer,
				Map.of("name", "Hijacked", "email", "aarav@example.com"))));
		checks.put("DELETE /api/customers/{id}",
				() -> notFound(deleteAs(this.ravi, "/api/customers/" + this.ashasCustomer)));
		checks.put("POST /api/customers", () -> assertThat(postAs(this.ravi, "/api/customers",
				Map.of("name", "Aarav", "email", "aarav@example.com")))
			.as("the same email is a separate customer in another business")
			.hasStatus(HttpStatus.CREATED));
		checks.put("PUT /api/policies/{id}",
				() -> notFound(putAs(this.ravi, "/api/policies/" + this.ashasPolicy, policy("Hijacked"))));
		checks.put("DELETE /api/policies/{id}",
				() -> notFound(deleteAs(this.ravi, "/api/policies/" + this.ashasPolicy)));
		checks.put("POST /api/policies", () -> assertThat(postAs(this.ravi, "/api/policies", policy("Asha's way")))
			.as("policy names are per business")
			.hasStatus(HttpStatus.CREATED));
		checks.put("PUT /api/business", () -> assertThat(putAs(this.ravi, "/api/business",
				Map.of("name", "Ravi's Gym", "timezone", "Asia/Kolkata", "quietStart", "21:00", "quietEnd", "09:00",
						"dailySendCap", 100)))
			.hasStatusOk());
		return checks;
	}

	private static void notFound(MvcTestResult result) {
		assertThat(result).hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
	}

	private MvcTestResult upload(String uri, String csv, Long policyId) {
		var request = this.mvc.post()
			.uri(uri)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + this.ravi)
			.multipart()
			.file(new MockMultipartFile("file", "dues.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));
		if (policyId != null) {
			MockPart mapping = new MockPart("mapping", this.json.writeValueAsBytes(Map.of("name", "Name", "email",
					"Email", "amount", "Amount", "dueDate", "Due Date")));
			mapping.getHeaders().setContentType(MediaType.APPLICATION_JSON);
			request = request.part(mapping).param("policyId", policyId.toString());
		}
		return request.exchange();
	}

	private long starterId(String token) {
		return ((Number) JsonPath.read(bodyOf(getAs(token, "/api/policies")), "$[0].id")).longValue();
	}

	private static Map<String, Object> policy(String name) {
		Map<String, Object> step = new HashMap<>();
		step.put("key", "only");
		step.put("offsetDays", 0);
		step.put("action", "SEND_EMAIL");
		step.put("tone", "neutral");
		step.put("subject", "About {amount}");
		step.put("body", "Hi {name}, {amount} is due. Pay here: {pay_link}");
		return Map.of("name", name, "steps", List.of(step));
	}

	private static long idOf(MvcTestResult result) {
		return ((Number) JsonPath.read(bodyOf(result), "$.id")).longValue();
	}

}
