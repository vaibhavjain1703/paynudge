package com.paynudge.policy;

import java.util.List;

import com.paynudge.policy.PolicyDtos.PolicyRequest;
import com.paynudge.policy.PolicyDtos.PolicyResponse;
import com.paynudge.security.Caller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/policies")
class PolicyController {

	private final PolicyService policies;

	PolicyController(PolicyService policies) {
		this.policies = policies;
	}

	@GetMapping
	List<PolicyResponse> list(Caller caller) {
		return this.policies.list(caller);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	PolicyResponse create(Caller caller, @RequestBody PolicyRequest request) {
		return this.policies.create(caller, request);
	}

	@PutMapping("/{id}")
	PolicyResponse update(Caller caller, @PathVariable long id, @RequestBody PolicyRequest request) {
		return this.policies.update(caller, id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(Caller caller, @PathVariable long id) {
		this.policies.delete(caller, id);
	}

}
