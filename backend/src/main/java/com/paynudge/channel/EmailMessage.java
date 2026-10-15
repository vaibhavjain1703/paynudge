package com.paynudge.channel;

import java.util.Map;

/**
 * One outgoing email. {@code html} is optional; {@code text} is always sent as the plain-text part. {@code headers}
 * are extra header fields (Message-ID, List-Unsubscribe).
 */
public record EmailMessage(String to, String subject, String text, String html, Map<String, String> headers) {

	public EmailMessage {
		headers = (headers == null) ? Map.of() : Map.copyOf(headers);
	}

	public EmailMessage(String to, String subject, String text, String html) {
		this(to, subject, text, html, Map.of());
	}

}
