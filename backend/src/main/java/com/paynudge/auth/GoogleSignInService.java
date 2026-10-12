package com.paynudge.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.paynudge.auth.AuthDtos.AuthResult;
import com.paynudge.common.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Continue with Google": the browser gets an ID token from Google Identity Services and we verify it here. A
 * verified Google email signs into the account with that email, or creates one once a business name is given.
 */
@Service
class GoogleSignInService {

	static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

	static final Duration CLOCK_SKEW = Duration.ofSeconds(60);

	static final String GOOGLE_SIGNIN_DISABLED = "GOOGLE_SIGNIN_DISABLED";

	static final String INVALID_GOOGLE_TOKEN = "INVALID_GOOGLE_TOKEN";

	private final GoogleProperties properties;

	private final GoogleTokenDecoder decoder;

	private final OwnerAccounts accounts;

	private final Clock clock;

	GoogleSignInService(GoogleProperties properties, GoogleTokenDecoder decoder, OwnerAccounts accounts,
			Clock clock) {
		this.properties = properties;
		this.decoder = decoder;
		this.accounts = accounts;
		this.clock = clock;
	}

	@Transactional
	AuthResult signIn(String idToken, String businessName) {
		return this.accounts.signInOrSignUp(verifiedEmail(idToken), businessName, () -> {
		});
	}

	private String verifiedEmail(String idToken) {
		if (!this.properties.enabled()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, GOOGLE_SIGNIN_DISABLED,
					"Google sign-in is not set up on this server");
		}
		Jwt jwt;
		try {
			jwt = this.decoder.decode(idToken);
		}
		catch (JwtException ex) {
			throw invalidToken();
		}
		Instant now = this.clock.instant();
		List<String> audience = jwt.getAudience();
		Instant expiresAt = jwt.getExpiresAt();
		String email = AuthDtos.normalizeEmail(jwt.getClaimAsString("email"));
		boolean valid = ISSUERS.contains(jwt.getClaimAsString("iss")) && audience != null
				&& audience.contains(this.properties.clientId()) && expiresAt != null
				&& now.isBefore(expiresAt.plus(CLOCK_SKEW)) && isTrue(jwt.getClaim("email_verified"))
				&& email != null && !email.isBlank();
		if (!valid) {
			throw invalidToken();
		}
		return email;
	}

	private static boolean isTrue(Object claim) {
		return Boolean.TRUE.equals(claim) || "true".equals(claim);
	}

	private static ApiException invalidToken() {
		return ApiException.unauthorized(INVALID_GOOGLE_TOKEN, "Google sign-in failed. Please try again.");
	}

}
