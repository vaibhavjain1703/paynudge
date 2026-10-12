package com.paynudge.auth;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.paynudge.auth.AuthDtos.AuthResult;
import com.paynudge.auth.AuthDtos.LoginRequest;
import com.paynudge.auth.AuthDtos.MeResponse;
import com.paynudge.auth.AuthDtos.SignupRequest;
import com.paynudge.common.ApiException;
import com.paynudge.security.Caller;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Email-and-password sign-up and sign-in. */
@Service
class AuthService {

	/** BCrypt ignores everything past 72 bytes, so longer passwords are refused rather than silently truncated. */
	static final int MAX_PASSWORD_BYTES = 72;

	static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";

	private final OwnerAccounts accounts;

	private final PasswordEncoder passwordEncoder;

	/** Checked when there is no password to compare, so every failed login takes the same time. */
	private final String unknownUserHash;

	AuthService(OwnerAccounts accounts, PasswordEncoder passwordEncoder) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
		this.unknownUserHash = passwordEncoder.encode("unknown-user-timing-equalizer");
	}

	@Transactional
	AuthResult signup(SignupRequest request) {
		if (request.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
			throw ApiException.invalidField("password", "must be at most " + MAX_PASSWORD_BYTES + " bytes");
		}
		return this.accounts.create(request.email(), this.passwordEncoder.encode(request.password()),
				request.businessName());
	}

	/** Owners who only ever used an email code or Google have no password, and fail here like a wrong password. */
	@Transactional(readOnly = true)
	AuthResult login(LoginRequest request) {
		Optional<UserAccount> user = this.accounts.findByEmail(request.email());
		String hash = user.map(UserAccount::getPasswordHash).orElse(null);
		boolean tooLong = request.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
		boolean matches = !tooLong
				&& this.passwordEncoder.matches(request.password(), (hash != null) ? hash : this.unknownUserHash);
		if (hash == null || !matches) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS, "Email or password is incorrect");
		}
		return this.accounts.signIn(user.get());
	}

	@Transactional(readOnly = true)
	MeResponse me(Caller caller) {
		return this.accounts.me(caller);
	}

}
