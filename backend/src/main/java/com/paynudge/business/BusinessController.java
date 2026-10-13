package com.paynudge.business;

import jakarta.validation.Valid;

import com.paynudge.security.Caller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/business")
class BusinessController {

	private final BusinessService businesses;

	BusinessController(BusinessService businesses) {
		this.businesses = businesses;
	}

	@GetMapping
	BusinessResponse get(Caller caller) {
		return this.businesses.get(caller);
	}

	@PutMapping
	BusinessResponse update(Caller caller, @Valid @RequestBody UpdateBusinessRequest request) {
		return this.businesses.update(caller, request);
	}

}
