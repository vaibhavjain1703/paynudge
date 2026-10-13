"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";

import { api } from "@/lib/api";
import { customerKeys } from "@/lib/customers";

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
};

export type ImportPreview = {
  headers: string[];
  mapping: ColumnMapping;
  /** Fields still without a column; "contact" means neither email nor phone. */
  missingColumns: (ImportField | "contact")[];
  summary: { rows: number; valid: number; invalid: number; newCustomers: number; existingCustomers: number };
  rows: PreviewRow[];
};

export type ImportResult = {
  importedRows: number;
  skippedRows: number;
  customersCreated: number;
  customersMatched: number;
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

function formOf(file: File, mapping?: ColumnMapping) {
  const form = new FormData();
  form.append("file", file);
  if (mapping) form.append("mapping", new Blob([JSON.stringify(mapping)], { type: "application/json" }));
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
    mutationFn: ({ file, mapping }: { file: File; mapping: ColumnMapping }) =>
      api<ImportResult>("/api/import/commit", { body: formOf(file, mapping) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: customerKeys.all }),
  });
}
