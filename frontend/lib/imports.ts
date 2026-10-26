"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";

import { api } from "@/lib/api";
import { customerFields, customerKeys, hasContact } from "@/lib/customers";
import { dueKeys } from "@/lib/dues";

export type ImportField = "name" | "email" | "phone" | "amount" | "dueDate";

/** Which header holds each field; null when the file has no such column. */
export type ColumnMapping = Record<ImportField, string | null>;

export type PreviewRow = {
  row: number;
  values: Partial<Record<ImportField, string>>;
  amountPaise: number | null;
  dueDate: string | null;
  errors: Partial<Record<ImportField, string>>;
  note: string | null;
  existingCustomer: boolean;
  /** Imported before (or a repeat of an earlier row): it will be skipped. */
  alreadyImported: boolean;
};

export type ImportPreview = {
  headers: string[];
  mapping: ColumnMapping;
  /** Fields still without a column; "contact" means neither email nor phone. */
  missingColumns: (ImportField | "contact")[];
  /** `alreadyImported` of the valid rows will be skipped; customer counts cover the rows to import. */
  summary: {
    rows: number;
    valid: number;
    invalid: number;
    alreadyImported: number;
    newCustomers: number;
    existingCustomers: number;
  };
  rows: PreviewRow[];
};

export type ImportResult = {
  /** Dues created, each with its reminders scheduled. */
  importedRows: number;
  alreadyImported: number;
  skippedRows: number;
  customersCreated: number;
  customersMatched: number;
  /** When the earliest new reminder goes out, or null if none will. */
  firstReminderAt: string | null;
};

export const FIELD_LABELS: Record<ImportField | "contact", string> = {
  name: "Name",
  email: "Email",
  phone: "Phone",
  amount: "Amount",
  dueDate: "Due date",
  contact: "Email or phone",
};

export const MAX_FILE_BYTES = 2 * 1024 * 1024;

function formOf(file: File, mapping?: ColumnMapping, policyId?: number) {
  const form = new FormData();
  form.append("file", file);
  if (mapping) form.append("mapping", new Blob([JSON.stringify(mapping)], { type: "application/json" }));
  if (policyId !== undefined) form.append("policyId", String(policyId));
  return form;
}

/** Checks a file without saving anything. Without a mapping the server guesses the columns. */
export function usePreviewImport() {
  return useMutation({
    mutationFn: ({ file, mapping }: { file: File; mapping?: ColumnMapping }) =>
      api<ImportPreview>("/api/import/preview", { body: formOf(file, mapping) }),
  });
}

/** Customers and dues both change: refetch whatever lists are showing them. */
function useRefreshLists() {
  const queryClient = useQueryClient();
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: customerKeys.all }),
      queryClient.invalidateQueries({ queryKey: dueKeys.all }),
    ]);
}

export function useCommitImport() {
  const refreshLists = useRefreshLists();
  return useMutation({
    mutationFn: ({ file, mapping, policyId }: { file: File; mapping: ColumnMapping; policyId: number }) =>
      api<ImportResult>("/api/import/commit", { body: formOf(file, mapping, policyId) }),
    onSuccess: refreshLists,
  });
}

// One due typed in by hand. A light mirror of the CSV row checks (ImportService.java), which have the final say:
// the amount goes up as typed and the server reads it into paise.
export const manualEntrySchema = customerFields
  .extend({
    amount: z
      .string()
      .trim()
      .min(1, "Enter the amount")
      .regex(/^₹?\s*\d[\d,]*(\.\d{1,2})?$/, "Use a number like 1500 or 1,500.50")
      .refine((amount) => /[1-9]/.test(amount), "The amount must be more than zero"),
    /** yyyy-mm-dd, from the date picker. */
    dueDate: z.string().min(1, "Enter the due date"),
  })
  .refine(...hasContact);

export type ManualEntryInput = z.infer<typeof manualEntrySchema>;

export type ManualEntryResult = {
  dueId: number;
  customerId: number;
  customerName: string;
  /** False when the email (or phone) matched a customer already in the list, who keeps their own details. */
  newCustomer: boolean;
  /** The customer has unsubscribed, so no reminders will go out. */
  optedOut: boolean;
  amountPaise: number;
  dueDate: string;
  /** When the first email goes out, or null if none will. */
  firstReminderAt: string | null;
};

/** The same due added twice (by hand or in a file) is refused; `details.dueId` is the one already there. */
export const DUE_EXISTS = "DUE_EXISTS";

export function useAddDue() {
  const refreshLists = useRefreshLists();
  return useMutation({
    mutationFn: ({ entry, policyId }: { entry: ManualEntryInput; policyId: number }) =>
      api<ManualEntryResult>("/api/import/manual", { body: { ...entry, policyId } }),
    onSuccess: refreshLists,
  });
}
