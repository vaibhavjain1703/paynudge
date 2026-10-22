"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api } from "@/lib/api";
import type { DueFilter } from "@/lib/due-filters";

export type { DueFilter } from "@/lib/due-filters";

export type DueStatus = "SCHEDULED" | "ACTIVE" | "PAID" | "CANCELLED" | "ESCALATED";

export type StepAction = "SEND_EMAIL" | "NOTIFY_OWNER";

/** `optedOut`: the customer used the unsubscribe link, so none of their dues get reminders. */
export type CustomerRef = { id: number; name: string; email: string | null; phone: string | null; optedOut: boolean };

export type DueSummary = {
  id: number;
  customer: CustomerRef;
  amountPaise: number;
  dueDate: string;
  status: DueStatus;
  /** The next step still to run, or null when nothing is left. */
  nextStep: { key: string; action: StepAction; runAt: string } | null;
  createdAt: string;
};

export type DueCounts = Record<"all" | "open" | "needsYou" | "overdue" | "paid" | "cancelled", number>;

export type DuePage = {
  items: DueSummary[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  /** How many dues (matching the search) each chip would show. */
  counts: DueCounts;
};

export type StepStatus = "PENDING" | "RUNNING" | "DONE" | "FAILED" | "CANCELLED" | "SKIPPED";

export type DueStep = {
  key: string;
  action: StepAction;
  status: StepStatus;
  runAt: string;
  attempt: number;
  /** Days from the due date, from the due's own copy of the policy. */
  offsetDays: number | null;
  tone: "friendly" | "neutral" | "firm" | "final_notice" | null;
  /** Why the step failed or was cancelled. */
  error: string | null;
  /** Why it is waiting: quiet hours, the daily limit, the gap between emails. */
  note: string | null;
};

export type DueMessage = { stepKey: string; channel: "EMAIL" | "OWNER_EMAIL"; subject: string | null; sentAt: string };

export type DuePayment = { providerPaymentId: string; amountPaise: number; method: string | null; paidAt: string };

export type DueDetail = {
  id: number;
  customer: CustomerRef;
  amountPaise: number;
  dueDate: string;
  status: DueStatus;
  policyId: number | null;
  /** Null if the policy was deleted later; the due keeps following its own copy. */
  policyName: string | null;
  paidAt: string | null;
  createdAt: string;
  steps: DueStep[];
  messages: DueMessage[];
  payments: DuePayment[];
};

export const DUES_PAGE_SIZE = 25;

export const dueKeys = {
  all: ["dues"] as const,
  list: (page: number, filter: DueFilter, search: string) => ["dues", "list", { page, filter, search }] as const,
  detail: (id: number) => ["dues", "detail", id] as const,
};

export function useDues(page: number, filter: DueFilter = "all", search = "") {
  return useQuery({
    queryKey: dueKeys.list(page, filter, search),
    queryFn: ({ signal }) => {
      const params = new URLSearchParams({ page: String(page), size: String(DUES_PAGE_SIZE), filter });
      if (search) params.set("q", search);
      return api<DuePage>(`/api/dues?${params}`, { signal });
    },
    placeholderData: keepPreviousData,
  });
}

export function useDue(id: number) {
  return useQuery({
    queryKey: dueKeys.detail(id),
    // Kept current by the live stream (lib/live.ts), which drops every change to the due straight in here.
    queryFn: ({ signal }) => api<DueDetail>(`/api/dues/${id}`, { signal }),
  });
}

/** Paid outside PayNudge, or called off: either way the due's remaining reminders stop. */
export type DueAction = "mark-paid" | "cancel";

export function useDueAction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, action }: { id: number; action: DueAction }) =>
      api<DueDetail>(`/api/dues/${id}/${action}`, { method: "POST" }),
    onSuccess: async (due) => {
      queryClient.setQueryData(dueKeys.detail(due.id), due);
      await queryClient.invalidateQueries({ queryKey: dueKeys.all });
    },
  });
}

export function useBulkDueAction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ ids, action }: { ids: number[]; action: DueAction }) =>
      api<{ updated: number; skipped: number }>("/api/dues/bulk", { body: { ids, action } }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: dueKeys.all }),
  });
}

/** Reminders can still be stopped: neither paid nor cancelled. */
export function isOpen(status: DueStatus) {
  return status !== "PAID" && status !== "CANCELLED";
}

export const DUE_STATUS_LABELS: Record<DueStatus, string> = {
  SCHEDULED: "Scheduled",
  ACTIVE: "Reminding",
  ESCALATED: "Needs you",
  PAID: "Paid",
  CANCELLED: "Cancelled",
};
