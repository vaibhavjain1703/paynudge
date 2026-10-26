package com.paynudge.channel;

import org.junit.jupiter.api.Test;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderChannelConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(ReminderChannelConfig.class, Properties.class);

	@Test
	void remindersGoToTheSmtpSandboxByDefault() {
		this.runner.run((context) -> assertThat(context).getBean(Channel.class).isInstanceOf(EmailChannel.class));
	}

	@Test
	void theOutboxKeepsRemindersWithoutEmailingAnyone() {
		this.runner.withPropertyValues("paynudge.reminders.delivery=outbox").run((context) -> {
			Channel channel = context.getBean(Channel.class);
			assertThat(channel).isInstanceOf(OutboxChannel.class);
			assertThat(channel.name()).as("stored like an email, so the timeline shows it the same way")
				.isEqualTo("EMAIL");
			assertThat(channel.send(new Channel.OutboundMessage("aarav@example.com", "Rent", "Hi", "due-1.remind_1",
					null)))
				.isNull();
		});
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(ReminderMailProperties.class)
	static class Properties {

	}

}
