package com.paynudge.business;

import java.time.LocalTime;

public record BusinessResponse(long id, String name, String timezone, LocalTime quietStart, LocalTime quietEnd,
		String senderName, int dailySendCap) {

	public static BusinessResponse from(Business business) {
		return new BusinessResponse(business.getId(), business.getName(), business.getTimezone(),
				business.getQuietStart(), business.getQuietEnd(), business.getSenderName(),
				business.getDailySendCap());
	}

}
