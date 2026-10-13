package com.paynudge.common;

/** Money is integer paise everywhere; this only formats it for people. */
public final class Money {

	private Money() {
	}

	/**
	 * Rupees with Indian digit grouping: 15000000 paise is "₹1,50,000" and 150050 is "₹1,500.50". (The JDK's
	 * en-IN formats group in threes, so the grouping is done here.)
	 */
	public static String rupees(long paise) {
		String sign = (paise < 0) ? "-" : "";
		long abs = Math.abs(paise);
		String whole = Long.toString(abs / 100);
		StringBuilder grouped = new StringBuilder();
		int length = whole.length();
		for (int i = 0; i < length; i++) {
			grouped.append(whole.charAt(i));
			int left = length - i - 1;
			if (left > 0 && (left == 3 || (left > 3 && (left - 3) % 2 == 0))) {
				grouped.append(',');
			}
		}
		long fraction = abs % 100;
		String rupees = sign + "₹" + grouped;
		return (fraction == 0) ? rupees : rupees + "." + String.format("%02d", fraction);
	}

}
