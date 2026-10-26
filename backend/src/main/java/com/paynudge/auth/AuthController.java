package com.paynudge.auth;

import jakarta.validation.Valid;

import com.paynudge.auth.AuthDtos.AuthResult;
import com.paynudge.auth.AuthDtos.LoginRequest;
import com.paynudge.auth.AuthDtos.MeResponse;
import com.paynudge.auth.AuthDtos.SignupRequest;
import com.paynudge.security.Caller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class AuthController {

	private final AuthService auth;

	AuthController(AuthService auth) {
		this.auth = auth;
	}

	/** Creates the owner's login and their business, and signs them in. */
	@PostMapping("/auth/signup")
	@ResponseStatus(HttpStatus.CREATED)
	AuthResult signup(@Valid @RequestBody SignupRequest request) {
		return this.auth.signup(request);
	}

	@PostMapping("/auth/login")
	AuthResult login(@Valid @RequestBody LoginRequest request) {
		return this.auth.login(request);
	}

	/** "Try the demo": a new demo business with sample data, signed in. Rate limited per address. */
	@PostMapping("/auth/demo")
	@ResponseStatus(HttpStatus.CREATED)
	AuthResult demo() {
		return this.auth.demo();
	}

	@GetMapping("/me")
	MeResponse me(Caller caller) {
		return this.auth.me(caller);
	}

}
