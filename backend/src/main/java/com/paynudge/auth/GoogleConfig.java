package com.paynudge.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration(proxyBeanMethods = false)
class GoogleConfig {

	/** Fetches Google's signing keys lazily on first use and caches them; startup needs no network. */
	@Bean
	GoogleTokenDecoder googleTokenDecoder(GoogleProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri())
			.jwsAlgorithm(SignatureAlgorithm.RS256)
			.build();
		decoder.setJwtValidator((jwt) -> OAuth2TokenValidatorResult.success());
		return decoder::decode;
	}

}
