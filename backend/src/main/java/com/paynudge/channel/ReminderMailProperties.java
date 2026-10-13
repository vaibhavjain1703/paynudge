package com.paynudge.channel;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where customer reminders are delivered: an SMTP sandbox, never real customer inboxes in the MVP. Mailpit locally
 * (the defaults), Mailtrap's sandbox in production (port 2525, which Render's free plan allows). Separate from
 * {@code spring.mail.*}, which carries sign-in codes to real owners.
 */
@ConfigurationProperties("paynudge.reminders")
public record ReminderMailProperties(@DefaultValue("localhost") String host, @DefaultValue("1025") int port,
		String username, String password, @DefaultValue("false") boolean starttls,
		@DefaultValue("PayNudge reminders <reminders@paynudge.local>") String from) {

	boolean authenticated() {
		return this.username != null && !this.username.isBlank();
	}

}
