package com.paynudge;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import com.paynudge.auth.GoogleTokenDecoder;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Stands in for Google: ID tokens are signed with a key generated for the test run, and the app's decoder trusts
 * that key instead of fetching Google's. Also records outgoing email instead of sending it.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestGoogleConfiguration {

	public static final String CLIENT_ID = "test-client.apps.googleusercontent.com";

	private static final KeyPair GOOGLE_KEYS = rsaKeyPair();

	@Bean
	@Primary
	GoogleTokenDecoder testGoogleTokenDecoder() {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) GOOGLE_KEYS.getPublic())
			.signatureAlgorithm(SignatureAlgorithm.RS256)
			.build();
		decoder.setJwtValidator((jwt) -> OAuth2TokenValidatorResult.success());
		return decoder::decode;
	}

	@Bean
	GoogleIdTokens googleIdTokens(MutableClock clock) {
		return new GoogleIdTokens(GOOGLE_KEYS, clock);
	}

	@Bean
	@Primary
	RecordingEmailSender recordingEmailSender() {
		return new RecordingEmailSender();
	}

	private static KeyPair rsaKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** Mints Google-style ID tokens. Defaults describe a valid token; tests override single claims. */
	public static final class GoogleIdTokens {

		private final JwtEncoder encoder;

		private final MutableClock clock;

		GoogleIdTokens(KeyPair keys, MutableClock clock) {
			this.encoder = NimbusJwtEncoder
				.withKeyPair((RSAPublicKey) keys.getPublic(), (RSAPrivateKey) keys.getPrivate())
				.build();
			this.clock = clock;
		}

		public String forEmail(String email) {
			return forEmail(email, (claims) -> {
			});
		}

		public String forEmail(String email, Consumer<JwtClaimsSet.Builder> overrides) {
			Instant now = this.clock.instant();
			JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
				.issuer("https://accounts.google.com")
				.audience(List.of(CLIENT_ID))
				.subject("1098765432101234567890")
				.issuedAt(now)
				.expiresAt(now.plus(Duration.ofHours(1)))
				.claim("email", email)
				.claim("email_verified", true);
			overrides.accept(claims);
			JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
			return this.encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
		}

	}

}
