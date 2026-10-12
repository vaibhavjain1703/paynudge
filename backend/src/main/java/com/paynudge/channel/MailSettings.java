package com.paynudge.channel;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** @param from the From header, e.g. {@code PayNudge <no-reply@example.com>} */
@Validated
@ConfigurationProperties("paynudge.mail")
public record MailSettings(@NotBlank String from) {
}
