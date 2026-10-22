"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api } from "@/lib/api";

/** What a customer's pay page shows. Only their first name: the link may have been forwarded. */
export type PayPage = {
  businessName: string;
  customerFirstName: string;
  amountPaise: number;
  dueDate: string;
  status: "OPEN" | "PAID" | "CANCELLED";
  paidAt: string | null;
  /** The Pay button works: the due is open and online payment is set up. */
  payable: boolean;
};

/** The query Razorpay adds when it sends the customer back. */
export type ReturnTrip = {
  paymentId: string;
  paymentLinkId: string;
  referenceId: string;
  status: string;
  signature: string;
};

const payKey = (token: string) => ["pay", token] as const;

const path = (token: string) => `/api/public/pay/${encodeURIComponent(token)}`;

/** Public: the customer has no account, so no token is sent even if this browser holds an owner's. */
export function usePayPage(token: string) {
  return useQuery({
    queryKey: payKey(token),
    queryFn: ({ signal }) => api<PayPage>(path(token), { signal, auth: false }),
  });
}

/** Razorpay's page to pay on (made on the first click, reused after). */
export function usePayLink(token: string) {
  return useMutation({
    mutationFn: () => api<{ url: string }>(`${path(token)}/link`, { method: "POST", auth: false }),
  });
}

export function useConfirmPayment(token: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (trip: ReturnTrip) =>
      api<PayPage>(`${path(token)}/confirm`, { body: trip, auth: false }),
    onSuccess: (page) => queryClient.setQueryData(payKey(token), page),
  });
}
