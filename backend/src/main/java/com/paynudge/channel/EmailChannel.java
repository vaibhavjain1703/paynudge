package com.paynudge.channel;

import java.util.Properties;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/** Customer reminders by email, through the reminder sandbox in {@link ReminderMailProperties}. */
@Component
class EmailChannel implements Channel, DisposableBean {

	private final SmtpEmailSender sender;

	EmailChannel(ReminderMailProperties properties) {
		this.sender = new SmtpEmailSender(mailSender(properties),
				new MailSettings(MailSettings.Provider.SMTP, properties.from()));
	}

	@Override
	public String name() {
		return "EMAIL";
	}

	@Override
	public String send(OutboundMessage message) {
		try {
			return this.sender.send(new EmailMessage(message.to(), message.subject(), message.text(), null));
		}
		catch (EmailDeliveryException ex) {
			throw new ChannelException(ex.getMessage(), ex);
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
