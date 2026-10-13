package com.paynudge.dues;

/** The values of {@code due_steps.status} (and the CHECK constraint on it). */
public final class StepStatus {

	public static final String PENDING = "PENDING";

	public static final String RUNNING = "RUNNING";

	public static final String DONE = "DONE";

	public static final String FAILED = "FAILED";

	public static final String CANCELLED = "CANCELLED";

	public static final String SKIPPED = "SKIPPED";

	private StepStatus() {
	}

}
