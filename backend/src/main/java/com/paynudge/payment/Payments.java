package com.paynudge.payment;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.paynudge.common.DueEvents.DueChanged;
import com.paynudge.dues.DueService;
import com.paynudge.dues.DueService.PayableDue;
import com.paynudge.dues.DueStatus;
import com.paynudge.payment.RazorpayEvent.PaidLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a payment and closes its due, in one transaction that holds the due's row lock (the lock a sending worker
 * holds): a reminder going out right now finishes first, and none starts afterwards. Razorpay delivers each event at
 * least once, so the same payment may arrive many times, even at the same moment; {@code payments} is unique on the
 * provider's payment id and every later copy changes nothing.
 */
@Service
class Payments {

	private static final Logger log = LoggerFactory.getLogger(Payments.class);

	enum Outcome {

		/** New payment; the due is now paid and its waiting reminders cancelled. */
		RECORDED,
		/** This payment was recorded before. */
		DUPLICATE,
		/** No due has this pay token (a link from elsewhere, or a deleted customer's). */
		UNKNOWN_LINK,
		/** Recorded, but for less than the due's amount, so the due stays open. Links do not allow part payments. */
		UNDERPAID

	}

	private final DueService dues;

	private final JdbcTemplate jdbc;

	private final ApplicationEventPublisher events;

	Payments(DueService dues, JdbcTemplate jdbc, ApplicationEventPublisher events) {
		this.dues = dues;
		this.jdbc = jdbc;
		this.events = events;
	}

	@Transactional
	Outcome record(PaidLink paid) {
		Optional<PayableDue> found = this.dues.lockForPayment(paid.payToken());
		if (found.isEmpty()) {
			return Outcome.UNKNOWN_LINK;
		}
		PayableDue due = found.get();
		List<Long> inserted = this.jdbc.queryForList("""
				INSERT INTO payments (due_id, provider_payment_id, amount_paise, paid_at, method)
				VALUES (?, ?, ?, ?, ?)
				ON CONFLICT (provider_payment_id) DO NOTHING
				RETURNING id""", Long.class, due.id(), paid.paymentId(), paid.amountPaise(),
				paid.paidAt().atOffset(ZoneOffset.UTC), paid.method());
		if (inserted.isEmpty()) {
			return Outcome.DUPLICATE;
		}
		if (paid.amountPaise() < due.amountPaise()) {
			log.warn("Payment below the due's amount dueId={} paymentId={} paidPaise={} duePaise={}", due.id(),
					paid.paymentId(), paid.amountPaise(), due.amountPaise());
			this.events.publishEvent(new DueChanged(due.id()));
			return Outcome.UNDERPAID;
		}
		if (!DueStatus.PAID.equals(due.status())) {
			// A cancelled due that gets paid is paid all the same: the money arrived.
			this.dues.settle(due.id(), paid.paidAt());
		}
		return Outcome.RECORDED;
	}

}
