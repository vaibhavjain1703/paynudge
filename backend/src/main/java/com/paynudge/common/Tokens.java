package com.paynudge.common;

import java.security.SecureRandom;
import java.util.Base64;

/** Unguessable URL-safe tokens for links sent to customers (pay links, unsubscribe links). */
public final class Tokens {

	private static final SecureRandom RANDOM = new SecureRandom();

	private Tokens() {
	}

	/** 32 random bytes as 43 URL-safe characters. */
	public static String random() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
