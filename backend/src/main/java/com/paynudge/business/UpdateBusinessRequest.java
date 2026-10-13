package com.paynudge.business;

import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.paynudge.common.Text;

/**
 * Every editable business setting (PUT replaces them all). Quiet hours may cross midnight, e.g. 21:00 to 09:00.
 * {@link BusinessService} also checks the timezone and that the quiet window is not empty.
 */
record UpdateBusinessRequest(@NotBlank @Size(max = 200) String name, @NotBlank String timezone,
		@NotNull LocalTime quietStart, @NotNull LocalTime quietEnd, @Size(max = 100) String senderName,
		@NotNull @Min(1) @Max(Business.MAX_DAILY_SEND_CAP) Integer dailySendCap) {

	UpdateBusinessRequest {
		name = Text.strip(name);
		timezone = Text.strip(timezone);
		senderName = Text.stripToNull(senderName);
	}

}
