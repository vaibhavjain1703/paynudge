package com.paynudge.csvimport;

import java.time.LocalDate;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DueDatesTest {

	@ParameterizedTest
	@ValueSource(strings = { "05/11/2026", "5/11/2026", "05-11-2026", "05.11.2026", "05/11/26", "2026-11-05",
			"2026-11-5", "5 Nov 2026", "05-Nov-2026", "05-nov-26", "5 November 2026", "5th November 2026",
			"Nov 5, 2026", "November 5 2026", "5 Nov, 2026", "2026-11-05 00:00:00", "2026-11-05T00:00", "05/11/2026 12:00 AM",
			"  5   Nov   2026 " })
	void commonWaysOfWritingTheFifthOfNovember(String raw) {
		assertThat(DueDates.parse(raw)).isEqualTo(LocalDate.of(2026, 11, 5));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			''            | Enter the due date
			11/25/2026    | Use a date like 05/11/2026 (day/month/year)
			31/02/2026    | Use a date like 05/11/2026 (day/month/year)
			tomorrow      | Use a date like 05/11/2026 (day/month/year)
			05/11/1926    | Check the year in this date
			""")
	void unusableDatesSayWhy(String raw, String message) {
		assertThatThrownBy(() -> DueDates.parse(raw)).isInstanceOf(CellException.class).hasMessage(message);
	}

}
