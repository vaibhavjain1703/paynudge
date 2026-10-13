package com.paynudge.customer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class CustomerFieldsTest {

	@Test
	void namesAndEmailsAreTidied() {
		CustomerFields.Parsed parsed = CustomerFields.parse("  Aarav   Shah ", "  Aarav.Shah@Example.COM ", null);

		assertThat(parsed.valid()).isTrue();
		assertThat(parsed.fields()).isEqualTo(new CustomerFields("Aarav Shah", "aarav.shah@example.com", null));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			9876543210        | +919876543210
			98765 43210       | +919876543210
			98765-43210       | +919876543210
			(987) 654-3210    | +919876543210
			09876543210       | +919876543210
			919876543210      | +919876543210
			+91 98765 43210   | +919876543210
			+91-98765-43210   | +919876543210
			+1 415 555 0100   | +14155550100
			+44 20 7946 0958  | +442079460958
			""")
	void phonesAreStoredWithTheirCountryCode(String raw, String stored) {
		assertThat(CustomerFields.parse("Aarav", null, raw).fields().phone()).isEqualTo(stored);
	}

	@ParameterizedTest
	@ValueSource(strings = { "12345", "5876543210", "98765432101", "+91 12345", "phone", "98765+43210", "+1234567" })
	void unclearPhonesAreRejected(String raw) {
		CustomerFields.Parsed parsed = CustomerFields.parse("Aarav", null, raw);

		assertThat(parsed.valid()).isFalse();
		assertThat(parsed.errors()).containsOnly(
				entry("phone", "Enter a 10-digit mobile number, or + and the country code"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "aarav", "aarav@", "@example.com", "aarav@example", "aarav shah@example.com" })
	void malformedEmailsAreRejected(String raw) {
		assertThat(CustomerFields.parse("Aarav", raw, null).errors()).containsEntry("email", "Enter a valid email address")
			.hasSize(1);
	}

	@Test
	void everyCustomerNeedsAWayToBeReached() {
		assertThat(CustomerFields.parse("Aarav", " ", "").errors()).containsOnlyKeys("email")
			.containsEntry("email", "Add an email or a phone number");
		assertThat(CustomerFields.parse("Aarav", null, "9876543210").valid()).isTrue();
	}

	@Test
	void namesAreRequiredAndBounded() {
		assertThat(CustomerFields.parse("   ", "a@example.com", null).errors()).containsEntry("name", "Enter a name");
		assertThat(CustomerFields.parse("x".repeat(201), "a@example.com", null).errors()).containsEntry("name",
				"Keep the name under 200 characters");
	}

	@Test
	void everyProblemIsReportedAtOnce() {
		assertThat(CustomerFields.parse("", "nope", "123").errors()).containsOnlyKeys("name", "email", "phone");
	}

}
