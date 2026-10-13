package com.paynudge.security;

import java.time.Clock;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** HS256 tokens signed and verified with one shared secret; expiry is checked against the application clock. */
@Configuration(proxyBeanMethods = false)
class JwtConfig {

	static final int MIN_KEY_BYTES = 32;

	@Bean
	SecretKey jwtSigningKey(JwtProperties properties) {
		return signingKey(properties.secret());
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return NimbusJwtEncoder.withSecretKey(jwtSigningKey).algorithm(MacAlgorithm.HS256).build();
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSigningKey, JwtProperties properties, Clock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		JwtTimestampValidator timestamps = new JwtTimestampValidator();
		timestamps.setClock(clock);
		timestamps.setAllowEmptyExpiryClaim(false);
		decoder.setJwtValidator(
				new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(properties.issuer())));
		return decoder;
	}

	static SecretKey signingKey(String base64Secret) {
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(base64Secret.strip());
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException("JWT_SECRET must be base64 (openssl rand -base64 48)", ex);
		}
		if (bytes.length < MIN_KEY_BYTES) {
			throw new IllegalStateException(
					"JWT_SECRET must decode to at least " + MIN_KEY_BYTES + " bytes; it has " + bytes.length);
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

}
