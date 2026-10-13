package com.paynudge.channel;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Sends through the SMTP server in {@code spring.mail.*}: the local Mailpit inbox by default.
 * <p>
 * Jakarta Mail waits on the network while holding monitors, which on JDK 21 pins the carrier thread of a virtual
 * thread: a few parallel sends could stall every request in the app. So the SMTP work runs on a small pool of platform
 * threads and the caller (a virtual thread) just waits for it, which does not pin.
 */
class SmtpEmailSender implements EmailSender, DisposableBean {

	private static final int THREADS = 4;

	/** A backstop: the connect, read and write timeouts in {@code spring.mail.properties} fire long before this. */
	private static final Duration SEND_TIMEOUT = Duration.ofSeconds(30);

	private final JavaMailSender mailSender;

	private final MailSettings properties;

	private final ExecutorService executor = Executors.newFixedThreadPool(THREADS,
			Thread.ofPlatform().name("smtp-", 1).daemon(true).factory());

	SmtpEmailSender(JavaMailSender mailSender, MailSettings properties) {
		this.mailSender = mailSender;
		this.properties = properties;
	}

	@Override
	public void send(EmailMessage message) {
		Future<?> sending = this.executor.submit(() -> deliver(message));
		try {
			sending.get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (ExecutionException ex) {
			if (ex.getCause() instanceof EmailDeliveryException failure) {
				throw failure;
			}
			throw new EmailDeliveryException("SMTP delivery failed", ex.getCause());
		}
		catch (TimeoutException ex) {
			sending.cancel(true);
			throw new EmailDeliveryException("SMTP delivery timed out", ex);
		}
		catch (InterruptedException ex) {
			sending.cancel(true);
			Thread.currentThread().interrupt();
			throw new EmailDeliveryException("Interrupted while sending", ex);
		}
	}

	private void deliver(EmailMessage message) {
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

	@Override
	public void destroy() {
		this.executor.shutdownNow();
	}

}
