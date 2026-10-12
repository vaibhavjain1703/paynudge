package com.paynudge;

import org.springframework.boot.SpringApplication;

public class TestPaynudgeApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(PaynudgeApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
