package com.paynudge.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param clientId the Web OAuth client ID from Google Cloud ({@code GOOGLE_CLIENT_ID}); empty turns Google sign-in
 * off. It is public by design: the browser needs it to render the button.
 * @param jwkSetUri where Google publishes the keys that sign its ID tokens
 */
@ConfigurationProperties("paynudge.google")
public record GoogleProperties(String clientId,
		@DefaultValue("https://www.googleapis.com/oauth2/v3/certs") String jwkSetUri) {

	public boolean enabled() {
		return this.clientId != null && !this.clientId.isBlank();
	}

}
