package com.paynudge.security;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param allowedOrigins exact frontend origins, from the comma-separated {@code CORS_ALLOWED_ORIGINS} */
@ConfigurationProperties("paynudge.cors")
public record CorsProperties(List<String> allowedOrigins) {

	public CorsProperties {
		allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
	}

}
