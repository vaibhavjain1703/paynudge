package com.paynudge.security;

import com.paynudge.common.ApiException;
import com.paynudge.common.ErrorCodes;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The authenticated owner making a request. Declare it as a controller parameter to receive it; every query a
 * controller runs must be scoped by {@link #businessId()}.
 */
public record Caller(long userId, long businessId) {

	static final String BUSINESS_CLAIM = "bid";

	static Caller from(Jwt jwt) {
		try {
			long userId = Long.parseLong(jwt.getSubject());
			Object businessId = jwt.getClaim(BUSINESS_CLAIM);
			if (businessId instanceof Number number) {
				return new Caller(userId, number.longValue());
			}
		}
		catch (NumberFormatException ex) {
			// fall through: a validly signed token we did not issue in this shape
		}
		throw ApiException.unauthorized(ErrorCodes.UNAUTHORIZED, "Invalid or expired token");
	}

}
