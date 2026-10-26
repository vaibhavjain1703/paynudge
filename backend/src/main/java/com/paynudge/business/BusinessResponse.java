package com.paynudge.business;

import java.time.LocalTime;

/** @param demo made by "Try the demo": sample data, deleted a day after it was made */
public record BusinessResponse(long id, String name, String timezone, LocalTime quietStart, LocalTime quietEnd,
		String senderName, int dailySendCap, boolean demo) {

	public static BusinessResponse from(Business business) {
		return new BusinessResponse(business.getId(), business.getName(), business.getTimezone(),
				business.getQuietStart(), business.getQuietEnd(), business.getSenderName(),
				business.getDailySendCap(), business.isDemo());
	}

}
