package com.paynudge.customer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import com.paynudge.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerApiTest extends IntegrationTest {

	@Test
	void anOwnerAddsACustomerWithTidiedContactDetails() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");

		MvcTestResult result = postAs(token, "/api/customers",
				customer(" Aarav  Shah ", " Aarav@Example.com ", "98765 43210"));

		assertThat(result).hasStatus(HttpStatus.CREATED).bodyJson().isLenientlyEqualTo("""
				{"name":"Aarav Shah","email":"aarav@example.com","phone":"+919876543210","optedOut":false,
				 "createdAt":"2026-01-05T04:30:00Z"}""");
	}

	@Test
	void theUnsubscribeTokenIsRandomAndNeverSentToTheOwner() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");

		MvcTestResult first = postAs(token, "/api/customers", customer("Aarav", "aarav@example.com", null));
		postAs(token, "/api/customers", customer("Diya", "diya@example.com", null));

		assertThat(bodyOf(first)).doesNotContain("unsub");
		assertThat(this.jdbc.queryForList("SELECT unsub_token FROM customers", String.class)).hasSize(2)
			.doesNotHaveDuplicates()
			.allSatisfy((value) -> assertThat(value).matches("[A-Za-z0-9_-]{43}"));
	}

	@Test
	void invalidCustomersNameEveryBadField() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");

		MvcTestResult result = postAs(token, "/api/customers", customer(" ", "not-an-email", "12345"));

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().isStrictlyEqualTo("""
				{"code":"VALIDATION_FAILED","message":"Request validation failed","details":{
				  "name":"Enter a name","email":"Enter a valid email address",
				  "phone":"Enter a 10-digit mobile number, or + and the country code"}}""");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM customers", Long.class)).isZero();
	}

	@Test
	void anEmailBelongsToOneCustomerPerBusiness() {
		String asha = signUpOwner("asha@example.com", "Bright Tuition");
		String ravi = signUpOwner("ravi@example.com", "Iron Gym");
		postAs(asha, "/api/customers", customer("Aarav", "parent@example.com", null));

		MvcTestResult again = postAs(asha, "/api/customers", customer("Diya", "PARENT@example.com", null));

		assertThat(again).hasStatus(HttpStatus.CONFLICT).bodyJson().isStrictlyEqualTo("""
				{"code":"CUSTOMER_EMAIL_TAKEN","message":"Another customer already uses this email",
				 "details":{"email":"Another customer already uses this email"}}""");
		// Another business may have its own customer with the same email.
		assertThat(postAs(ravi, "/api/customers", customer("Parent", "parent@example.com", null)))
			.hasStatus(HttpStatus.CREATED);
	}

	@Test
	void customersAreListedByNameOnePageAtATime() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");
		for (String name : new String[] { "diya", "Aarav", "Chetan", "bina" }) {
			postAs(token, "/api/customers", customer(name, name.toLowerCase() + "@example.com", null));
		}

		MvcTestResult first = getAs(token, "/api/customers?size=3");
		assertThat(first).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"page":0,"size":3,"totalItems":4,"totalPages":2}""");
		assertThat(first).bodyJson().extractingPath("$.items[*].name").isEqualTo(List.of("Aarav", "bina", "Chetan"));

		assertThat(getAs(token, "/api/customers?size=3&page=1")).bodyJson()
			.extractingPath("$.items[*].name")
			.isEqualTo(List.of("diya"));
	}

	@Test
	void searchMatchesPartOfANameEmailOrPhone() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");
		postAs(token, "/api/customers", customer("Aarav Shah", "aarav@example.com", null));
		postAs(token, "/api/customers", customer("Diya Rao", null, "98765 43210"));
		postAs(token, "/api/customers", customer("100% Fitness", "gym@example.org", null));

		assertThat(names(token, "SHAH")).containsExactly("Aarav Shah");
		assertThat(names(token, "example.org")).containsExactly("100% Fitness");
		assertThat(names(token, "4321")).containsExactly("Diya Rao");
		// LIKE wildcards in the search are taken literally.
		assertThat(names(token, "%")).containsExactly("100% Fitness");
		assertThat(names(token, "_")).isEmpty();
	}

	@Test
	void anOwnerEditsACustomer() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");
		long aarav = idOf(postAs(token, "/api/customers", customer("Aarav", "aarav@example.com", null)));
		postAs(token, "/api/customers", customer("Diya", "diya@example.com", null));

		MvcTestResult renamed = putAs(token, "/api/customers/" + aarav, customer("Aarav Shah", "AARAV@example.com", "9876543210"));
		assertThat(renamed).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"name":"Aarav Shah","email":"aarav@example.com","phone":"+919876543210"}""");

		MvcTestResult clash = putAs(token, "/api/customers/" + aarav, customer("Aarav", "diya@example.com", null));
		assertThat(clash).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("CUSTOMER_EMAIL_TAKEN");

		MvcTestResult phoneOnly = putAs(token, "/api/customers/" + aarav, customer("Aarav", null, "9876543210"));
		assertThat(phoneOnly).hasStatusOk().bodyJson().extractingPath("$.email").isNull();
	}

	@Test
	void deletingACustomerAlsoDeletesTheirDues() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");
		long aarav = idOf(postAs(token, "/api/customers", customer("Aarav", "aarav@example.com", null)));
		this.jdbc.update("""
				INSERT INTO dues (business_id, customer_id, amount_paise, due_date, status, pay_token, policy_snapshot)
				SELECT business_id, id, 150000, DATE '2026-02-01', 'SCHEDULED', 'pay-token-1', '{}'::jsonb
				FROM customers WHERE id = ?""", aarav);

		assertThat(deleteAs(token, "/api/customers/" + aarav)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM customers", Long.class)).isZero();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM dues", Long.class)).isZero();
		assertThat(deleteAs(token, "/api/customers/" + aarav)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void pagesAreAtMostOneHundredCustomers() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");

		assertThat(getAs(token, "/api/customers?size=100")).hasStatusOk();
		assertThat(getAs(token, "/api/customers?size=101")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("VALIDATION_FAILED");
		assertThat(getAs(token, "/api/customers?page=-1")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void customersNeedASignedInOwner() {
		assertThat(this.mvc.get().uri("/api/customers").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(this.mvc.post().uri("/api/customers").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(this.mvc.delete().uri("/api/customers/1").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void anOwnerCanNeverSeeOrTouchAnotherOwnersCustomers() {
		String asha = signUpOwner("asha@example.com", "Bright Tuition");
		String ravi = signUpOwner("ravi@example.com", "Iron Gym");
		long aarav = idOf(postAs(asha, "/api/customers", customer("Aarav", "aarav@example.com", null)));

		assertThat(getAs(ravi, "/api/customers")).bodyJson().extractingPath("$.totalItems").isEqualTo(0);
		assertThat(getAs(ravi, "/api/customers?q=aarav")).bodyJson().extractingPath("$.totalItems").isEqualTo(0);
		assertThat(putAs(ravi, "/api/customers/" + aarav, customer("Hijacked", "evil@example.com", null)))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(deleteAs(ravi, "/api/customers/" + aarav)).hasStatus(HttpStatus.NOT_FOUND);

		assertThat(getAs(asha, "/api/customers")).bodyJson().extractingPath("$.items[0].name").isEqualTo("Aarav");
	}

	private static Map<String, Object> customer(String name, String email, String phone) {
		Map<String, Object> body = new HashMap<>();
		body.put("name", name);
		body.put("email", email);
		body.put("phone", phone);
		return body;
	}

	private List<String> names(String token, String query) {
		// MockMvc encodes the URI itself, so the query goes in raw.
		return JsonPath.read(bodyOf(getAs(token, "/api/customers?q=" + query)), "$.items[*].name");
	}

	private static long idOf(MvcTestResult result) {
		return ((Number) JsonPath.read(bodyOf(result), "$.id")).longValue();
	}

}
