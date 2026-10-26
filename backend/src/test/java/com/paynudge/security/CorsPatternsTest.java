package com.paynudge.security;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class CorsPatternsTest {

	private final CorsConfiguration cors = SecurityConfig.cors(new CorsProperties(
			List.of("https://getpaynudge.vercel.app", "https://paynudge-*-vaibhav-jains-projects-d3437368.vercel.app")));

	@Test
	void theLiveSiteAndItsPreviewsMayCallTheApi() {
		assertThat(this.cors.checkOrigin("https://getpaynudge.vercel.app")).isEqualTo("https://getpaynudge.vercel.app");
		assertThat(this.cors.checkOrigin("https://paynudge-a1b2c3d4e-vaibhav-jains-projects-d3437368.vercel.app"))
			.isNotNull();
		assertThat(this.cors.checkOrigin("https://paynudge-git-day9-vaibhav-jains-projects-d3437368.vercel.app"))
			.isNotNull();
	}

	@Test
	void otherSitesMayNot() {
		assertThat(this.cors.checkOrigin("https://evil.example")).isNull();
		assertThat(this.cors.checkOrigin("https://paynudge-x-someone-else.vercel.app")).isNull();
		assertThat(this.cors.checkOrigin("http://getpaynudge.vercel.app")).as("plain http").isNull();
	}

}
