package com.paynudge.customer;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.paynudge.common.PageResponse;
import com.paynudge.customer.CustomerDtos.CustomerRequest;
import com.paynudge.customer.CustomerDtos.CustomerResponse;
import com.paynudge.security.Caller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
class CustomerController {

	static final int MAX_PAGE_SIZE = 100;

	private final CustomerService customers;

	CustomerController(CustomerService customers) {
		this.customers = customers;
	}

	@GetMapping
	PageResponse<CustomerResponse> list(Caller caller, @RequestParam(required = false) String q,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "25") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
		return this.customers.list(caller, q, page, size);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	CustomerResponse create(Caller caller, @RequestBody CustomerRequest request) {
		return this.customers.create(caller, request);
	}

	@PutMapping("/{id}")
	CustomerResponse update(Caller caller, @PathVariable long id, @RequestBody CustomerRequest request) {
		return this.customers.update(caller, id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(Caller caller, @PathVariable long id) {
		this.customers.delete(caller, id);
	}

}
