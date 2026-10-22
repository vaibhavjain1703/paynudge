/** The chips above the dues list. Plain data, so the server page can read a `?filter=` against it too. */
export const DUE_FILTERS = ["all", "open", "needs-you", "overdue", "paid", "cancelled"] as const;

export type DueFilter = (typeof DUE_FILTERS)[number];
