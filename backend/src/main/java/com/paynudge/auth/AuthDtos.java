package com.paynudge.auth;

import java.time.Instant;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.paynudge.business.BusinessResponse;

/** Request and response bodies for the auth endpoints. Emails are normalized as they are read. */
final class AuthDtos {

	private AuthDtos() {
	}

	record SignupRequest(@NotBlank @Email @Size(max = 255) String email,
			@NotBlank @Size(min = 8, max = AuthService.MAX_PASSWORD_BYTES) String password,
			@NotBlank @Size(max = 200) String businessName) {

		SignupRequest {
			email = normalizeEmail(email);
			businessName = strip(businessName);
		}

	}

	record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {

		LoginRequest {
			email = normalizeEmail(email);
		}

	}

	record EmailCodeRequest(@NotBlank @Email @Size(max = 255) String email) {

		EmailCodeRequest {
			email = normalizeEmail(email);
		}

	}

	record EmailCodeSent(long expiresInSeconds, long resendAfterSeconds) {
	}

	/** {@code businessName} is only read when the email has no account yet. */
	record VerifyEmailCodeRequest(@NotBlank @Email @Size(max = 255) String email,
			@NotBlank @Pattern(regexp = "\\d{6}", message = "must be the 6-digit code") String code,
			@Size(max = 200) String businessName) {

		VerifyEmailCodeRequest {
			email = normalizeEmail(email);
			code = (code == null) ? null : code.replaceAll("\\s", "");
			businessName = strip(businessName);
		}

	}

	/** {@code idToken} is the credential Google Identity Services hands the browser. */
	record GoogleSignInRequest(@NotBlank @Size(max = 4096) String idToken, @Size(max = 200) String businessName) {

		GoogleSignInRequest {
			businessName = strip(businessName);
		}

	}

	/** Which sign-in methods the web app should offer. {@code googleClientId} is public by design. */
	record ProvidersResponse(boolean emailCode, String googleClientId) {
	}

	/**
	 * The outcome of every sign-in route. {@code SIGNED_IN} carries the token; {@code NEEDS_BUSINESS_NAME} means the
	 * email was proven but has no account yet, so the client asks for a business name and repeats the call with it.
	 */
	@JsonInclude(JsonInclude.Include.NON_NULL)
	record AuthResult(Status status, String token, Instant expiresAt, MeResponse me, String email) {

		enum Status {

			SIGNED_IN, NEEDS_BUSINESS_NAME

		}

		static AuthResult signedIn(String token, Instant expiresAt, MeResponse me) {
			return new AuthResult(Status.SIGNED_IN, token, expiresAt, me, null);
		}

		static AuthResult needsBusinessName(String email) {
			return new AuthResult(Status.NEEDS_BUSINESS_NAME, null, null, null, email);
		}

	}

	record MeResponse(long userId, String email, BusinessResponse business) {
	}

	static String normalizeEmail(String email) {
		return (email == null) ? null : email.strip().toLowerCase(Locale.ROOT);
	}

	private static String strip(String value) {
		return (value == null) ? null : value.strip();
	}

}
