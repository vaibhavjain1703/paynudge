package com.paynudge.demo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.paynudge.business.Business;
import com.paynudge.common.Tokens;
import com.paynudge.customer.CustomerFields;
import com.paynudge.customer.CustomerImporter;
import com.paynudge.dues.DueService;
import com.paynudge.dues.DueStatus;
import com.paynudge.dues.StepStatus;
import com.paynudge.engine.PastReminders;
import com.paynudge.policy.PolicyService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A demo business's sample data: a tuition centre two months into PayNudge. Every state shows up: fees paid online
 * and in cash, overdue ones still being reminded, one that needs the owner, a customer who unsubscribed, a cancelled
 * fee, upcoming ones, and one whose first reminder goes out moments after the demo opens. Past reminders carry the
 * engine's own wording and working pay links; customers have example.com addresses, and demo reminders are never
 * emailed anywhere real.
 */
@Service
public class DemoData {

	public static final String BUSINESS_NAME = "Sunrise Tuition Centre";

	static final String POLICY = "Friendly then firm";

	private static final ZoneId ZONE = ZoneId.of(Business.DEFAULT_TIMEZONE);

	private static final List<CustomerFields> STUDENTS = List.of(
			fields("Aarav Shah", "aarav.shah@example.com", "98765 43210"),
			fields("Diya Rao", "diya.rao@example.com", null),
			fields("Kabir Mehta", "kabir.mehta@example.com", "91234 56789"),
			fields("Ananya Bose", "ananya.bose@example.com", null),
			fields("Rohan Gupta", "rohan.gupta@example.com", "99887 76655"),
			fields("Meera Iyer", "meera.iyer@example.com", null),
			fields("Ishaan Khanna", "ishaan.khanna@example.com", null),
			fields("Sana Khan", "sana.khan@example.com", "90000 12345"),
			fields("Vihaan Joshi", "vihaan.joshi@example.com", null),
			fields("Priya Nair", "priya.nair@example.com", null));

	private static final List<String> METHODS = List.of("upi", "card", "netbanking", "upi");

	/** What happened to a fee. */
	enum Outcome {

		/** Paid through the pay link. */
		PAID_ONLINE,
		/** Paid in cash; the owner marked it. */
		PAID_CASH,
		/** Still unpaid: reminders so far went out, the rest wait. */
		OPEN,
		/** Its first reminder is due right now, so the engine sends it while the visitor watches. */
		LIVE,
		/** Called off by the owner. */
		CANCELLED,
		/** The student used the unsubscribe link. */
		UNSUBSCRIBED

	}

	/**
	 * @param student index into {@link #STUDENTS}
	 * @param dueInDays from today; negative is in the past
	 * @param settledAfterDays when it was paid, cancelled or unsubscribed, in days after the due date
	 */
	record Story(int student, long amountPaise, int dueInDays, Outcome outcome, int settledAfterDays) {
	}

	/** This month's fees, one story each. */
	static final List<Story> THIS_MONTH = List.of(new Story(0, 250000, -12, Outcome.PAID_ONLINE, 1),
			new Story(1, 320000, -5, Outcome.OPEN, 0), new Story(2, 450000, -8, Outcome.OPEN, 0),
			new Story(3, 250000, -3, Outcome.PAID_CASH, 2), new Story(4, 850000, 0, Outcome.OPEN, 0),
			new Story(5, 180000, 2, Outcome.LIVE, 0), new Story(6, 500000, 9, Outcome.OPEN, 0),
			new Story(7, 220000, 16, Outcome.OPEN, 0), new Story(8, 300000, -6, Outcome.UNSUBSCRIBED, 1),
			new Story(9, 600000, -20, Outcome.CANCELLED, 4));

	private final CustomerImporter customers;

	private final PolicyService policies;

	private final DueService dues;

	private final PastReminders past;

	private final JdbcTemplate jdbc;

	DemoData(CustomerImporter customers, PolicyService policies, DueService dues, PastReminders past,
			JdbcTemplate jdbc) {
		this.customers = customers;
		this.policies = policies;
		this.dues = dues;
		this.past = past;
		this.jdbc = jdbc;
	}

