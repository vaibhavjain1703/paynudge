package com.paynudge.auth;

import jakarta.validation.Valid;

import com.paynudge.auth.AuthDtos.AuthResult;
import com.paynudge.auth.AuthDtos.EmailCodeRequest;
import com.paynudge.auth.AuthDtos.EmailCodeSent;
import com.paynudge.auth.AuthDtos.GoogleSignInRequest;
import com.paynudge.auth.AuthDtos.ProvidersResponse;
import com.paynudge.auth.AuthDtos.VerifyEmailCodeRequest;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sign-in without a password: a code sent by email, or Google. Both sign in or sign up in one flow. */
@RestController
@RequestMapping("/api/auth")
class PasswordlessAuthController {

	private final EmailCodeService emailCodes;

	private final GoogleSignInService google;

	private final GoogleProperties googleProperties;

	PasswordlessAuthController(EmailCodeService emailCodes, GoogleSignInService google,
			GoogleProperties googleProperties) {
		this.emailCodes = emailCodes;
		this.google = google;
		this.googleProperties = googleProperties;
	}

	@GetMapping("/providers")
	ProvidersResponse providers() {
		return new ProvidersResponse(true, this.googleProperties.enabled() ? this.googleProperties.clientId() : null);
	}

	@PostMapping("/email/start")
	@ResponseStatus(HttpStatus.ACCEPTED)
	EmailCodeSent startEmail(@Valid @RequestBody EmailCodeRequest request) {
		return this.emailCodes.start(request.email());
	}

	@PostMapping("/email/verify")
	AuthResult verifyEmail(@Valid @RequestBody VerifyEmailCodeRequest request) {
		return this.emailCodes.verify(request.email(), request.code(), request.businessName());
	}

	@PostMapping("/google")
	AuthResult google(@Valid @RequestBody GoogleSignInRequest request) {
		return this.google.signIn(request.idToken(), request.businessName());
	}

}
