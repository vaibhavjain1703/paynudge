package com.paynudge.csvimport;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import com.paynudge.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/** A due typed in by hand on the import page: the CSV rules, one due at a time. */
class ManualEntryApiTest extends IntegrationTest {

	private String asha;

	private long policy;

	@BeforeEach
	void ashaWithHerStarterPolicies() {
		this.asha = signUpOwner("asha@example.com", "Bright Tuition");
		this.policy = ((Number) JsonPath.read(bodyOf(getAs(this.asha, "/api/policies")), "$[0].id")).longValue();
	}

	@Test
	void aDueTypedInCreatesItsCustomerAndSchedulesItsReminders() {
		MvcTestResult result = add(entry("  Rohan   Gupta ", "Rohan@Example.com", "98765 43210", "₹8,500", "2026-01-20"));

		long due = this.jdbc.queryForObject("SELECT id FROM dues", Long.class);
		long customer = this.jdbc.queryForObject("SELECT id FROM customers", Long.class);
		// Friendly then firm starts 2 days before the 20 January due date, at 10:00 in Kolkata.
		assertThat(result).hasStatus(HttpStatus.CREATED).bodyJson().isStrictlyEqualTo("""
				{"dueId":%d,"customerId":%d,"customerName":"Rohan Gupta","newCustomer":true,"optedOut":false,
				 "amountPaise":850000,"dueDate":"2026-01-20","firstReminderAt":"2026-01-18T04:30:00Z"}"""
			.formatted(due, customer));
		assertThat(this.jdbc.queryForObject("SELECT name || ' ' || email || ' ' || phone FROM customers", String.class))
			.isEqualTo("Rohan Gupta rohan@example.com +919876543210");

		// It is in the dues list like any imported due, following its own copy of the policy.
		MvcTestResult dues = getAs(this.asha, "/api/dues");
		assertThat(dues).bodyJson().extractingPath("$.totalItems").isEqualTo(1);
		assertThat(dues).bodyJson().extractingPath("$.items[0].customer.name").isEqualTo("Rohan Gupta");
		assertThat(dues).bodyJson().extractingPath("$.items[0].amountPaise").isEqualTo(850000);
		assertThat(dues).bodyJson().extractingPath("$.items[0].status").isEqualTo("SCHEDULED");
		assertThat(getAs(this.asha, "/api/dues/" + due)).bodyJson()
			.extractingPath("$.policyName")
			.isEqualTo("Friendly then firm");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM due_steps WHERE due_id = ? AND status = 'PENDING'",
				Long.class, due))
			.isEqualTo(5);
	}

	@Test
	void aKnownEmailAddsTheDueToThatCustomerAsTheyAre() {
		long rohan = ((Number) JsonPath.read(bodyOf(postAs(this.asha, "/api/customers",
				Map.of("name", "Rohan Gupta", "email", "rohan@example.com"))), "$.id")).longValue();

		MvcTestResult result = add(entry("R. Gupta", "ROHAN@example.com", "9876543210", "1500", "2026-01-20"));

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).bodyJson().extractingPath("$.customerId").isEqualTo((int) rohan);
		assertThat(result).bodyJson().extractingPath("$.newCustomer").isEqualTo(false);
		assertThat(result).bodyJson().extractingPath("$.customerName").isEqualTo("Rohan Gupta");
		assertThat(this.jdbc.queryForObject("SELECT name || ' ' || coalesce(phone, '-') FROM customers", String.class))
			.as("an existing customer keeps their own details, as with a CSV import")
			.isEqualTo("Rohan Gupta -");
	}

	@Test
	void aPhoneWithoutAnEmailIsEnoughAndFindsTheSameCustomerNextTime() {
		MvcTestResult first = add(entry("Kabir Mehta", "", "91234 56789", "2000", "2026-01-20"));
		MvcTestResult second = add(entry("Kabir Mehta", null, "+91 91234 56789", "2000", "2026-02-20"));

		assertThat(first).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.newCustomer").isEqualTo(true);
		assertThat(second).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.newCustomer").isEqualTo(false);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM customers", Long.class)).isEqualTo(1);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM dues", Long.class)).isEqualTo(2);
	}

	@Test
	void theSameDueTypedTwiceIsRefusedSoNobodyIsRemindedTwice() {
		long first = ((Number) JsonPath.read(
				bodyOf(add(entry("Rohan Gupta", "rohan@example.com", "", "8500", "2026-01-20"))), "$.dueId"))
			.longValue();

		MvcTestResult again = add(entry("rohan  gupta", "ROHAN@example.com", "", "8,500.00", "20/01/2026"));

		assertThat(again).hasStatus(HttpStatus.CONFLICT).bodyJson().isStrictlyEqualTo("""
				{"code":"DUE_EXISTS","message":"This due is already in your list","details":{"dueId":%d}}"""
			.formatted(first));
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM dues", Long.class)).isEqualTo(1);
	}

	@Test
	void aDueTypedInAndTheSameRowInAFileAreOneDue() {
		add(entry("Rohan Gupta", "rohan@example.com", "", "8500", "2026-01-20"));
		upload("Name,Email,Amount,Due Date\nRohan Gupta,rohan@example.com,\"8,500\",20/01/2026\n");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM dues", Long.class))
			.as("the import skips the row typed in before")
			.isEqualTo(1);

		upload("Name,Email,Amount,Due Date\nAarav Shah,aarav@example.com,1500,05/02/2026\n");
		assertThat(add(entry("Aarav Shah", "aarav@example.com", "", "1500", "2026-02-05")))
			.as("typing in a due imported before is refused")
			.hasStatus(HttpStatus.CONFLICT);
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM dues", Long.class)).isEqualTo(2);
	}

	@Test
	void everyProblemIsReportedUnderItsFieldAndNothingIsSaved() {
		Map<String, Object> entry = entry(" ", "rohan@", "12345", "-5", "31/02/2026");
		entry.put("policyId", null);

		MvcTestResult result = add(entry);

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().isStrictlyEqualTo("""
				{"code":"VALIDATION_FAILED","message":"Request validation failed","details":{
				  "name":"Enter a name","email":"Enter a valid email address",
				  "phone":"Enter a 10-digit mobile number, or + and the country code",
				  "amount":"The amount must be more than zero",
				  "dueDate":"Use a date like 05/11/2026 (day/month/year)","policyId":"Choose a reminder policy"}}""");
		assertThat(add(entry("Rohan", "", "", "", ""))).bodyJson().extractingPath("$.details").isEqualTo(Map.of(
				"email", "Add an email or a phone number", "amount", "Enter the amount", "dueDate",
				"Enter the due date"));
		assertThat(add(entry("Rohan", "rohan@example.com", "", "1500.505", "2026-01-20"))).bodyJson()
			.extractingPath("$.details")
			.isEqualTo(Map.of("amount", "Use at most 2 decimal places"));
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM customers", Long.class)).isZero();
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM dues", Long.class)).isZero();
	}

	@Test
	void anotherBusinesssPolicyIsRefusedAndNothingIsSaved() {
		String ravi = signUpOwner("ravi@example.com", "Ravi Gym");
		Map<String, Object> entry = entry("Rohan Gupta", "rohan@example.com", "", "8500", "2026-01-20");
		entry.put("policyId", this.policy);

		assertThat(postAs(ravi, "/api/import/manual", entry)).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.details.policyId")
			.isEqualTo("Choose one of your reminder policies");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM customers", Long.class))
			.as("the customer is not left behind either")
			.isZero();
	}

	@Test
	void anUnsubscribedCustomerGetsTheDueButNoReminderTime() {
		postAs(this.asha, "/api/customers", Map.of("name", "Rohan Gupta", "email", "rohan@example.com"));
		this.jdbc.update("UPDATE customers SET opted_out = true, opted_out_at = now()");

		MvcTestResult result = add(entry("Rohan Gupta", "rohan@example.com", "", "8500", "2026-01-20"));

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result).bodyJson().extractingPath("$.optedOut").isEqualTo(true);
		assertThat(result).bodyJson().extractingPath("$.firstReminderAt").isNull();
	}

	@Test
	void addingADueNeedsASignedInOwner() {
		assertThat(this.mvc.post()
			.uri("/api/import/manual")
			.contentType(MediaType.APPLICATION_JSON)
			.content(this.json.writeValueAsString(entry("Rohan", "rohan@example.com", "", "8500", "2026-01-20")))
			.exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private Map<String, Object> entry(String name, String email, String phone, String amount, String dueDate) {
		Map<String, Object> entry = new HashMap<>();
		entry.put("name", name);
		entry.put("email", email);
		entry.put("phone", phone);
		entry.put("amount", amount);
		entry.put("dueDate", dueDate);
		entry.put("policyId", this.policy);
		return entry;
	}

	private MvcTestResult add(Map<String, Object> entry) {
		return postAs(this.asha, "/api/import/manual", entry);
	}

	private void upload(String csv) {
		MockPart mapping = new MockPart("mapping",
				this.json.writeValueAsBytes(Map.of("name", "Name", "email", "Email", "amount", "Amount", "dueDate",
						"Due Date")));
		mapping.getHeaders().setContentType(MediaType.APPLICATION_JSON);
		assertThat(this.mvc.post()
			.uri("/api/import/commit")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + this.asha)
			.multipart()
			.file(new MockMultipartFile("file", "dues.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
			.part(mapping)
			.param("policyId", Long.toString(this.policy))
			.exchange()).hasStatusOk();
	}

}
