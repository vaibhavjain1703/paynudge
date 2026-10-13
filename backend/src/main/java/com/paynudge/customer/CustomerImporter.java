package com.paynudge.customer;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bulk customer work for imports. A customer is identified by {@link CustomerFields#key()}: the same email (or, with
 * no email, the same phone) as an existing customer means that customer, who is left as they are.
 */
@Service
public class CustomerImporter {

	private static final int BATCH_SIZE = 500;

	private final CustomerRepository customers;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	CustomerImporter(CustomerRepository customers, JdbcTemplate jdbc, Clock clock) {
		this.customers = customers;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** The id of each candidate that is already one of the business's customers, by key. */
	public Map<String, Long> existingIds(long businessId, Collection<CustomerFields> candidates) {
		List<String> emails = new ArrayList<>();
		List<String> phones = new ArrayList<>();
		for (CustomerFields candidate : candidates) {
			if (candidate.email() != null) {
				emails.add(candidate.email());
			}
			else {
				phones.add(candidate.phone());
			}
		}
		Map<String, Long> ids = new HashMap<>();
		if (!emails.isEmpty()) {
			this.customers.findByBusinessIdAndEmailIn(businessId, emails)
				.forEach((customer) -> ids.put("email:" + customer.getEmail(), customer.getId()));
		}
		if (!phones.isEmpty()) {
			this.customers.findByBusinessIdAndPhoneIn(businessId, phones)
				.forEach((customer) -> ids.putIfAbsent("phone:" + customer.getPhone(), customer.getId()));
		}
		return ids;
	}

	/**
	 * Creates every candidate that is not a customer yet (the first one per key wins) and returns the customer id for
	 * every key. Inserts are batched, and an email another request added meanwhile is skipped, not an error.
	 */
	@Transactional
	public Imported findOrCreate(long businessId, Collection<CustomerFields> candidates) {
		Map<String, CustomerFields> byKey = new LinkedHashMap<>();
		candidates.forEach((candidate) -> byKey.putIfAbsent(candidate.key(), candidate));
		Map<String, Long> existing = existingIds(businessId, byKey.values());
		List<CustomerFields> missing = byKey.values()
			.stream()
			.filter((candidate) -> !existing.containsKey(candidate.key()))
			.toList();

		Timestamp now = Timestamp.from(this.clock.instant());
		int[][] counts = this.jdbc.batchUpdate("""
				INSERT INTO customers (business_id, name, email, phone, unsub_token, created_at)
				VALUES (?, ?, ?, ?, ?, ?)
				ON CONFLICT (business_id, email) DO NOTHING""", missing, BATCH_SIZE, (statement, customer) -> {
			statement.setLong(1, businessId);
			statement.setString(2, customer.name());
			statement.setString(3, customer.email());
			statement.setString(4, customer.phone());
			statement.setString(5, Customer.newUnsubscribeToken());
			statement.setTimestamp(6, now);
		});
		int created = Arrays.stream(counts).flatMapToInt(Arrays::stream).map((count) -> Math.max(count, 0)).sum();
		return new Imported(existingIds(businessId, byKey.values()), created);
	}

	/** @param idsByKey the customer id for every key; @param created how many customers were new */
	public record Imported(Map<String, Long> idsByKey, int created) {
	}

}
