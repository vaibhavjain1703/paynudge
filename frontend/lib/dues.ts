"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api } from "@/lib/api";
import type { Page } from "@/lib/customers";

export type DueStatus = "SCHEDULED" | "ACTIVE" | "PAID" | "CANCELLED" | "ESCALATED";

export type DueSummary = {
  id: number;
  /** `optedOut`: the customer used the unsubscribe link, so none of their dues get reminders. */
  customer: { id: number; name: string; email: string | null; phone: string | null; optedOut: boolean };
  amountPaise: number;
  dueDate: string;
  status: DueStatus;
  /** The next step still to run, or null when nothing is left. */
  nextStep: { key: string; action: "SEND_EMAIL" | "NOTIFY_OWNER"; runAt: string } | null;
  createdAt: string;
};

export const DUES_PAGE_SIZE = 25;

export const dueKeys = {
  all: ["dues"] as const,
  list: (page: number) => ["dues", { page }] as const,
};

export function useDues(page: number) {
  return useQuery({
    queryKey: dueKeys.list(page),
    queryFn: ({ signal }) =>
      api<Page<DueSummary>>(`/api/dues?page=${page}&size=${DUES_PAGE_SIZE}`, { signal }),
    placeholderData: keepPreviousData,
  });
}

/** Paid outside PayNudge, or called off: either way the due's remaining reminders stop. */
export type DueAction = "mark-paid" | "cancel";

export function useDueAction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, action }: { id: number; action: DueAction }) =>
      api<{ id: number; status: DueStatus }>(`/api/dues/${id}/${action}`, { method: "POST" }),
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
