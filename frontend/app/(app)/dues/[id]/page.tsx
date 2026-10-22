import type { Metadata } from "next";
import { notFound } from "next/navigation";

import { DueDetailView } from "@/components/dues/due-detail-view";

export const metadata: Metadata = { title: "Due" };

export default async function DuePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!/^\d{1,18}$/.test(id)) notFound();
  return <DueDetailView id={Number(id)} />;
}
