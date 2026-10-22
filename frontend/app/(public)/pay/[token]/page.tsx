import type { Metadata } from "next";

import { PayView } from "@/components/pay/pay-view";

export const metadata: Metadata = { title: "Pay", robots: { index: false } };

type Query = Record<string, string | string[] | undefined>;

/** Razorpay sends the customer back here with the signed result in the query. */
export default async function PayPage({
  params,
  searchParams,
}: {
  params: Promise<{ token: string }>;
  searchParams: Promise<Query>;
}) {
  const { token } = await params;
  const query = await searchParams;
  const one = (name: string) => (typeof query[name] === "string" ? query[name] : undefined);
  const paymentId = one("razorpay_payment_id");
  const paymentLinkId = one("razorpay_payment_link_id");
  const trip =
    paymentId && paymentLinkId
      ? {
          paymentId,
          paymentLinkId,
          referenceId: one("razorpay_payment_link_reference_id") ?? "",
          status: one("razorpay_payment_link_status") ?? "",
          signature: one("razorpay_signature") ?? "",
        }
      : null;
  return <PayView token={token} trip={trip} />;
}
