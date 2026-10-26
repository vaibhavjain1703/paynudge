package com.paynudge.channel;

/**
 * Reminders that are kept, not sent: the engine stores every message it delivers ({@code messages.body}), and the
 * owner reads it on the due's timeline. The live demo runs this way, so strangers trying it never email anyone.
 */
class OutboxChannel implements Channel {

	@Override
	public String name() {
		return "EMAIL";
	}

	@Override
	public String send(OutboundMessage message) {
		return null;
	}

}
