"use client";

import { useQuery } from "@tanstack/react-query";

import { api } from "@/lib/api";

export type PolicyStep = {
  key: string;
  offsetDays: number;
  action: "SEND_EMAIL" | "NOTIFY_OWNER";
  tone: string | null;
  subject: string | null;
  body: string | null;
};

export type Policy = {
  id: number;
  name: string;
  /** A built-in starter: usable and copyable, not editable. */
  starter: boolean;
  steps: PolicyStep[];
  createdAt: string;
};

export const policyKeys = { all: ["policies"] as const };

export function usePolicies() {
  return useQuery({
    queryKey: policyKeys.all,
    queryFn: ({ signal }) => api<Policy[]>("/api/policies", { signal }),
    staleTime: 5 * 60_000,
  });
}

/** "2 days before", "on the due date", "3 days after". */
export function describeDay(offsetDays: number) {
  if (offsetDays === 0) return "on the due date";
  const days = Math.abs(offsetDays);
  return `${days} ${days === 1 ? "day" : "days"} ${offsetDays < 0 ? "before" : "after"}`;
}

/** One line for a picker: "4 emails, from 2 days before to 10 days after; you hear after 7 days." */
export function summarizePolicy(policy: Policy) {
  const emails = policy.steps.filter((step) => step.action === "SEND_EMAIL");
  const owner = policy.steps.find((step) => step.action === "NOTIFY_OWNER");
  const span =
    emails.length === 0
      ? "No emails"
      : emails.length === 1
        ? `1 email, ${describeDay(emails[0].offsetDays)}`
        : `${emails.length} emails, from ${describeDay(emails[0].offsetDays)} to ${describeDay(emails[emails.length - 1].offsetDays)}`;
  return owner ? `${span}; you hear ${describeDay(owner.offsetDays)} if still unpaid.` : `${span}.`;
}
