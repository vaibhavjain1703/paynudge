package com.paynudge.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import com.paynudge.auth.AuthDtos.AuthResponse;
import com.paynudge.auth.AuthDtos.LoginRequest;
import com.paynudge.auth.AuthDtos.MeResponse;
import com.paynudge.auth.AuthDtos.SignupRequest;
import com.paynudge.business.Business;
import com.paynudge.business.BusinessRepository;
import com.paynudge.business.BusinessResponse;
import com.paynudge.common.ApiException;
import com.paynudge.common.ErrorCodes;
import com.paynudge.security.Caller;
import com.paynudge.security.TokenService;
import com.paynudge.security.TokenService.IssuedToken;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthService {

	/** BCrypt ignores everything past 72 bytes, so longer passwords are refused rather than silently truncated. */
	static final int MAX_PASSWORD_BYTES = 72;

	static final String EMAIL_TAKEN = "EMAIL_TAKEN";

	static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";

	private final UserAccountRepository users;

	private final BusinessRepository businesses;

	private final PasswordEncoder passwordEncoder;

	private final TokenService tokens;

	private final Clock clock;

	/** Checked when the email is unknown so both login failures take the same time. */
	private final String unknownUserHash;

	AuthService(UserAccountRepository users, BusinessRepository businesses, PasswordEncoder passwordEncoder,
			TokenService tokens, Clock clock) {
		this.users = users;
		this.businesses = businesses;
		this.passwordEncoder = passwordEncoder;
		this.tokens = tokens;
		this.clock = clock;
		this.unknownUserHash = passwordEncoder.encode("unknown-user-timing-equalizer");
	}

	@Transactional
	AuthResponse signup(SignupRequest request) {
		if (request.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
			throw ApiException.invalidField("password", "must be at most " + MAX_PASSWORD_BYTES + " bytes");
		}
		if (this.users.existsByEmail(request.email())) {
			throw emailTaken();
		}
		Instant now = this.clock.instant();
		UserAccount user;
		try {
			user = this.users
				.saveAndFlush(new UserAccount(request.email(), this.passwordEncoder.encode(request.password()), now));
		}
		catch (DataIntegrityViolationException ex) {
			// lost a race with a concurrent signup for the same email
			throw emailTaken();
		}
		Business business = this.businesses.save(Business.createFor(user.getId(), request.businessName(), now));
		return respond(user, business);
	}

	@Transactional(readOnly = true)
	AuthResponse login(LoginRequest request) {
		Optional<UserAccount> user = this.users.findByEmail(request.email());
		boolean tooLong = request.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES;
		String hash = user.map(UserAccount::getPasswordHash).orElse(this.unknownUserHash);
		boolean matches = !tooLong && this.passwordEncoder.matches(request.password(), hash);
		if (user.isEmpty() || !matches) {
			throw invalidCredentials();
		}
		Business business = this.businesses.findByOwnerId(user.get().getId()).orElseThrow(this::invalidCredentials);
		return respond(user.get(), business);
	}

	@Transactional(readOnly = true)
	MeResponse me(Caller caller) {
		UserAccount user = this.users.findById(caller.userId()).orElseThrow(this::accountGone);
		Business business = this.businesses.findByIdAndOwnerId(caller.businessId(), caller.userId())
			.orElseThrow(this::accountGone);
		return toMe(user, business);
	}

	private AuthResponse respond(UserAccount user, Business business) {
		IssuedToken token = this.tokens.issue(user.getId(), business.getId());
		return new AuthResponse(token.value(), token.expiresAt(), toMe(user, business));
	}

	private static MeResponse toMe(UserAccount user, Business business) {
		return new MeResponse(user.getId(), user.getEmail(), BusinessResponse.from(business));
	}

	private ApiException emailTaken() {
		return ApiException.conflict(EMAIL_TAKEN, "An account with this email already exists");
	}

	private ApiException invalidCredentials() {
		return new ApiException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS, "Email or password is incorrect");
	}

	private ApiException accountGone() {
		return ApiException.unauthorized(ErrorCodes.UNAUTHORIZED, "This account no longer exists");
	}

}
