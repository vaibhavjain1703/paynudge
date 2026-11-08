package com.paynudge.channel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Customer reminders by email, through the reminder sandbox in {@link ReminderMailProperties}. */
class EmailChannel implements Channel, DisposableBean {

	private final SmtpEmailSender sender;

	/** The right-hand side of our Message-IDs: the sender's domain. */
	private final String domain;

	EmailChannel(ReminderMailProperties properties) {
		this.sender = new SmtpEmailSender(mailSender(properties),
				new MailSettings(MailSettings.Provider.SMTP, properties.from()));
		this.domain = domainOf(properties.from());
	}

	@Override
	public String name() {
		return "EMAIL";
	}

	/**
	 * The Message-ID comes from the message's reference, so if a worker dies after the server took the email and the
	 * step is sent again, both copies carry the same id and mail services (Gmail among them) keep only one.
	 * List-Unsubscribe with List-Unsubscribe-Post lets the mail client's own unsubscribe button work in one click.
	 */
	@Override
	public String send(OutboundMessage message) {
		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Message-ID", "<" + message.reference() + "@" + this.domain + ">");
		if (message.oneClickUnsubscribe() != null) {
			headers.put("List-Unsubscribe", "<" + message.oneClickUnsubscribe() + ">");
			headers.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
		}
		try {
			return this.sender.send(new EmailMessage(message.to(), message.subject(), message.text(), null, headers));
		}
		catch (EmailDeliveryException ex) {
			throw new ChannelException(ex.getMessage(), ex, ex.permanent());
		}
	}

	private static String domainOf(String from) {
		try {
			String address = new InternetAddress(from, true).getAddress();
			return address.substring(address.indexOf('@') + 1);
		}
		catch (AddressException ex) {
			throw new IllegalStateException("REMINDER_MAIL_FROM is not a valid address: " + from, ex);
		}
	}

	@Override
	public void destroy() {
		this.sender.destroy();
	}

	/** Its own connection settings, not a bean: a second JavaMailSender bean would switch off Boot's mail setup. */
	private static JavaMailSenderImpl mailSender(ReminderMailProperties properties) {
		JavaMailSenderImpl mail = new JavaMailSenderImpl();
		mail.setHost(properties.host());
		mail.setPort(properties.port());
		if (properties.authenticated()) {
			mail.setUsername(properties.username());
			mail.setPassword(properties.password());
		}
		Properties smtp = mail.getJavaMailProperties();
		smtp.put("mail.smtp.auth", String.valueOf(properties.authenticated()));
		smtp.put("mail.smtp.starttls.enable", String.valueOf(properties.starttls()));
		smtp.put("mail.smtp.connectiontimeout", "5000");
		smtp.put("mail.smtp.timeout", "5000");
		smtp.put("mail.smtp.writetimeout", "5000");
		smtp.put("mail.smtp.localhost", "localhost");
		return mail;
	}

}
