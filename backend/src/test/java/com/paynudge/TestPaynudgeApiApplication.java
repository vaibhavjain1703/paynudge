package com.paynudge;

import org.springframework.boot.SpringApplication;

/** Runs the API locally against a throwaway Testcontainers Postgres instead of docker-compose. */
public class TestPaynudgeApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(PaynudgeApiApplication::main)
			.with(TestcontainersConfiguration.class)
			.withAdditionalProfiles("test")
			.run(args);
	}

}
