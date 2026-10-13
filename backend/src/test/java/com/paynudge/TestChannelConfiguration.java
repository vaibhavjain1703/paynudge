package com.paynudge;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Customer reminders go to a {@link RecordingChannel} in tests. */
@TestConfiguration(proxyBeanMethods = false)
public class TestChannelConfiguration {

	@Bean
	@Primary
	RecordingChannel recordingChannel() {
		return new RecordingChannel();
	}

}
