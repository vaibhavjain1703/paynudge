"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { api } from "@/lib/api";

/** Whose reminders an unsubscribe link stops, and whether it has been used. */
export type UnsubscribeStatus = { businessName: string; optedOut: boolean };

const unsubscribeKey = (token: string) => ["unsubscribe", token] as const;

const path = (token: string) => `/api/public/unsubscribe/${encodeURIComponent(token)}`;

/** Public: the customer has no account, so no token is sent even if this browser has one. */
export function useUnsubscribeStatus(token: string) {
  return useQuery({
    queryKey: unsubscribeKey(token),
    queryFn: ({ signal }) => api<UnsubscribeStatus>(path(token), { signal, auth: false }),
  });
}

export function useUnsubscribe(token: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => api<UnsubscribeStatus>(path(token), { method: "POST", auth: false }),
    onSuccess: (status) => queryClient.setQueryData(unsubscribeKey(token), status),
  });
}
