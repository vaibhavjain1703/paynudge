import type { Metadata } from "next";

import { UnsubscribeView } from "@/components/unsubscribe/unsubscribe-view";

export const metadata: Metadata = { title: "Unsubscribe", robots: { index: false } };

export default async function UnsubscribePage({ params }: { params: Promise<{ token: string }> }) {
  const { token } = await params;
  return <UnsubscribeView token={token} />;
}
