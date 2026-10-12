package com.paynudge.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import com.paynudge.auth.AuthDtos.AuthResult;
import com.paynudge.auth.AuthDtos.MeResponse;
import com.paynudge.business.Business;
import com.paynudge.business.BusinessRepository;
import com.paynudge.business.BusinessResponse;
import com.paynudge.common.ApiException;
import com.paynudge.common.ErrorCodes;
import com.paynudge.security.Caller;
import com.paynudge.security.TokenService;
import com.paynudge.security.TokenService.IssuedToken;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Owner accounts, whichever way someone signs in. One email is one account: a password, an email code and Google
 * all lead to the same owner and business.
 */
@Service
class OwnerAccounts {

	static final String EMAIL_TAKEN = "EMAIL_TAKEN";

	private final UserAccountRepository users;

	private final BusinessRepository businesses;

	private final TokenService tokens;

	private final Clock clock;

	OwnerAccounts(UserAccountRepository users, BusinessRepository businesses, TokenService tokens, Clock clock) {
		this.users = users;
		this.businesses = businesses;
		this.tokens = tokens;
		this.clock = clock;
	}

	Optional<UserAccount> findByEmail(String email) {
		return this.users.findByEmail(email);
	}

	/**
	 * Creates the owner and their business and signs them in. {@code passwordHash} is null for passwordless owners.
	 * Callers run this inside their transaction.
	 */
	AuthResult create(String email, String passwordHash, String businessName) {
		if (this.users.existsByEmail(email)) {
			throw emailTaken();
		}
		Instant now = this.clock.instant();
		UserAccount user;
		try {
			user = this.users.saveAndFlush(new UserAccount(email, passwordHash, now));
		}
		catch (DataIntegrityViolationException ex) {
			// lost a race with a concurrent sign-up for the same email
			throw emailTaken();
		}
		Business business = this.businesses.save(Business.createFor(user.getId(), businessName, now));
		return signedIn(user, business);
	}

	AuthResult signIn(UserAccount user) {
		Business business = this.businesses.findByOwnerId(user.getId())
			.orElseThrow(() -> new IllegalStateException("Owner " + user.getId() + " has no business"));
		return signedIn(user, business);
	}

	/**
	 * For routes that have already proven the caller owns {@code email} (email code, Google): signs in a known owner,
	 * or creates the account once a business name is supplied. {@code onAccepted} runs only when the proof is used
	 * up, i.e. not when we still need the business name.
	 */
	AuthResult signInOrSignUp(String email, String businessName, Runnable onAccepted) {
		Optional<UserAccount> existing = this.users.findByEmail(email);
		if (existing.isPresent()) {
			onAccepted.run();
			return signIn(existing.get());
		}
		if (businessName == null || businessName.isBlank()) {
			return AuthResult.needsBusinessName(email);
		}
		onAccepted.run();
		return create(email, null, businessName);
	}

	MeResponse me(Caller caller) {
		UserAccount user = this.users.findById(caller.userId()).orElseThrow(OwnerAccounts::accountGone);
		Business business = this.businesses.findByIdAndOwnerId(caller.businessId(), caller.userId())
			.orElseThrow(OwnerAccounts::accountGone);
		return toMe(user, business);
	}

	private AuthResult signedIn(UserAccount user, Business business) {
		IssuedToken token = this.tokens.issue(user.getId(), business.getId());
		return AuthResult.signedIn(token.value(), token.expiresAt(), toMe(user, business));
	}

	private static MeResponse toMe(UserAccount user, Business business) {
		return new MeResponse(user.getId(), user.getEmail(), BusinessResponse.from(business));
	}

	private static ApiException emailTaken() {
		return ApiException.conflict(EMAIL_TAKEN, "An account with this email already exists");
	}

	private static ApiException accountGone() {
		return ApiException.unauthorized(ErrorCodes.UNAUTHORIZED, "This account no longer exists");
	}

}
