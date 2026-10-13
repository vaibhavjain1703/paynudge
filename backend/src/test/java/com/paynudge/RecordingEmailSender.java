package com.paynudge;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import com.paynudge.channel.EmailDeliveryException;
import com.paynudge.channel.EmailMessage;
import com.paynudge.channel.EmailSender;

/** Records every email instead of sending it, and can be told to fail the next send. */
public class RecordingEmailSender implements EmailSender {

	private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

	private final AtomicBoolean failNext = new AtomicBoolean();

	@Override
	public void send(EmailMessage message) {
		if (this.failNext.getAndSet(false)) {
			throw new EmailDeliveryException("simulated provider outage", null);
		}
		this.sent.add(message);
	}

	public List<EmailMessage> sent() {
		return List.copyOf(this.sent);
	}

	public EmailMessage last() {
		return this.sent.getLast();
	}

	public void failNextSend() {
		this.failNext.set(true);
	}

	public void reset() {
		this.sent.clear();
		this.failNext.set(false);
	}

}
