package com.paynudge.csvimport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import com.paynudge.IntegrationTest;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

class ImportApiTest extends IntegrationTest {

	private static final Map<String, String> MESSY_MAPPING = Map.of("name", "Customer Name", "email", "E-mail",
			"phone", "Mobile No.", "amount", "Amount (₹)", "dueDate", "Due Date");

	@Test
	void previewMatchesColumnsAndExplainsEveryBadRowWithoutSavingAnything() throws IOException {
		String token = signUpOwner("asha@example.com", "Bright Tuition");

		MvcTestResult result = upload(token, "/api/import/preview", messyCsv(), null);

		assertThat(result).hasStatusOk();
		assertThat(result).bodyJson().extractingPath("$.mapping").isEqualTo(MESSY_MAPPING);
		assertThat(result).bodyJson().extractingPath("$.missingColumns").asArray().isEmpty();
		assertThat(result).bodyJson().extractingPath("$.summary").isEqualTo(
				Map.of("rows", 11, "valid", 4, "invalid", 7, "newCustomers", 3, "existingCustomers", 0));
		assertThat(result).bodyJson().extractingPath("$.rows[*].row")
			.isEqualTo(List.of(2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13));

		assertThat(result).bodyJson().extractingPath("$.rows[0].values").isEqualTo(Map.of("name", "Aarav Shah",
				"email", "aarav.shah@example.com", "phone", "98765 43210", "amount", "₹1,500", "dueDate",
				"05/11/2026"));
		assertThat(result).bodyJson().extractingPath("$.rows[0].amountPaise").isEqualTo(150000);
		assertThat(result).bodyJson().extractingPath("$.rows[0].dueDate").isEqualTo("2026-11-05");
		assertThat(result).bodyJson().extractingPath("$.rows[0].errors").isEqualTo(Map.of());
		assertThat(result).bodyJson().extractingPath("$.rows[0].note").isNull();
		assertThat(result).bodyJson().extractingPath("$.rows[0].existingCustomer").isEqualTo(false);
		assertThat(result).bodyJson().extractingPath("$.rows[1].amountPaise").isEqualTo(150050);
		assertThat(result).bodyJson().extractingPath("$.rows[2].amountPaise").isEqualTo(200000);

		assertThat(errorsOf(result, 6)).isEqualTo(Map.of("email", "Enter a valid email address"));
		assertThat(errorsOf(result, 7))
			.isEqualTo(Map.of("phone", "Enter a 10-digit mobile number, or + and the country code"));
		assertThat(errorsOf(result, 8)).isEqualTo(Map.of("amount", "Use a number like 1500 or 1,500.50"));
		assertThat(errorsOf(result, 9)).isEqualTo(Map.of("amount", "The amount must be more than zero"));
		assertThat(errorsOf(result, 10)).isEqualTo(Map.of("dueDate", "Use a date like 05/11/2026 (day/month/year)"));
		assertThat(errorsOf(result, 11)).isEqualTo(Map.of("name", "Enter a name"));
		assertThat(errorsOf(result, 13)).isEqualTo(Map.of("email", "Add an email or a phone number"));
		assertThat(result).bodyJson().extractingPath("$.rows[?(@.row == 12)].note")
			.isEqualTo(List.of("Same customer as row 2"));

		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM customers", Long.class)).isZero();
	}

