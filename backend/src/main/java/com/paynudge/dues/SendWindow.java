package com.paynudge.dues;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * When a business may message its customers: never inside its quiet hours, which may cross midnight (21:00 to 09:00)
 * or not (13:00 to 15:00). Times are in the business's own timezone.
 */
public record SendWindow(ZoneId zone, LocalTime quietStart, LocalTime quietEnd) {

	/** Reminders go out at this time of day (business time) unless something moves them. */
	public static final LocalTime SEND_TIME = LocalTime.of(10, 0);

	/** From a business's settings, as stored. */
	public static SendWindow of(String timezone, LocalTime quietStart, LocalTime quietEnd) {
		return new SendWindow(ZoneId.of(timezone), quietStart, quietEnd);
	}

	/** The usual send time on the day after {@code moment}, outside quiet hours. */
	public Instant nextDay(Instant moment) {
		LocalDate tomorrow = moment.atZone(this.zone).toLocalDate().plusDays(1);
		return nextAllowed(tomorrow.atTime(SEND_TIME).atZone(this.zone).toInstant());
	}

	/** The given moment if it is outside quiet hours, otherwise the moment quiet hours end. */
	public Instant nextAllowed(Instant moment) {
		ZonedDateTime local = moment.atZone(this.zone);
		LocalTime time = local.toLocalTime();
		LocalDate date = local.toLocalDate();
		if (this.quietStart.isBefore(this.quietEnd)) {
			// Same-day window, e.g. 13:00 to 15:00.
			if (!time.isBefore(this.quietStart) && time.isBefore(this.quietEnd)) {
				return date.atTime(this.quietEnd).atZone(this.zone).toInstant();
			}
			return moment;
		}
		// Overnight window, e.g. 21:00 to 09:00.
		if (!time.isBefore(this.quietStart)) {
			return date.plusDays(1).atTime(this.quietEnd).atZone(this.zone).toInstant();
		}
		if (time.isBefore(this.quietEnd)) {
			return date.atTime(this.quietEnd).atZone(this.zone).toInstant();
		}
		return moment;
	}

}
