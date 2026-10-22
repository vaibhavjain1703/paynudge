package com.paynudge;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Customer reminders go to a {@link RecordingChannel} in tests, and payment links to a {@link FakeRazorpay}. */
@TestConfiguration(proxyBeanMethods = false)
public class TestChannelConfiguration {

	@Bean
	@Primary
	FakeRazorpay fakeRazorpay() {
		return new FakeRazorpay();
	}

	@Bean
	@Primary
	RecordingChannel recordingChannel() {
		return new RecordingChannel();
	}

}
