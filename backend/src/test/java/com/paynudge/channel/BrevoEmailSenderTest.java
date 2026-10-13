package com.paynudge.channel;

import java.io.IOException;

import com.paynudge.channel.MailSettings.Provider;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class BrevoEmailSenderTest {

	private static final BrevoProperties BREVO = new BrevoProperties("test-key", "https://brevo.test");

	private static final MailSettings MAIL = new MailSettings(Provider.BREVO, "PayNudge <codes@example.com>");

	private static final EmailMessage MESSAGE = new EmailMessage("owner@example.com", "Your PayNudge sign-in code",
			"Your code is 123456", "<p>Your code is <b>123456</b></p>");

	private final RestClient.Builder builder = RestClient.builder();

	private final MockRestServiceServer brevo = MockRestServiceServer.bindTo(this.builder).build();

	private final BrevoEmailSender sender = new BrevoEmailSender(BREVO, MAIL, this.builder);

	@Test
	void postsTheEmailToTheTransactionalApi() {
		this.brevo.expect(requestTo("https://brevo.test/v3/smtp/email"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("api-key", "test-key"))
			.andExpect(content().json("""
					{
					  "sender": {"email": "codes@example.com", "name": "PayNudge"},
					  "to": [{"email": "owner@example.com"}],
					  "subject": "Your PayNudge sign-in code",
					  "textContent": "Your code is 123456",
					  "htmlContent": "<p>Your code is <b>123456</b></p>"
					}
					""", JsonCompareMode.STRICT))
			.andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
				.body("{\"messageId\":\"<1@smtp-relay.mailin.fr>\"}"));

		this.sender.send(MESSAGE);

		this.brevo.verify();
	}

	@Test
	void aRejectedEmailFailsWithBrevosReason() {
		this.brevo.expect(requestTo("https://brevo.test/v3/smtp/email"))
			.andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
				.body("{\"code\":\"unauthorized\",\"message\":\"We have detected you are using an unrecognised IP address\"}"));

		assertThatThrownBy(() -> this.sender.send(MESSAGE)).isInstanceOf(EmailDeliveryException.class)
			.hasMessageContaining("401")
			.hasMessageContaining("unrecognised IP address");
	}

	@Test
	void anUnreachableApiFailsAsADeliveryError() {
		this.brevo.expect(requestTo("https://brevo.test/v3/smtp/email"))
			.andRespond(withException(new IOException("connect timed out")));

		assertThatThrownBy(() -> this.sender.send(MESSAGE)).isInstanceOf(EmailDeliveryException.class)
			.hasMessage("Brevo could not be reached");
	}

	@Test
	void startingWithoutAnApiKeyFailsFast() {
		assertThatIllegalStateException()
			.isThrownBy(() -> new BrevoEmailSender(new BrevoProperties(" ", "https://brevo.test"), MAIL,
					RestClient.builder()))
			.withMessageContaining("BREVO_API_KEY");
	}

	@Test
	void startingWithAMalformedSenderFailsFast() {
		assertThatIllegalStateException()
			.isThrownBy(() -> new BrevoEmailSender(BREVO, new MailSettings(Provider.BREVO, "PayNudge <not an address"),
					RestClient.builder()))
			.withMessageContaining("MAIL_FROM");
	}

}
