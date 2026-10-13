package com.paynudge.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import com.paynudge.auth.AuthDtos.AuthResult;
import com.paynudge.auth.AuthDtos.EmailCodeSent;
import com.paynudge.channel.EmailDeliveryException;
import com.paynudge.channel.EmailSender;
import com.paynudge.common.ApiException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Passwordless sign-in: email a six-digit code, then trade it for a session. The same flow signs in an existing
 * owner or, given a business name, creates a new one.
 */
@Service
class EmailCodeService {

	private static final Logger log = LoggerFactory.getLogger(EmailCodeService.class);

	static final Duration CODE_TTL = Duration.ofMinutes(10);

	static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

	static final int MAX_CODES_PER_HOUR = 5;

	static final int MAX_WRONG_GUESSES = 5;

	static final String CODE_RECENTLY_SENT = "CODE_RECENTLY_SENT";

	static final String TOO_MANY_CODES = "TOO_MANY_CODES";

	static final String INVALID_CODE = "INVALID_CODE";

	static final String EMAIL_UNAVAILABLE = "EMAIL_UNAVAILABLE";

	private final LoginCodeRepository codes;

	private final LoginCodeHasher hasher;

	private final EmailSender emailSender;

	private final OwnerAccounts accounts;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	private final SecureRandom random = new SecureRandom();

	EmailCodeService(LoginCodeRepository codes, LoginCodeHasher hasher, EmailSender emailSender,
			OwnerAccounts accounts, JdbcTemplate jdbc, Clock clock) {
		this.codes = codes;
		this.hasher = hasher;
		this.emailSender = emailSender;
		this.accounts = accounts;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** Issues and emails a new code. If the email cannot be sent, nothing is stored. */
	@Transactional
	EmailCodeSent start(String email) {
		lockEmail(email);
		Instant now = this.clock.instant();
		this.codes.findFirstByEmailOrderByCreatedAtDesc(email).ifPresent((latest) -> {
			Instant nextAllowed = latest.getCreatedAt().plus(RESEND_COOLDOWN);
			if (now.isBefore(nextAllowed)) {
				long wait = Math.max(1, Duration.between(now, nextAllowed).toSeconds());
				throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, CODE_RECENTLY_SENT,
						"A code was just sent. You can ask for another in " + wait + " seconds.",
						Map.of("retryAfterSeconds", wait));
			}
		});
		if (this.codes.countByEmailAndCreatedAtAfter(email, now.minus(Duration.ofHours(1))) >= MAX_CODES_PER_HOUR) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, TOO_MANY_CODES,
					"Too many codes requested for this email. Try again in an hour.");
		}

		String code = "%06d".formatted(this.random.nextInt(1_000_000));
		this.codes.save(new LoginCode(email, this.hasher.hash(email, code), now.plus(CODE_TTL), now));
		try {
			this.emailSender.send(LoginCodeEmail.render(email, code, CODE_TTL));
		}
		catch (EmailDeliveryException ex) {
			log.warn("Could not send a sign-in code", ex);
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, EMAIL_UNAVAILABLE,
					"We couldn't send the email right now. Please try again in a minute.");
		}
		return new EmailCodeSent(CODE_TTL.toSeconds(), RESEND_COOLDOWN.toSeconds());
	}

	/**
	 * Checks the newest code for the email. A wrong guess is recorded even though the request fails, which is why
	 * {@link InvalidCodeException} (and only that) does not roll the transaction back.
	 */
	@Transactional(noRollbackFor = InvalidCodeException.class)
	AuthResult verify(String email, String code, String businessName) {
		lockEmail(email);
		Instant now = this.clock.instant();
		LoginCode latest = this.codes.findFirstByEmailOrderByCreatedAtDesc(email)
			.filter((candidate) -> candidate.isUsable(now, MAX_WRONG_GUESSES))
			.orElseThrow(EmailCodeService::invalidCode);
		if (!this.hasher.matches(email, code, latest.getCodeHash())) {
			latest.recordWrongGuess();
			throw invalidCode();
		}
		return this.accounts.signInOrSignUp(email, businessName, () -> latest.consume(now));
	}

	/** Serializes start and verify per email, so parallel requests cannot skip the cooldown or the guess limit. */
	private void lockEmail(String email) {
		this.jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))", (rs) -> null, "login-code:" + email);
	}

	private static InvalidCodeException invalidCode() {
		return new InvalidCodeException();
	}

	static final class InvalidCodeException extends ApiException {

		InvalidCodeException() {
			super(HttpStatus.BAD_REQUEST, INVALID_CODE,
					"That code is wrong or has expired. Check the latest email or ask for a new code.");
		}

	}

}
