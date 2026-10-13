package com.paynudge.csvimport;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmountsTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			1500            | 150000
			1500.5          | 150050
			1500.50         | 150050
			1,500.50        | 150050
			'1,50,000'      | 15000000
			₹1,500     | 150000
			₹ 1,500    | 150000
			Rs. 1500/-      | 150000
			rs 1500         | 150000
			INR 999         | 99900
			1500 INR        | 150000
			0.01            | 1
			1500.00         | 150000
			10000000        | 1000000000
			""")
	void spreadsheetAmountsBecomeExactPaise(String raw, long paise) {
		assertThat(Amounts.parsePaise(raw)).isEqualTo(paise);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			''              | Enter the amount
			abc             | Use a number like 1500 or 1,500.50
			1.2.3           | Use a number like 1500 or 1,500.50
			1500 rupees     | Use a number like 1500 or 1,500.50
			-500            | The amount must be more than zero
			0               | The amount must be more than zero
			0.00            | The amount must be more than zero
			10.555          | Use at most 2 decimal places
			10000000.01     | Amounts above ₹1 crore are not supported
			""")
	void unusableAmountsSayWhy(String raw, String message) {
		assertThatThrownBy(() -> Amounts.parsePaise(raw)).isInstanceOf(CellException.class).hasMessage(message);
	}

}
