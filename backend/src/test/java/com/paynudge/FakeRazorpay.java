package com.paynudge;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.paynudge.payment.RazorpayApi;

/**
 * Razorpay in tests: makes payment links ({@code plink_1}, {@code plink_2}, ...) and remembers them, and reports back
 * whatever a test says happened on one ({@link #pay}). Can also be down.
 */
public class FakeRazorpay implements RazorpayApi {

	private final List<NewPaymentLink> created = new CopyOnWriteArrayList<>();

	private final Map<String, PaymentLink> links = new ConcurrentHashMap<>();

	private final AtomicInteger counter = new AtomicInteger();

	private final AtomicBoolean down = new AtomicBoolean();

	@Override
	public PaymentLink createPaymentLink(NewPaymentLink link) {
		if (this.down.get()) {
			throw new RazorpayException("simulated Razorpay outage", null);
		}
		this.created.add(link);
		String id = "plink_" + this.counter.incrementAndGet();
		PaymentLink made = new PaymentLink(id, "https://rzp.io/rzp/" + id, "created", link.expiresAt(),
				link.payToken(), List.of());
		this.links.put(id, made);
		return made;
	}

	@Override
	public PaymentLink paymentLink(String id) {
		if (this.down.get()) {
			throw new RazorpayException("simulated Razorpay outage", null);
		}
		PaymentLink link = this.links.get(id);
		if (link == null) {
			throw new RazorpayException("no such link " + id, null);
		}
		return link;
	}

	/** The customer pays the link: it becomes paid, with one captured payment. */
	public void pay(String linkId, String paymentId, long amountPaise, Instant at) {
		PaymentLink link = this.links.get(linkId);
		this.links.put(linkId, new PaymentLink(link.id(), link.shortUrl(), "paid", link.expiresAt(), link.payToken(),
				List.of(new LinkPayment(paymentId, amountPaise, "upi", "captured", at))));
	}

	public List<NewPaymentLink> created() {
		return List.copyOf(this.created);
	}

	public void down(boolean down) {
		this.down.set(down);
	}

	public void reset() {
		this.created.clear();
		this.links.clear();
		this.counter.set(0);
		this.down.set(false);
	}

}
