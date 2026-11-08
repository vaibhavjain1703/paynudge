package com.paynudge.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param allowedOrigins frontend origins, from the comma-separated {@code CORS_ALLOWED_ORIGINS}; each is exact or a
 * pattern with {@code *} (for example {@code https://paynudge-*-my-team.vercel.app} for Vercel previews)
 */
@ConfigurationProperties("paynudge.cors")
public record CorsProperties(List<String> allowedOrigins) {

	public CorsProperties {
		allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
	}

}
