package com.paynudge.dues;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.paynudge.dues.DueDtos.BulkActionRequest;
import com.paynudge.dues.DueDtos.BulkActionResult;
import com.paynudge.dues.DueDtos.CreateDueRequest;
import com.paynudge.dues.DueDtos.DueDetail;
import com.paynudge.dues.DueDtos.DuePage;
import com.paynudge.security.Caller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dues")
class DueController {

	private final DueService dues;

	DueController(DueService dues) {
		this.dues = dues;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	DueDetail create(Caller caller, @RequestBody CreateDueRequest request) {
		return this.dues.create(caller, request);
	}

	/**
	 * @param filter all, open, needs-you, overdue, paid or cancelled
	 * @param q part of a customer's name, email or phone
	 */
	@GetMapping
	DuePage list(Caller caller, @RequestParam(defaultValue = "all") String filter,
			@RequestParam(name = "q", required = false) String q,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
		return this.dues.list(caller, filter, q, page, size);
	}

	@GetMapping("/{id}")
	DueDetail detail(Caller caller, @PathVariable long id) {
		return this.dues.detail(caller, id);
	}

	@PostMapping("/{id}/mark-paid")
	DueDetail markPaid(Caller caller, @PathVariable long id) {
		return this.dues.markPaid(caller, id);
	}

	@PostMapping("/{id}/cancel")
	DueDetail cancel(Caller caller, @PathVariable long id) {
		return this.dues.cancel(caller, id);
	}

	@PostMapping("/bulk")
	BulkActionResult bulk(Caller caller, @RequestBody BulkActionRequest request) {
		return this.dues.bulk(caller, request);
	}

}
