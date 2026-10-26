"use client";

import type { ImportPreview, ManualEntryInput } from "@/lib/imports";
import { getToken } from "@/lib/session";

/** Upload a CSV, or type one due in by hand. */
export type ImportMode = "csv" | "manual";

/**
 * Where the owner left the Import page: which way they were adding dues, the policy they picked, the file being
 * checked (with its preview) and the due being typed in.
 */
export type ImportDraft = {
  mode: ImportMode;
  policyId?: number;
  csv?: { file: File; preview: ImportPreview };
  entry?: Partial<ManualEntryInput>;
};

/**
 * Kept while the owner visits other pages and comes back. It lives only in memory and belongs to the session that
 * made it, so logging out (or in as someone else) or reloading the tab starts over, and customer data is never
 * written to browser storage.
 */
let saved: (ImportDraft & { token: string | null }) | undefined;

export function loadImportDraft(): ImportDraft | undefined {
  if (saved && saved.token !== getToken()) saved = undefined;
  return saved;
}

/** Updates part of the draft; `{ csv: undefined }` lets the file go and keeps the rest. */
export function saveImportDraft(change: Partial<ImportDraft>) {
  saved = { ...(loadImportDraft() ?? { mode: "csv" }), ...change, token: getToken() };
}

/**
 * A copy of the file in memory. The import then sends exactly the rows that were checked, and still works after
 * the file on disk is edited (browsers refuse to upload a picked file that changed since it was picked).
 */
export async function inMemory(file: File) {
  return new File([await file.arrayBuffer()], file.name, { type: file.type, lastModified: file.lastModified });
}
