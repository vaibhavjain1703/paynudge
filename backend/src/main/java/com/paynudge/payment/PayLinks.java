package com.paynudge.payment;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.paynudge.common.ApiException;
import com.paynudge.common.PublicLinks;
import com.paynudge.payment.RazorpayApi.LinkPayment;
import com.paynudge.payment.RazorpayApi.NewPaymentLink;
import com.paynudge.payment.RazorpayApi.PaymentLink;
import com.paynudge.payment.RazorpayApi.RazorpayException;
import com.paynudge.payment.RazorpayEvent.PaidLink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The customer's side of paying: what the pay page shows, the Razorpay payment link it sends them to, and the signed
 * result Razorpay hands back when they return. Everything is found by the due's pay token, the secret in the link
 * every reminder carries; nothing needs an account.
 */
@Service
class PayLinks {

	static final String NOT_PAYABLE = "NOT_PAYABLE";

	static final String PAYMENTS_OFF = "PAYMENTS_NOT_SET_UP";

	static final String PROVIDER_ERROR = "PAYMENT_PROVIDER_ERROR";

	static final String INVALID_SIGNATURE = "INVALID_SIGNATURE";

	/** How long a new link stays open, and how much of that must be left to reuse one. */
	static final Duration LINK_LIFETIME = Duration.ofDays(7);

	static final Duration REUSE_MARGIN = Duration.ofDays(1);

	private static final Logger log = LoggerFactory.getLogger(PayLinks.class);

	private final JdbcTemplate jdbc;

	private final RazorpayApi razorpay;

	private final RazorpayProperties properties;

	private final Payments payments;

	private final PublicLinks links;

	private final Clock clock;

	PayLinks(JdbcTemplate jdbc, RazorpayApi razorpay, RazorpayProperties properties, Payments payments,
			PublicLinks links, Clock clock) {
		this.jdbc = jdbc;
		this.razorpay = razorpay;
		this.properties = properties;
		this.payments = payments;
		this.links = links;
		this.clock = clock;
	}

	/**
	 * What the pay page shows. Only the customer's first name: the link may be forwarded.
	 * @param status OPEN, PAID or CANCELLED
	 * @param payable the Pay button works: the due is open and online payment is set up
	 */
	record PayPage(String businessName, String customerFirstName, long amountPaise, LocalDate dueDate, String status,
			Instant paidAt, boolean payable) {
	}

	/** The query Razorpay adds when it sends the customer back. */
	record ReturnTrip(String paymentId, String paymentLinkId, String referenceId, String status, String signature) {
	}

	PayPage page(String token) {
		Due due = find(token);
		String status = switch (due.status()) {
			case "PAID" -> "PAID";
			case "CANCELLED" -> "CANCELLED";
			default -> "OPEN";
		};
		return new PayPage(due.businessName(), due.customerName().strip().split("\\s+")[0], due.amountPaise(),
				due.dueDate(), status, due.paidAt(), "OPEN".equals(status) && this.properties.payable());
	}