	/** Fills a new demo business. Runs in the caller's transaction, which creates the business. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void seed(long businessId, Instant now) {
		Map<String, Long> ids = this.customers.findOrCreate(businessId, STUDENTS).idsByKey();
		List<Story> stories = stories();
		LocalDate today = now.atZone(ZONE).toLocalDate();
		List<Long> created = this.dues
			.createImported(businessId, this.policies.starterId(businessId, POLICY), stories.stream()
				.map((story) -> new DueService.ImportedDue(ids.get(STUDENTS.get(story.student()).key()),
						story.amountPaise(), today.plusDays(story.dueInDays()), null))
				.toList())
			.ids();
		for (int i = 0; i < stories.size(); i++) {
			tell(created.get(i), stories.get(i), today, now, ids.get(STUDENTS.get(stories.get(i).student()).key()));
		}
	}

	/** This month's fees, then two earlier months that were all paid (they fill the collections chart). */
	static List<Story> stories() {
		List<Story> stories = new ArrayList<>(THIS_MONTH);
		for (Story story : THIS_MONTH) {
			for (int month = 1; month <= 2; month++) {
				int student = story.student();
				Outcome outcome = ((student + month) % 4 == 0) ? Outcome.PAID_CASH : Outcome.PAID_ONLINE;
				stories.add(new Story(student, story.amountPaise(), story.dueInDays() - 30 * month, outcome,
						(student + month) % 4));
			}
		}
		return stories;
	}

	private void tell(long dueId, Story story, LocalDate today, Instant now, long customerId) {
		LocalDate dueDate = today.plusDays(story.dueInDays());
		Instant settled = at(dueDate.plusDays(story.settledAfterDays()), LocalTime.of(14, 0).plusMinutes(
				17L * story.student()));
		this.jdbc.update("UPDATE dues SET created_at = ? WHERE id = ?",
				min(at(dueDate.minusDays(12), LocalTime.of(18, 30)), now).atOffset(ZoneOffset.UTC), dueId);
		switch (story.outcome()) {
			case PAID_ONLINE -> {
				this.past.backfill(dueId, settled);
				this.jdbc.update("""
						INSERT INTO payments (due_id, provider_payment_id, amount_paise, paid_at, method)
						VALUES (?, ?, ?, ?, ?)""", dueId, "pay_demo" + Tokens.random().substring(0, 14), story.amountPaise(),
						settled.atOffset(ZoneOffset.UTC), METHODS.get(story.student() % METHODS.size()));
				this.dues.settle(dueId, settled);
			}
			case PAID_CASH -> {
				this.past.backfill(dueId, settled);
				this.dues.settle(dueId, settled);
			}
			case OPEN -> this.past.backfill(dueId, now);
			case LIVE -> {
				// Left as scheduled: its first reminder is due now, and the engine sends it.
			}
			case CANCELLED -> {
				this.past.backfill(dueId, settled);
				this.jdbc.update("UPDATE dues SET status = ? WHERE id = ?", DueStatus.CANCELLED, dueId);
				stop(dueId, "Due is cancelled");
			}
			case UNSUBSCRIBED -> {
				this.past.backfill(dueId, settled);
				this.jdbc.update("UPDATE customers SET opted_out = true, opted_out_at = ? WHERE id = ?",
						settled.atOffset(ZoneOffset.UTC), customerId);
				stop(dueId, "Customer unsubscribed");
			}
		}
	}

	private void stop(long dueId, String reason) {
		this.jdbc.update("UPDATE due_steps SET status = ?, error = ? WHERE due_id = ? AND status = ?",
				StepStatus.CANCELLED, reason, dueId, StepStatus.PENDING);
	}

	private static Instant at(LocalDate date, LocalTime time) {
		return date.atTime(time).atZone(ZONE).toInstant();
	}

	private static Instant min(Instant a, Instant b) {
		return a.isBefore(b) ? a : b;
	}

	private static CustomerFields fields(String name, String email, String phone) {
		return CustomerFields.parse(name, email, phone).fields();
	}

}
