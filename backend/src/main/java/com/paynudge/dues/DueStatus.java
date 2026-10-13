package com.paynudge.dues;

import java.util.Set;

/** The values of {@code dues.status} (and the CHECK constraint on it). */
public final class DueStatus {

	/** Created; no reminder has gone out yet. */
	public static final String SCHEDULED = "SCHEDULED";

	/** At least one reminder has gone out. */
	public static final String ACTIVE = "ACTIVE";

	public static final String PAID = "PAID";

	public static final String CANCELLED = "CANCELLED";

	/** The policy has run out and the owner has been told; still unpaid. */
	public static final String ESCALATED = "ESCALATED";

	/** No reminder may go out for a due in one of these states. */
	public static final Set<String> CLOSED = Set.of(PAID, CANCELLED);

	private DueStatus() {
	}

}
