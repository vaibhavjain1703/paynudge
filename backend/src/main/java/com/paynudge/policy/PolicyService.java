package com.paynudge.policy;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.paynudge.common.ApiException;
import com.paynudge.common.ErrorCodes;
import com.paynudge.common.Text;
import com.paynudge.policy.PolicyDefinition.Step;
import com.paynudge.policy.PolicyDtos.PolicyRequest;
import com.paynudge.policy.PolicyDtos.PolicyResponse;
import com.paynudge.security.Caller;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The caller's reminder policies, scoped by business. The starter policies are created the first time a business
 * needs them; they can be used and copied but not changed, so "Friendly then firm" means the same everywhere.
 */
@Service
public class PolicyService {

	static final String NAME_TAKEN = "POLICY_NAME_TAKEN";

	static final String STARTER_READ_ONLY = "STARTER_READ_ONLY";

	private final PolicyRepository policies;

	private final JdbcTemplate jdbc;

	private final JsonMapper json;

	private final Clock clock;

	PolicyService(PolicyRepository policies, JdbcTemplate jdbc, JsonMapper json, Clock clock) {
		this.policies = policies;
		this.jdbc = jdbc;
		this.json = json;
		this.clock = clock;
	}

	/** Starters first (in their usual order), then the owner's own policies by name. */
	@Transactional
	List<PolicyResponse> list(Caller caller) {
		ensureStarters(caller.businessId());
		List<String> starterOrder = StarterPolicies.ALL.stream().map(StarterPolicies.Starter::name).toList();
		return this.policies.findByBusinessId(caller.businessId())
			.stream()
			.sorted(Comparator.comparing((Policy policy) -> !policy.isStarter())
				.thenComparing((policy) -> policy.isStarter() ? starterOrder.indexOf(policy.getName()) : 0)
				.thenComparing(Policy::getName, String.CASE_INSENSITIVE_ORDER))
			.map(this::toResponse)
			.toList();
	}

	@Transactional
	PolicyResponse create(Caller caller, PolicyRequest request) {
		ensureStarters(caller.businessId()); // starter names are taken even before the first list
		String name = Text.strip(request.name());
		List<Step> steps = check(name, request.steps());
		if (this.policies.existsByBusinessIdAndName(caller.businessId(), name)) {
			throw nameTaken();
		}
		Policy policy = new Policy(caller.businessId(), name, toJson(definition(steps)), false, this.clock.instant());
		return toResponse(this.policies.saveAndFlush(policy));
	}

	@Transactional
	PolicyResponse update(Caller caller, long id, PolicyRequest request) {
		Policy policy = editable(caller, id);
		String name = Text.strip(request.name());
		List<Step> steps = check(name, request.steps());
		if (this.policies.existsByBusinessIdAndNameAndIdNot(caller.businessId(), name, id)) {
			throw nameTaken();
		}
		policy.update(name, toJson(definition(steps)));
		this.policies.flush();
		return toResponse(policy);
	}

	/** Dues created with this policy keep their own copy of it, so deleting never changes them. */
	@Transactional
	void delete(Caller caller, long id) {
		this.policies.delete(editable(caller, id));
	}

	/**
	 * The policy a new due will follow, as the snapshot to store on the due. Starters are created first if needed, so
	 * a business can schedule with them before ever opening the policies page.
	 */
	@Transactional
	public Snapshot snapshot(long businessId, long policyId) {
		ensureStarters(businessId);
		Policy policy = this.policies.findByIdAndBusinessId(policyId, businessId)
			.orElseThrow(() -> ApiException.invalidField("policyId", "Choose one of your reminder policies"));
		return new Snapshot(policy.getId(), policy.getName(), readJson(policy.getDefinition()), policy.getDefinition());
	}

	/** @param json the definition exactly as stored, ready to copy into {@code dues.policy_snapshot} */
	public record Snapshot(long policyId, String name, PolicyDefinition definition, String json) {
	}

	/** Creates any starter the business is missing. Safe to run concurrently: a name can only be taken once. */
	void ensureStarters(long businessId) {
		if (this.policies.countByBusinessIdAndStarterTrue(businessId) >= StarterPolicies.ALL.size()) {
			return;
		}
		Timestamp now = Timestamp.from(this.clock.instant());
		for (StarterPolicies.Starter starter : StarterPolicies.ALL) {
			this.jdbc.update("""
					INSERT INTO policies (business_id, name, definition, is_template, created_at)
					VALUES (?, ?, ?::jsonb, true, ?)
					ON CONFLICT (business_id, name) DO NOTHING""", businessId, starter.name(),
					toJson(definition(starter.steps())), now);
		}
	}

	private Policy editable(Caller caller, long id) {
		Policy policy = this.policies.findByIdAndBusinessId(id, caller.businessId())
			.orElseThrow(() -> ApiException.notFound("Policy not found"));
		if (policy.isStarter()) {
			throw ApiException.conflict(STARTER_READ_ONLY,
					"Starter policies can't be changed. Save a copy under a new name and edit that.");
		}
		return policy;
	}

	private static List<Step> check(String name, List<Step> steps) {
		Map<String, String> errors = PolicyRules.check(name, steps);
		if (!errors.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "Request validation failed",
					Map.copyOf(errors));
		}
		return steps;
	}

	private static PolicyDefinition definition(List<Step> steps) {
		return new PolicyDefinition(PolicyDefinition.SCHEMA_VERSION, List.copyOf(steps), PolicyDefinition.STOP_ON);
	}

	private PolicyResponse toResponse(Policy policy) {
		return new PolicyResponse(policy.getId(), policy.getName(), policy.isStarter(),
				readJson(policy.getDefinition()).steps(), policy.getCreatedAt());
	}

	private String toJson(PolicyDefinition definition) {
		return this.json.writeValueAsString(definition);
	}

	private PolicyDefinition readJson(String definition) {
		return this.json.readValue(definition, PolicyDefinition.class);
	}

	private static ApiException nameTaken() {
		return new ApiException(HttpStatus.CONFLICT, NAME_TAKEN, "You already have a policy with this name",
				Map.of("name", "You already have a policy with this name"));
	}

}
