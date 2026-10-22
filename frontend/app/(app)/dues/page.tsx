import type { Metadata } from "next";

import { DuesView } from "@/components/dues/dues-view";
import { DUE_FILTERS } from "@/lib/due-filters";

export const metadata: Metadata = { title: "Dues" };

/** `?filter=overdue` opens the list on that chip (the dashboard's tiles link here). */
export default async function DuesPage({ searchParams }: { searchParams: Promise<{ filter?: string }> }) {
  const { filter } = await searchParams;
  const initial = DUE_FILTERS.find((candidate) => candidate === filter) ?? "all";
  return <DuesView key={initial} initialFilter={initial} />;
}
