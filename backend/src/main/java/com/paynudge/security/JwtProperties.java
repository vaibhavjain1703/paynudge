package com.paynudge.security;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param secret base64 of at least 32 random bytes, from the {@code JWT_SECRET} environment variable
 * @param ttl how long an access token stays valid
 * @param issuer the {@code iss} claim written and required on every token
 */
@Validated
@ConfigurationProperties("paynudge.jwt")
public record JwtProperties(@NotBlank(message = "set JWT_SECRET (openssl rand -base64 48)") String secret,
		@NotNull Duration ttl, @DefaultValue("paynudge-api") String issuer) {
}
