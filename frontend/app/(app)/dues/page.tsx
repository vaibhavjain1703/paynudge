import type { Metadata } from "next";

import { DuesView } from "@/components/dues/dues-view";

export const metadata: Metadata = { title: "Dues" };

export default function DuesPage() {
  return <DuesView />;
}
