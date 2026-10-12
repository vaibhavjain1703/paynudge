package com.paynudge.channel;

import java.nio.charset.StandardCharsets;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** Sends through the SMTP server in {@code spring.mail.*}: the local Mailpit inbox by default. */
@Component
class SmtpEmailSender implements EmailSender {

	private final JavaMailSender mailSender;

	private final MailSettings properties;

	SmtpEmailSender(JavaMailSender mailSender, MailSettings properties) {
		this.mailSender = mailSender;
		this.properties = properties;
	}

	@Override
	public void send(EmailMessage message) {
		try {
			MimeMessage mime = this.mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mime, message.html() != null,
					StandardCharsets.UTF_8.name());
			helper.setFrom(this.properties.from());
			helper.setTo(message.to());
			helper.setSubject(message.subject());
			if (message.html() != null) {
				helper.setText(message.text(), message.html());
			}
			else {
				helper.setText(message.text());
			}
			this.mailSender.send(mime);
		}
		catch (MailException | MessagingException ex) {
			throw new EmailDeliveryException("SMTP delivery failed", ex);
		}
	}

}
