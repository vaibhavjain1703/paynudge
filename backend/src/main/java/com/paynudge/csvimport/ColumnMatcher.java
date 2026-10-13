package com.paynudge.csvimport;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Guesses which column is which from the headers ("Customer Name", "E-mail", "Mobile No.", "Amount (₹)", "Due
 * Date"). Exact names win over partial ones, and each column is used once. The owner confirms or fixes the guess
 * before anything is imported.
 */
final class ColumnMatcher {

	/** Checked in this order, so "Due Amount" is an amount and "Customer Email" is an email. */
	private static final List<String> FIELDS = List.of("email", "phone", "amount", "dueDate", "name");

	private static final Map<String, Set<String>> EXACT = Map.of(
			"name", Set.of("name", "customername", "customer", "fullname", "studentname", "membername", "tenantname",
					"parentname", "clientname", "client", "payer", "payername"),
			"email", Set.of("email", "emailaddress", "emailid", "mail", "mailid", "parentemail"),
			"phone", Set.of("phone", "phonenumber", "phoneno", "mobile", "mobilenumber", "mobileno", "contact",
					"contactnumber", "contactno", "whatsapp", "whatsappnumber", "cell", "cellphone"),
			"amount", Set.of("amount", "amountdue", "dueamount", "fee", "fees", "feeamount", "balance", "outstanding",
					"total", "rent", "inr", "rs"),
			"dueDate", Set.of("duedate", "dueon", "date", "deadline", "paymentdate", "lastdate", "payby", "paybydate"));

	private static final Map<String, List<String>> PARTIAL = Map.of("email", List.of("email", "mail"), "phone",
			List.of("phone", "mobile", "whatsapp", "contact"), "amount", List.of("amount", "fee", "fees", "rent", "balance"),
			"dueDate", List.of("date", "due"), "name", List.of("name"));

	private ColumnMatcher() {
	}

	static ColumnMapping guess(List<String> headers) {
		Map<String, String> chosen = new LinkedHashMap<>();
		Set<String> used = new HashSet<>();
		for (String field : FIELDS) {
			for (String header : headers) {
				if (!used.contains(header) && EXACT.get(field).contains(normalize(header))) {
					chosen.put(field, header);
					used.add(header);
					break;
				}
			}
		}
		for (String field : FIELDS) {
			if (chosen.containsKey(field)) {
				continue;
			}
			for (String header : headers) {
				if (!used.contains(header) && PARTIAL.get(field).stream().anyMatch((token) -> mentions(header, token))) {
					chosen.put(field, header);
					used.add(header);
					break;
				}
			}
		}
		return new ColumnMapping(chosen.get("name"), chosen.get("email"), chosen.get("phone"), chosen.get("amount"),
				chosen.get("dueDate"));
	}

	/**
	 * Whether the header mentions the token: as a whole word ("Monthly Fee"), or, for tokens of five letters or more,
	 * anywhere ("ParentEmailAddress"). Short tokens only match whole words, so "Parent Name" is not about rent.
	 */
	private static boolean mentions(String header, String token) {
		String lower = header.toLowerCase(Locale.ROOT);
		if (Arrays.asList(lower.split("[^a-z0-9]+")).contains(token)) {
			return true;
		}
		return token.length() >= 5 && normalize(header).contains(token);
	}

	/** "E-mail ID" becomes "emailid", "Amount (₹)" becomes "amount". */
	private static String normalize(String header) {
		return header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
	}

}