	@Test
	void commitImportsTheValidRowsOnceAndARepeatFindsTheSameCustomers() throws IOException {
		String token = signUpOwner("asha@example.com", "Bright Tuition");

		MvcTestResult first = upload(token, "/api/import/commit", messyCsv(), MESSY_MAPPING);

		assertThat(first).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{"importedRows":4,"skippedRows":7,"customersCreated":3,"customersMatched":0}""");
		assertThat(this.jdbc.queryForList("SELECT name || ' ' || coalesce(email, '-') || ' ' || coalesce(phone, '-') "
				+ "FROM customers ORDER BY name", String.class))
			.containsExactly("Aarav Shah aarav.shah@example.com +919876543210", "Diya Rao diya@example.com -",
					"Kabir Mehta - +919123456789");
		assertThat(this.jdbc.queryForList("SELECT unsub_token FROM customers", String.class)).doesNotHaveDuplicates()
			.allSatisfy((value) -> assertThat(value).hasSize(43));

		MvcTestResult again = upload(token, "/api/import/commit", messyCsv(), MESSY_MAPPING);
		assertThat(again).hasStatusOk().bodyJson().isStrictlyEqualTo("""
				{"importedRows":4,"skippedRows":7,"customersCreated":0,"customersMatched":3}""");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM customers", Long.class)).isEqualTo(3);

		MvcTestResult preview = upload(token, "/api/import/preview", messyCsv(), null);
		assertThat(preview).bodyJson().extractingPath("$.summary.existingCustomers").isEqualTo(3);
		assertThat(preview).bodyJson().extractingPath("$.rows[0].existingCustomer").isEqualTo(true);
	}

	@Test
	void theOwnerCanChooseColumnsTheGuessMissed() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");
		byte[] csv = """
				Who,How to reach,How much,When
				Aarav,aarav@example.com,1500,05/11/2026
				""".getBytes(StandardCharsets.UTF_8);

		MvcTestResult guessed = upload(token, "/api/import/preview", csv, null);
		assertThat(guessed).hasStatusOk();
		assertThat(guessed).bodyJson().extractingPath("$.missingColumns")
			.isEqualTo(List.of("name", "contact", "amount", "dueDate"));
		assertThat(guessed).bodyJson().extractingPath("$.rows").asArray().isEmpty();
		assertThat(guessed).bodyJson().extractingPath("$.headers")
			.isEqualTo(List.of("Who", "How to reach", "How much", "When"));

		Map<String, String> chosen = Map.of("name", "Who", "email", "How to reach", "amount", "How much", "dueDate",
				"When");
		assertThat(upload(token, "/api/import/preview", csv, chosen)).bodyJson()
			.extractingPath("$.summary.valid")
			.isEqualTo(1);
		assertThat(upload(token, "/api/import/commit", csv, chosen)).hasStatusOk();
	}

	@Test
	void columnChoicesMustExistAndBeComplete() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");
		byte[] csv = "Name,Email,Amount,Due\nAarav,a@example.com,1500,05/11/2026\n".getBytes(StandardCharsets.UTF_8);

		MvcTestResult unknown = upload(token, "/api/import/preview", csv, Map.of("name", "Full Name"));
		assertThat(unknown).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().isStrictlyEqualTo("""
				{"code":"UNKNOWN_COLUMN","message":"Some chosen columns are not in this file",
				 "details":{"name":"This file has no column called \\"Full Name\\""}}""");

		MvcTestResult incomplete = upload(token, "/api/import/commit", csv, Map.of("name", "Name", "email", "Email"));
		assertThat(incomplete).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().isStrictlyEqualTo("""
				{"code":"MISSING_COLUMNS","message":"Choose a column for every required field",
				 "details":{"missingColumns":["amount","dueDate"]}}""");

		assertThat(upload(token, "/api/import/commit", csv, null)).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("MISSING_COLUMNS");
	}

	@Test
	void aFileWithNoUsableRowsImportsNothing() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");
		byte[] csv = "Name,Email,Amount,Due Date\nAarav,not-an-email,abc,someday\n".getBytes(StandardCharsets.UTF_8);

		MvcTestResult result = upload(token, "/api/import/commit", csv,
				Map.of("name", "Name", "email", "Email", "amount", "Amount", "dueDate", "Due Date"));

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code")
			.isEqualTo("NOTHING_TO_IMPORT");
		assertThat(this.jdbc.queryForObject("SELECT count(*) FROM customers", Long.class)).isZero();
	}

	@Test
	void fileProblemsUseTheSharedErrorShape() {
		String token = signUpOwner("asha@example.com", "Bright Tuition");

		assertThat(upload(token, "/api/import/preview", new byte[0], null)).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.isStrictlyEqualTo("""
					{"code":"EMPTY_FILE","message":"This file is empty.","details":{}}""");

		MvcTestResult noFile = this.mvc.post()
			.uri("/api/import/preview")
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.multipart()
			.part(new MockPart("other", "x".getBytes(StandardCharsets.UTF_8)))
			.exchange();
		assertThat(noFile).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().isStrictlyEqualTo("""
				{"code":"MALFORMED_REQUEST","message":"Choose a CSV file to upload","details":{}}""");
	}

	@Test
	void importNeedsASignedInOwner() {
		MvcTestResult result = this.mvc.post()
			.uri("/api/import/preview")
			.multipart()
			.file(new MockMultipartFile("file", "dues.csv", "text/csv", "Name\nA\n".getBytes(StandardCharsets.UTF_8)))
			.exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void importsOnlyEverTouchTheCallersOwnCustomers() throws IOException {
		String asha = signUpOwner("asha@example.com", "Bright Tuition");
		String ravi = signUpOwner("ravi@example.com", "Iron Gym");
		upload(asha, "/api/import/commit", messyCsv(), MESSY_MAPPING);

		// Ravi's preview of the same file finds none of Asha's customers...
		assertThat(upload(ravi, "/api/import/preview", messyCsv(), null)).bodyJson()
			.extractingPath("$.summary.existingCustomers")
			.isEqualTo(0);
		// ...and his import creates his own, leaving Asha's alone.
		assertThat(upload(ravi, "/api/import/commit", messyCsv(), MESSY_MAPPING)).bodyJson()
			.extractingPath("$.customersCreated")
			.isEqualTo(3);
		assertThat(this.jdbc.queryForList("""
				SELECT b.name || ':' || count(c.id) FROM businesses b JOIN customers c ON c.business_id = b.id
				GROUP BY b.name ORDER BY b.name""", String.class))
			.containsExactly("Bright Tuition:3", "Iron Gym:3");
	}

	private MvcTestResult upload(String token, String uri, byte[] csv, Map<String, String> mapping) {
		var request = this.mvc.post()
			.uri(uri)
			.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
			.multipart()
			.file(new MockMultipartFile("file", "dues.csv", "text/csv", csv));
		if (mapping != null) {
			MockPart part = new MockPart("mapping", this.json.writeValueAsBytes(mapping));
			part.getHeaders().setContentType(MediaType.APPLICATION_JSON);
			request = request.part(part);
		}
		return request.exchange();
	}

	private static Map<String, String> errorsOf(MvcTestResult result, int row) {
		List<Map<String, Object>> rows = JsonPath.read(bodyOf(result), "$.rows[?(@.row == " + row + ")]");
		@SuppressWarnings("unchecked")
		Map<String, String> errors = (Map<String, String>) rows.get(0).get("errors");
		return errors;
	}

	private static byte[] messyCsv() throws IOException {
		try (InputStream in = ImportApiTest.class.getResourceAsStream("/csv/messy-dues.csv")) {
			return in.readAllBytes();
		}
	}

}
