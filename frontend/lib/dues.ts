"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";

import { api } from "@/lib/api";
import type { Page } from "@/lib/customers";

export type DueStatus = "SCHEDULED" | "ACTIVE" | "PAID" | "CANCELLED" | "ESCALATED";

export type DueSummary = {
  id: number;
  customer: { id: number; name: string; email: string | null; phone: string | null };
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

export const DUE_STATUS_LABELS: Record<DueStatus, string> = {
  SCHEDULED: "Scheduled",
  ACTIVE: "Reminding",
  ESCALATED: "Needs you",
  PAID: "Paid",
  CANCELLED: "Cancelled",
};
