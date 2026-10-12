package com.paynudge.auth;

import java.time.Clock;

import com.paynudge.common.ApiException;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleSignInServiceTest {

	@Test
	void withoutAClientIdGoogleSignInIsOffAndNoTokenIsTouched() {
		GoogleProperties unset = new GoogleProperties("", "https://www.googleapis.com/oauth2/v3/certs");
		GoogleSignInService service = new GoogleSignInService(unset, (token) -> {
			throw new AssertionError("decoder must not be called");
		}, null, Clock.systemUTC());

		assertThatThrownBy(() -> service.signIn("any-token", "Bright")).isInstanceOfSatisfying(ApiException.class,
				(ex) -> {
					assertThat(ex.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
					assertThat(ex.code()).isEqualTo("GOOGLE_SIGNIN_DISABLED");
				});
	}

}
