package com.paynudge.channel;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Brevo's transactional email API, used when {@code paynudge.mail.provider=brevo}.
 *
 * @param apiKey an API key from Brevo (SMTP &amp; API, API Keys), {@code BREVO_API_KEY}
 * @param baseUrl the API root
 */
@ConfigurationProperties("paynudge.brevo")
public record BrevoProperties(String apiKey, @DefaultValue("https://api.brevo.com") String baseUrl) {
}
