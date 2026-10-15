package com.paynudge.channel;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Sends through Brevo's transactional email API over HTTPS, so it works on hosts that block outbound SMTP (Render's
 * free plan). The From address must be a sender verified in Brevo.
 */
class BrevoEmailSender implements EmailSender {

	private static final int MAX_ERROR_BODY = 300;

	private final RestClient client;

	private final Contact sender;

	BrevoEmailSender(BrevoProperties brevo, MailSettings mail, RestClient.Builder builder) {
		if (brevo.apiKey() == null || brevo.apiKey().isBlank()) {
			throw new IllegalStateException("MAIL_PROVIDER=brevo needs BREVO_API_KEY");
		}
		this.sender = Contact.parse(mail.from());
		this.client = builder.baseUrl(brevo.baseUrl()).defaultHeader("api-key", brevo.apiKey()).build();
	}

	@Override
	public String send(EmailMessage message) {
		SendRequest request = new SendRequest(this.sender, List.of(new Contact(message.to(), null)),
				message.subject(), message.text(), message.html(),
				message.headers().isEmpty() ? null : message.headers());
		try {
			Sent sent = this.client.post()
				.uri("/v3/smtp/email")
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(Sent.class);
			return (sent != null) ? sent.messageId() : null;
		}
		catch (RestClientResponseException ex) {
			throw new EmailDeliveryException("Brevo rejected the email: " + ex.getStatusCode().value() + " "
					+ abbreviate(ex.getResponseBodyAsString()), ex, refusesTheEmail(ex.getStatusCode().value()));
		}
		catch (RestClientException ex) {
			throw new EmailDeliveryException("Brevo could not be reached", ex);
		}
	}

	/**
	 * 400 (an address Brevo will not take, say) fails the same way every time. A bad API key (401, 403), throttling
	 * (429) and Brevo's own errors (5xx) can clear up, so those are worth another try.
	 */
	private static boolean refusesTheEmail(int status) {
		return status >= 400 && status < 500 && status != 401 && status != 403 && status != 429;
	}

	private static String abbreviate(String body) {
		return (body.length() <= MAX_ERROR_BODY) ? body : body.substring(0, MAX_ERROR_BODY) + "...";
	}

	/** Brevo's answer to a send. */
	record Sent(String messageId) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record SendRequest(Contact sender, List<Contact> to, String subject, String textContent, String htmlContent,
			Map<String, String> headers) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record Contact(String email, String name) {

		static Contact parse(String address) {
			try {
				InternetAddress parsed = new InternetAddress(address, true);
				return new Contact(parsed.getAddress(), parsed.getPersonal());
			}
			catch (AddressException ex) {
				throw new IllegalStateException("MAIL_FROM is not a valid address: " + address, ex);
			}
		}

	}

}
