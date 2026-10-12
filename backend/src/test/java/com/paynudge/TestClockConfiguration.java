package com.paynudge;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the production system clock with a {@link MutableClock} in integration tests. */
@TestConfiguration(proxyBeanMethods = false)
class TestClockConfiguration {

	@Bean
	@Primary
	MutableClock testClock() {
		return new MutableClock();
	}

}
