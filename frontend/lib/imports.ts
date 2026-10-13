"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { api } from "@/lib/api";
import { customerKeys } from "@/lib/customers";
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

export function useCommitImport() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ file, mapping, policyId }: { file: File; mapping: ColumnMapping; policyId: number }) =>
      api<ImportResult>("/api/import/commit", { body: formOf(file, mapping, policyId) }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: customerKeys.all }),
        queryClient.invalidateQueries({ queryKey: dueKeys.all }),
      ]);
    },
  });
}
