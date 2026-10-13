package com.paynudge.customer;

import java.time.Instant;

/** Request and response bodies for the customer endpoints. */
final class CustomerDtos {

	private CustomerDtos() {
	}

	/** Raw input; {@link CustomerFields#parse} cleans and checks it so the form and CSV import follow one rule set. */
	record CustomerRequest(String name, String email, String phone) {
	}

	record CustomerResponse(long id, String name, String email, String phone, boolean optedOut, Instant createdAt) {

		static CustomerResponse from(Customer customer) {
			return new CustomerResponse(customer.getId(), customer.getName(), customer.getEmail(),
					customer.getPhone(), customer.isOptedOut(), customer.getCreatedAt());
		}

	}

}
