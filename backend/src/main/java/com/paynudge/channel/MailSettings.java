package com.paynudge.channel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param provider how email leaves the API: {@code smtp} ({@code spring.mail.*}, the local Mailpit inbox by default)
 * or {@code brevo} (real inboxes, over HTTPS)
 * @param from the From header, e.g. {@code PayNudge <no-reply@example.com>}; with Brevo it must be a verified sender
 */
@Validated
@ConfigurationProperties("paynudge.mail")
public record MailSettings(@NotNull @DefaultValue("smtp") Provider provider, @NotBlank String from) {

	public enum Provider {

		SMTP, BREVO

	}

}
