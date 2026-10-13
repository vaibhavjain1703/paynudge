"use client";

import type { ImportPreview } from "@/lib/imports";
import { getToken } from "@/lib/session";

/** The file being checked on the Import page, with its preview and the policy the owner picked. */
export type ImportDraft = { file: File; preview: ImportPreview; policyId?: number };

/**
 * Kept while the owner visits other pages and comes back. It lives only in memory and belongs to the session that
 * chose it, so logging out (or in as someone else) or reloading the tab starts over, and customer data is never
 * written to browser storage.
 */
let saved: (ImportDraft & { token: string | null }) | undefined;

export function loadImportDraft(): ImportDraft | undefined {
  if (saved && saved.token !== getToken()) saved = undefined;
  return saved;
}

export function saveImportDraft(draft: ImportDraft | undefined) {
  saved = draft && { ...draft, token: getToken() };
}

/**
 * A copy of the file in memory. The import then sends exactly the rows that were checked, and still works after
 * the file on disk is edited (browsers refuse to upload a picked file that changed since it was picked).
 */
export async function inMemory(file: File) {
  return new File([await file.arrayBuffer()], file.name, { type: file.type, lastModified: file.lastModified });
}
