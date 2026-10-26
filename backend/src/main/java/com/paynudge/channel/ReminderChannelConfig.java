package com.paynudge.channel;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Picks how customer reminders are delivered from {@code paynudge.reminders.delivery} ({@code REMINDER_DELIVERY}). */
@Configuration(proxyBeanMethods = false)
class ReminderChannelConfig {

	@Bean
	Channel reminderChannel(ReminderMailProperties properties) {
		return switch (properties.delivery()) {
			case SMTP -> new EmailChannel(properties);
			case OUTBOX -> new OutboxChannel();
		};
	}

}
