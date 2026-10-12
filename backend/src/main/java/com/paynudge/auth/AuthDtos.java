package com.paynudge.auth;

import java.time.Instant;
import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.paynudge.business.BusinessResponse;

/** Request and response bodies for {@link AuthController}. Emails are normalized as they are read. */
final class AuthDtos {

	private AuthDtos() {
	}

	record SignupRequest(@NotBlank @Email @Size(max = 255) String email,
			@NotBlank @Size(min = 8, max = AuthService.MAX_PASSWORD_BYTES) String password,
			@NotBlank @Size(max = 200) String businessName) {

		SignupRequest {
			email = normalizeEmail(email);
			businessName = (businessName == null) ? null : businessName.strip();
		}

	}

	record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {

		LoginRequest {
			email = normalizeEmail(email);
		}

	}

	record AuthResponse(String token, Instant expiresAt, MeResponse me) {
	}

	record MeResponse(long userId, String email, BusinessResponse business) {
	}

	private static String normalizeEmail(String email) {
		return (email == null) ? null : email.strip().toLowerCase(Locale.ROOT);
	}

}
