package com.paynudge.channel;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where customer reminders go: never to real customer inboxes in the MVP. With {@code delivery=smtp} they go to an
 * SMTP sandbox, Mailpit locally (the defaults) or Mailtrap's sandbox (port 2525, which Render's free plan allows).
 * With {@code delivery=outbox} nothing is emailed and each reminder is only kept in PayNudge ({@code messages.body}).
 * Separate from {@code spring.mail.*}, which carries sign-in codes to real owners.
 */
@ConfigurationProperties("paynudge.reminders")
public record ReminderMailProperties(@DefaultValue("smtp") Delivery delivery, @DefaultValue("localhost") String host,
		@DefaultValue("1025") int port, String username, String password, @DefaultValue("false") boolean starttls,
		@DefaultValue("PayNudge reminders <reminders@paynudge.local>") String from) {

	public enum Delivery {

		SMTP, OUTBOX

	}

	boolean authenticated() {
		return this.username != null && !this.username.isBlank();
	}

}