	/**
	 * The link to pay through: the due's current one if it has a day or more left, otherwise a new one. Razorpay is
	 * called outside any transaction; if two clicks race, the first link stored wins and both go to it.
	 */
	String link(String token) {
		Due due = find(token);
		if ("PAID".equals(due.status()) || "CANCELLED".equals(due.status())) {
			throw ApiException.conflict(NOT_PAYABLE,
					"PAID".equals(due.status()) ? "This is already paid" : "There is nothing to pay here");
		}
		if (!this.properties.payable()) {
			throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, PAYMENTS_OFF,
					"Online payment isn't set up yet. Please pay " + due.businessName() + " directly.");
		}
		Instant now = this.clock.instant();
		boolean current = due.linkExpiresAt() != null && due.linkExpiresAt().isAfter(now.plus(REUSE_MARGIN));
		if (due.linkUrl() != null && current) {
			return due.linkUrl();
		}
		PaymentLink created;
		try {
			created = this.razorpay.createPaymentLink(new NewPaymentLink(due.amountPaise(),
					"due-" + due.id() + "-" + now.getEpochSecond(), "Payment to " + due.businessName(),
					due.customerName(), due.customerEmail(), due.customerPhone(), token, this.links.pay(token),
					now.plus(LINK_LIFETIME)));
		}
		catch (RazorpayException ex) {
			log.warn("Payment link not created dueId={}: {}", due.id(), ex.getMessage());
			throw new ApiException(HttpStatus.BAD_GATEWAY, PROVIDER_ERROR,
					"Razorpay couldn't start the payment. Please try again in a minute.");
		}
		int stored = this.jdbc.update("""
				UPDATE dues SET pay_link_id = ?, pay_link_url = ?, pay_link_expires_at = ?
				WHERE id = ? AND (pay_link_expires_at IS NULL OR pay_link_expires_at <= ?)""", created.id(),
				created.shortUrl(), utc(now.plus(LINK_LIFETIME)), due.id(), utc(now.plus(REUSE_MARGIN)));
		return (stored == 1) ? created.shortUrl() : find(token).linkUrl();
	}

	/**
	 * The customer is back from Razorpay. The result is believed only if its signature (made with the key secret)
	 * checks out; the payment itself is then read from Razorpay, not from the query, and recorded exactly as the
	 * webhook would record it, so whichever arrives second changes nothing.
	 */
	PayPage confirm(String token, ReturnTrip trip) {
		if (!this.properties.payable() || !signedByRazorpay(trip)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_SIGNATURE,
					"This payment result could not be verified");
		}
		find(token);
		PaymentLink link;
		try {
			link = this.razorpay.paymentLink(trip.paymentLinkId());
		}
		catch (RazorpayException ex) {
			log.warn("Payment link not read linkId={}: {}", trip.paymentLinkId(), ex.getMessage());
			throw new ApiException(HttpStatus.BAD_GATEWAY, PROVIDER_ERROR,
					"Razorpay couldn't confirm the payment yet. It will show here shortly.");
		}
		if (!token.equals(link.payToken())) {
			throw new ApiException(HttpStatus.BAD_REQUEST, INVALID_SIGNATURE, "This payment is for something else");
		}
		Optional<LinkPayment> payment = link.payments()
			.stream()
			.filter((candidate) -> candidate.id().equals(trip.paymentId()) && "captured".equals(candidate.status()))
			.findFirst();
		if ("paid".equals(link.status()) && payment.isPresent()) {
			LinkPayment paid = payment.get();
			Payments.Outcome outcome = this.payments.record(new PaidLink(token, paid.id(), paid.amountPaise(),
					paid.method(), (paid.createdAt() != null) ? paid.createdAt() : this.clock.instant()));
			log.info("Return trip {} paymentId={}", outcome, paid.id());
		}
		return page(token);
	}

	/** Razorpay's signature on the return trip: hex HMAC-SHA256 of link id|reference id|status|payment id. */
	private boolean signedByRazorpay(ReturnTrip trip) {
		if (trip.signature() == null || trip.paymentLinkId() == null || trip.paymentId() == null) {
			return false;
		}
		String payload = trip.paymentLinkId() + "|" + trip.referenceId() + "|" + trip.status() + "|" + trip.paymentId();
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(this.properties.keySecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			byte[] expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)))
				.getBytes(StandardCharsets.US_ASCII);
			return MessageDigest.isEqual(expected,
					trip.signature().strip().toLowerCase().getBytes(StandardCharsets.US_ASCII));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA256 is not available", ex);
		}
	}

	private Due find(String token) {
		List<Due> found = this.jdbc.query("""
				SELECT d.id, d.status, d.amount_paise, d.due_date, d.paid_at, d.pay_link_url, d.pay_link_expires_at,
				       c.name AS customer_name, c.email, c.phone, b.name AS business_name
				FROM dues d
				JOIN customers c ON c.id = d.customer_id
				JOIN businesses b ON b.id = d.business_id
				WHERE d.pay_token = ?""", (rs, row) -> due(rs), token);
		return found.stream()
			.findFirst()
			.orElseThrow(() -> ApiException.notFound("This payment link is not valid"));
	}

	private static Due due(ResultSet rs) throws SQLException {
		return new Due(rs.getLong("id"), rs.getString("status"), rs.getLong("amount_paise"),
				rs.getObject("due_date", LocalDate.class), instant(rs, "paid_at"), rs.getString("pay_link_url"),
				instant(rs, "pay_link_expires_at"), rs.getString("customer_name"), rs.getString("email"),
				rs.getString("phone"), rs.getString("business_name"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return (value == null) ? null : value.toInstant();
	}

	private static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private record Due(long id, String status, long amountPaise, LocalDate dueDate, Instant paidAt, String linkUrl,
			Instant linkExpiresAt, String customerName, String customerEmail, String customerPhone,
			String businessName) {
	}

}
