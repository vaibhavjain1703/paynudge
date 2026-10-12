package com.paynudge.channel;

/**
 * Sends one email. Implementations differ by transport (SMTP to the local Mailpit inbox, or Brevo's HTTPS API for
 * real inboxes, since Render's free plan blocks outbound SMTP). Tests replace this with a recording fake.
 */
public interface EmailSender {

	/** @throws EmailDeliveryException when the provider rejects or cannot be reached */
	void send(EmailMessage message);

}
