package com.paynudge.customer;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;

import com.paynudge.common.ApiException;
import com.paynudge.common.ErrorCodes;
import com.paynudge.common.PageResponse;
import com.paynudge.common.Text;
import com.paynudge.customer.CustomerDtos.CustomerRequest;
import com.paynudge.customer.CustomerDtos.CustomerResponse;
import com.paynudge.security.Caller;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The caller's customers. Every lookup is scoped by the caller's business. */
@Service
class CustomerService {

	static final String EMAIL_TAKEN = "CUSTOMER_EMAIL_TAKEN";

	private static final Sort BY_NAME = Sort.by(Sort.Order.asc("name").ignoreCase(), Sort.Order.asc("id"));

	private final CustomerRepository customers;

	private final Clock clock;

	CustomerService(CustomerRepository customers, Clock clock) {
		this.customers = customers;
		this.clock = clock;
	}

	/** Customers sorted by name. {@code query} matches part of a name, email or phone, ignoring case. */
	PageResponse<CustomerResponse> list(Caller caller, String query, int page, int size) {
		Pageable pageable = PageRequest.of(page, size, BY_NAME);
		String search = Text.stripToNull(query);
		Page<Customer> result = (search == null) ? this.customers.findByBusinessId(caller.businessId(), pageable)
				: this.customers.search(caller.businessId(), containsPattern(search), pageable);
		return PageResponse.of(result, CustomerResponse::from);
	}

	@Transactional
	CustomerResponse create(Caller caller, CustomerRequest request) {
		CustomerFields fields = parse(request);
		if (fields.email() != null && this.customers.existsByBusinessIdAndEmail(caller.businessId(), fields.email())) {
			throw emailTaken();
		}
		try {
			return CustomerResponse
				.from(this.customers.saveAndFlush(new Customer(caller.businessId(), fields, this.clock.instant())));
		}
		catch (DataIntegrityViolationException ex) {
			throw emailTaken(); // a parallel request added the same email first
		}
	}

	@Transactional
	CustomerResponse update(Caller caller, long id, CustomerRequest request) {
		Customer customer = find(caller, id);
		CustomerFields fields = parse(request);
		if (fields.email() != null
				&& this.customers.existsByBusinessIdAndEmailAndIdNot(caller.businessId(), fields.email(), id)) {
			throw emailTaken();
		}
		customer.update(fields);
		try {
			this.customers.flush();
		}
		catch (DataIntegrityViolationException ex) {
			throw emailTaken();
		}
		return CustomerResponse.from(customer);
	}

	/** Also deletes the customer's dues, their reminder steps and payments (the database cascades). */
	@Transactional
	void delete(Caller caller, long id) {
		this.customers.delete(find(caller, id));
	}

	private Customer find(Caller caller, long id) {
		return this.customers.findByIdAndBusinessId(id, caller.businessId())
			.orElseThrow(() -> ApiException.notFound("Customer not found"));
	}

	private static CustomerFields parse(CustomerRequest request) {
		CustomerFields.Parsed parsed = CustomerFields.parse(request.name(), request.email(), request.phone());
		if (!parsed.valid()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_FAILED, "Request validation failed",
					Map.copyOf(parsed.errors()));
		}
		return parsed.fields();
	}

	private static String containsPattern(String search) {
		String escaped = search.toLowerCase(Locale.ROOT)
			.replace("\\", "\\\\")
			.replace("%", "\\%")
			.replace("_", "\\_");
		return "%" + escaped + "%";
	}

	private static ApiException emailTaken() {
		return new ApiException(HttpStatus.CONFLICT, EMAIL_TAKEN, "Another customer already uses this email",
				Map.of("email", "Another customer already uses this email"));
	}

}
