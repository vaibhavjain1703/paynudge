package com.paynudge.channel;

/**
 * The email was not handed over. {@code permanent}: the provider refused the address itself, so trying again cannot
 * help; otherwise (timeouts, outages, throttling) a later try may succeed.
 */
public class EmailDeliveryException extends RuntimeException {

	private final boolean permanent;

	public EmailDeliveryException(String message, Throwable cause) {
		this(message, cause, false);
	}

	public EmailDeliveryException(String message, Throwable cause, boolean permanent) {
		super(message, cause);
		this.permanent = permanent;
	}

	public boolean permanent() {
		return this.permanent;
	}

}
