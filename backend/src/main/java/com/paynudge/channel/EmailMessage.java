package com.paynudge.channel;

/** One outgoing email. {@code html} is optional; {@code text} is always sent as the plain-text part. */
public record EmailMessage(String to, String subject, String text, String html) {
}
