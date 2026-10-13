package com.paynudge.csvimport;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ColumnMatcherTest {

	@Test
	void matchesTheHeadersPeopleActuallyUse() {
		ColumnMapping mapping = ColumnMatcher
			.guess(List.of("Sr. No.", "Customer Name", "E-mail ID", "Mobile No.", "Amount (₹)", "Due Date", "Notes"));

		assertThat(mapping).isEqualTo(new ColumnMapping("Customer Name", "E-mail ID", "Mobile No.", "Amount (₹)", "Due Date"));
		assertThat(mapping.missing()).isEmpty();
	}

	@Test
	void partialMatchesPickTheRightField() {
		ColumnMapping mapping = ColumnMatcher
			.guess(List.of("Student / Parent Name", "Parent Email Address", "WhatsApp No", "Monthly Fee", "Pay by date"));

		assertThat(mapping).isEqualTo(new ColumnMapping("Student / Parent Name", "Parent Email Address", "WhatsApp No",
				"Monthly Fee", "Pay by date"));
	}

	@Test
	void dueAmountIsAnAmountNotADate() {
		ColumnMapping mapping = ColumnMatcher.guess(List.of("Name", "Due Amount", "Due On", "Phone"));

		assertThat(mapping.amount()).isEqualTo("Due Amount");
		assertThat(mapping.dueDate()).isEqualTo("Due On");
	}

	@Test
	void unknownFilesLeaveFieldsForTheOwnerToChoose() {
		ColumnMapping mapping = ColumnMatcher.guess(List.of("Col A", "Col B"));

		assertThat(mapping.missing()).containsExactly("name", "contact", "amount", "dueDate");
	}

}
