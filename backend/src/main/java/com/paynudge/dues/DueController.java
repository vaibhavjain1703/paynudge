package com.paynudge.dues;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import com.paynudge.common.PageResponse;
import com.paynudge.dues.DueDtos.CreateDueRequest;
import com.paynudge.dues.DueDtos.DueDetail;
import com.paynudge.dues.DueDtos.DueSummary;
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

	@GetMapping
	PageResponse<DueSummary> list(Caller caller, @RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
		return this.dues.list(caller, page, size);
	}

	@GetMapping("/{id}")
	DueDetail detail(Caller caller, @PathVariable long id) {
		return this.dues.detail(caller, id);
	}

}
