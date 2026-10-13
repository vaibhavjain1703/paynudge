package com.paynudge.policy;

import java.time.Instant;
import java.util.List;

import com.paynudge.policy.PolicyDefinition.Step;

/** Request and response bodies for the policy endpoints. */
final class PolicyDtos {

	private PolicyDtos() {
	}

	/** Checked by {@link PolicyRules}; problems come back keyed by field, e.g. "steps[2].body". */
	record PolicyRequest(String name, List<Step> steps) {
	}

	/** @param starter a built-in policy: usable and copyable, but not editable */
	record PolicyResponse(long id, String name, boolean starter, List<Step> steps, Instant createdAt) {
	}

}
