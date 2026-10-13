package com.paynudge.channel;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;

class EmailSenderConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(EmailSenderConfig.class)
		.withBean(JavaMailSender.class, JavaMailSenderImpl::new)
		.withPropertyValues("paynudge.mail.from=PayNudge <codes@example.com>");

	@Test
	void smtpIsTheDefault() {
		this.runner.run((context) -> assertThat(context).getBean(EmailSender.class)
			.isInstanceOf(SmtpEmailSender.class));
	}

	@Test
	void brevoIsUsedWhenSelected() {
		this.runner.withPropertyValues("paynudge.mail.provider=brevo", "paynudge.brevo.api-key=test-key")
			.run((context) -> assertThat(context).getBean(EmailSender.class).isInstanceOf(BrevoEmailSender.class));
	}

	@Test
	void brevoWithoutAnApiKeyStopsStartup() {
		this.runner.withPropertyValues("paynudge.mail.provider=brevo")
			.run((context) -> assertThat(context).getFailure().hasStackTraceContaining("BREVO_API_KEY"));
	}

	@Test
	void anUnknownProviderStopsStartup() {
		this.runner.withPropertyValues("paynudge.mail.provider=gmail")
			.run((context) -> assertThat(context).getFailure().hasStackTraceContaining("paynudge.mail.provider"));
	}

}
