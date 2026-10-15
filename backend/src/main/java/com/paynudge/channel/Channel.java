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

	/**
	 * A plain-text message, already filled in.
	 * @param reference the same for every try of one step's message, so a resend after a crash can be recognised
	 * downstream (email uses it as the Message-ID)
	 * @param oneClickUnsubscribe where a mail client may POST to unsubscribe the customer (RFC 8058)
	 */
	record OutboundMessage(String to, String subject, String text, String reference, String oneClickUnsubscribe) {
	}

	/** The provider did not take the message. {@code permanent}: trying again cannot help (an address it rejects). */
	class ChannelException extends RuntimeException {

		private final boolean permanent;

		public ChannelException(String message, Throwable cause, boolean permanent) {
			super(message, cause);
			this.permanent = permanent;
		}

		public boolean permanent() {
			return this.permanent;
		}

	}

}
