package com.paynudge.channel;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmtpEmailSenderTest {

	private static final MailSettings MAIL = new MailSettings(MailSettings.Provider.SMTP, "PayNudge <codes@example.com>");

	private static final EmailMessage MESSAGE = new EmailMessage("owner@example.com", "Your PayNudge sign-in code",
			"Your code is 123456", null);

	private final RecordingMailSender smtp = new RecordingMailSender();

	private final SmtpEmailSender sender = new SmtpEmailSender(this.smtp, MAIL);

	@AfterEach
	void stop() {
		this.sender.destroy();
	}

	@Test
	void aSendFromAVirtualThreadRunsOnAPlatformThread() throws Exception {
		Thread caller = Thread.ofVirtual().start(() -> this.sender.send(MESSAGE));
		caller.join();

		assertThat(this.smtp.thread.get()).isNotNull();
		assertThat(this.smtp.thread.get().isVirtual()).isFalse();
		assertThat(this.smtp.thread.get().getName()).startsWith("smtp-");
		assertThat(this.smtp.sent.get().getSubject()).isEqualTo("Your PayNudge sign-in code");
		assertThat(this.smtp.sent.get().getFrom()[0].toString()).isEqualTo("PayNudge <codes@example.com>");
	}

	@Test
	void aRejectedSendReachesTheCallerAsADeliveryError() {
		this.smtp.failWith = new MailSendException("Mailbox unavailable");

		assertThatThrownBy(() -> this.sender.send(MESSAGE)).isInstanceOf(EmailDeliveryException.class)
			.hasMessage("SMTP delivery failed")
			.hasRootCauseMessage("Mailbox unavailable");
	}

	/** Records the message and the thread it was sent on instead of talking to a server. */
	private static final class RecordingMailSender extends JavaMailSenderImpl {

		final AtomicReference<MimeMessage> sent = new AtomicReference<>();

		final AtomicReference<Thread> thread = new AtomicReference<>();

		MailSendException failWith;

		@Override
		public void send(MimeMessage message) {
			if (this.failWith != null) {
				throw this.failWith;
			}
			this.thread.set(Thread.currentThread());
			this.sent.set(message);
		}

	}

}
