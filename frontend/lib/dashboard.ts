"use client";

import { useQuery } from "@tanstack/react-query";

import { api } from "@/lib/api";
import type { StepAction } from "@/lib/dues";

/** A sum of money and how many dues make it up. */
export type Total = { paise: number; dues: number };

export type Dashboard = {
  /** Every due the business has, in any state. */
  dues: number;
  collectedThisMonth: Total;
  /** Not yet paid or cancelled. */
  outstanding: Total;
  /** Outstanding, and the due date has passed. */
  overdue: Total;
  /** The policy has run out, or a reminder failed. */
  needsYou: number;
  /** Paid per week (weeks start on Monday), oldest first; the last is this week. */
  collections: { weekStart: string; paise: number }[];
  /** The next reminders and owner notices, soonest first. */
  upcoming: {
    dueId: number;
    customerName: string;
    amountPaise: number;
    stepKey: string;
    action: StepAction;
    runAt: string;
  }[];
};

/** Under the dues key, so anything that changes a due (paying, cancelling, importing) refreshes it too. */
export const dashboardKey = ["dues", "dashboard"] as const;

export function useDashboard() {
  return useQuery({
    queryKey: dashboardKey,
    queryFn: ({ signal }) => api<Dashboard>("/api/dashboard", { signal }),
  });
}
