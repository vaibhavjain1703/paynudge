package com.paynudge.common;

/**
 * What changed, published inside the transaction that changed it; live views hear about it once it commits. Any
 * module may publish these without depending on the one that listens.
 */
public final class DueEvents {

	private DueEvents() {
	}

	/** One due changed: a reminder went out or was postponed, it was paid or cancelled. */
	public record DueChanged(long dueId) {
	}

	/** Many of a business's dues changed at once: an import, a bulk action, an unsubscribe, a deleted customer. */
	public record DuesChanged(long businessId) {
	}

}
