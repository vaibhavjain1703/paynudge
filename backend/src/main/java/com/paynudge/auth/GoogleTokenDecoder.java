package com.paynudge.auth;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Verifies the signature of a Google ID token and parses it. Claim checks (issuer, audience, expiry, verified email)
 * happen in {@link GoogleSignInService} against the application clock. Tests replace this with a decoder that
 * trusts a local key.
 */
@FunctionalInterface
public interface GoogleTokenDecoder {

	Jwt decode(String token) throws JwtException;

}
