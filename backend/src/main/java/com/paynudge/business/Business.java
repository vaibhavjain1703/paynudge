package com.paynudge.business;

import java.time.Instant;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The one business an owner runs: its name, timezone, quiet hours and sending limits. */
@Entity
@Table(name = "businesses")
public class Business {

	public static final String DEFAULT_TIMEZONE = "Asia/Kolkata";

	public static final LocalTime DEFAULT_QUIET_START = LocalTime.of(21, 0);

	public static final LocalTime DEFAULT_QUIET_END = LocalTime.of(9, 0);

	public static final int DEFAULT_DAILY_SEND_CAP = 500;

	/** The most messages one business may send in a day. An anti-spam limit, not a billing one. */
	public static final int MAX_DAILY_SEND_CAP = 500;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "owner_id", nullable = false, updatable = false)
	private Long ownerId;

	@Column(nullable = false, length = 200)
	private String name;

	@Column(nullable = false, length = 50)
	private String timezone;

	@Column(name = "quiet_start", nullable = false)
	private LocalTime quietStart;

	@Column(name = "quiet_end", nullable = false)
	private LocalTime quietEnd;

	@Column(name = "sender_name", length = 100)
	private String senderName;

	@Column(name = "daily_send_cap", nullable = false)
	private int dailySendCap;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** Made by "Try the demo": sample data, deleted after a day, owner notices never emailed. */
	@Column(nullable = false, updatable = false)
	private boolean demo;

	protected Business() {
	}

	private Business(long ownerId, String name, Instant createdAt) {
		this.ownerId = ownerId;
		this.name = name;
		this.timezone = DEFAULT_TIMEZONE;
		this.quietStart = DEFAULT_QUIET_START;
		this.quietEnd = DEFAULT_QUIET_END;
		this.dailySendCap = DEFAULT_DAILY_SEND_CAP;
		this.createdAt = createdAt;
	}

	/** A new business with the default timezone, quiet hours and send cap. */
	public static Business createFor(long ownerId, String name, Instant now) {
		return new Business(ownerId, name, now);
	}

	/** A demo business: the defaults, marked so that it is cleaned up and never emails its (made-up) owner. */
	public static Business createDemoFor(long ownerId, String name, Instant now) {
		Business business = new Business(ownerId, name, now);
		business.demo = true;
		return business;
	}

	/** Replaces every editable setting. Callers validate first. */
	void update(String name, String timezone, LocalTime quietStart, LocalTime quietEnd, String senderName,
			int dailySendCap) {
		this.name = name;
		this.timezone = timezone;
		this.quietStart = quietStart;
		this.quietEnd = quietEnd;
		this.senderName = senderName;
		this.dailySendCap = dailySendCap;
	}

	public Long getId() {
		return this.id;
	}

	public Long getOwnerId() {
		return this.ownerId;
	}

	public String getName() {
		return this.name;
	}

	public String getTimezone() {
		return this.timezone;
	}

	public LocalTime getQuietStart() {
		return this.quietStart;
	}

	public LocalTime getQuietEnd() {
		return this.quietEnd;
	}

	public String getSenderName() {
		return this.senderName;
	}

	public int getDailySendCap() {
		return this.dailySendCap;
	}

	public boolean isDemo() {
		return this.demo;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
