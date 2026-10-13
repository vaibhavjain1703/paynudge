package com.paynudge.security;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class JwtConfigTest {

	@Test
	void acceptsABase64SecretOfAtLeast32Bytes() {
		String secret = Base64.getEncoder().encodeToString(new byte[32]);

		assertThat(JwtConfig.signingKey(secret).getEncoded()).hasSize(32);
	}

	@Test
	void refusesAShortSecret() {
		String secret = Base64.getEncoder().encodeToString(new byte[31]);

		assertThatIllegalStateException().isThrownBy(() -> JwtConfig.signingKey(secret))
			.withMessageContaining("at least 32 bytes");
	}

	@Test
	void refusesASecretThatIsNotBase64() {
		assertThatIllegalStateException().isThrownBy(() -> JwtConfig.signingKey("not base64 !!"))
			.withMessageContaining("must be base64");
	}

}
