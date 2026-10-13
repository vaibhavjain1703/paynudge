package com.paynudge.channel;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.client.RestClient;

/** Picks the email transport from {@code paynudge.mail.provider} ({@code MAIL_PROVIDER}). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ MailSettings.class, BrevoProperties.class })
class EmailSenderConfig {

	@Bean
	EmailSender emailSender(MailSettings mail, BrevoProperties brevo, ObjectProvider<JavaMailSender> smtp) {
		return switch (mail.provider()) {
			case SMTP -> new SmtpEmailSender(smtp.getObject(), mail);
			case BREVO -> new BrevoEmailSender(brevo, mail, RestClient.builder().requestFactory(httpRequestFactory()));
		};
	}

	/** A slow provider must not hold a sign-in request for long; the owner can simply ask for the code again. */
	private static JdkClientHttpRequestFactory httpRequestFactory() {
		HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
		factory.setReadTimeout(Duration.ofSeconds(10));
		return factory;
	}

}
