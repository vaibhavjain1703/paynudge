package com.paynudge.channel;

/**
 * Delivers a reminder to a customer. Email is the only channel in the MVP; SMS and WhatsApp would be further
 * implementations once a provider approves them. Tests use a recording fake.
 */
public interface Channel {

	/** The value stored in {@code messages.channel}, e.g. "EMAIL". */
	String name();

	/**
	 * @return the provider's id for the message, or null if it gives none
	 * @throws ChannelException when the message could not be handed to the provider
	 */
	String send(OutboundMessage message);

	/** A plain-text message, already filled in. */
	record OutboundMessage(String to, String subject, String text) {
	}

	class ChannelException extends RuntimeException {

		public ChannelException(String message, Throwable cause) {
			super(message, cause);
		}

	}

}
