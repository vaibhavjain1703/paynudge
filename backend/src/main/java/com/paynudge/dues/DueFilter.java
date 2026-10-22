package com.paynudge.dues;

import java.util.Arrays;
import java.util.stream.Collectors;

import com.paynudge.common.ApiException;

/** The chips above the dues list. Each is a condition on {@code dues d}; overdue compares with today's date. */
enum DueFilter {

	ALL("all", "true"),
	/** Still being reminded or waiting on the owner: not paid, not cancelled. */
	OPEN("open", "d.status IN ('SCHEDULED', 'ACTIVE', 'ESCALATED')"),
	/** A reminder failed, or the policy has run out: the owner should step in. */
	NEEDS_YOU("needs-you", "d.status = 'ESCALATED'"),
	/** Open, and the due date (in the business's timezone) has passed. */
	OVERDUE("overdue", "d.status IN ('SCHEDULED', 'ACTIVE', 'ESCALATED') AND d.due_date < :today"),
	PAID("paid", "d.status = 'PAID'"),
	CANCELLED("cancelled", "d.status = 'CANCELLED'");

	private final String value;

	private final String condition;

	DueFilter(String value, String condition) {
		this.value = value;
		this.condition = condition;
	}

	String condition() {
		return this.condition;
	}

	static DueFilter from(String value) {
		return Arrays.stream(values())
			.filter((filter) -> filter.value.equals(value))
			.findFirst()
			.orElseThrow(() -> ApiException.invalidField("filter", "Use one of: "
					+ Arrays.stream(values()).map((filter) -> filter.value).collect(Collectors.joining(", "))));
	}

}
