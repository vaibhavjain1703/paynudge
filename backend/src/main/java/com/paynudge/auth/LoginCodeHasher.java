package com.paynudge.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * Hashes sign-in codes with HMAC-SHA256 under a key derived from the server's JWT secret. A six-digit code has only
 * a million values, so a plain hash would fall to brute force if the table leaked; the server-held key prevents
 * that.
 */
@Component
class LoginCodeHasher {

	private static final String ALGORITHM = "HmacSHA256";

	private final SecretKeySpec key;

	LoginCodeHasher(SecretKey jwtSigningKey) {
		this.key = new SecretKeySpec(hmac(jwtSigningKey, "paynudge/login-code/v1"), ALGORITHM);
	}

	String hash(String email, String code) {
		return HexFormat.of().formatHex(hmac(this.key, email + "\n" + code));
	}

	boolean matches(String email, String code, String expectedHash) {
		return MessageDigest.isEqual(hash(email, code).getBytes(StandardCharsets.US_ASCII),
				expectedHash.getBytes(StandardCharsets.US_ASCII));
	}

	private static byte[] hmac(SecretKey key, String value) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA256 unavailable", ex);
		}
	}

}
