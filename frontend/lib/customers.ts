"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { z } from "zod";

import { api } from "@/lib/api";

export type Customer = {
  id: number;
  name: string;
  email: string | null;
  phone: string | null;
  optedOut: boolean;
  createdAt: string;
};

export type Page<T> = {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
};

export const PAGE_SIZE = 25;

export const customerKeys = {
  all: ["customers"] as const,
  list: (query: string, page: number) => ["customers", { query, page }] as const,
};

// A light mirror of CustomerFields.java. The server tidies phones (+91...) and has the final say on both.
export const customerSchema = z
  .object({
    name: z.string().trim().min(1, "Enter a name").max(200, "Keep the name under 200 characters"),
    email: z.union([z.literal(""), z.string().trim().pipe(z.email("Enter a valid email address"))]),
    phone: z
      .string()
      .trim()
      .regex(/^(\+?[\d\s().-]+)?$/, "Enter a 10-digit mobile number, or + and the country code"),
  })
  .refine((customer) => customer.email !== "" || customer.phone !== "", {
    path: ["email"],
    message: "Add an email or a phone number",
  });

export type CustomerInput = z.infer<typeof customerSchema>;

export function useCustomers(query: string, page: number) {
  return useQuery({
    queryKey: customerKeys.list(query, page),
    queryFn: ({ signal }) => {
      const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
      if (query) params.set("q", query);
      return api<Page<Customer>>(`/api/customers?${params}`, { signal });
    },
    // Keep showing the current rows while the next page or search loads, instead of flashing a skeleton.
    placeholderData: keepPreviousData,
  });
}

function useCustomerMutation<TInput, TResult>(mutationFn: (input: TInput) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: customerKeys.all }),
  });
}

export function useSaveCustomer() {
  return useCustomerMutation(({ id, input }: { id?: number; input: CustomerInput }) =>
    id === undefined
      ? api<Customer>("/api/customers", { body: input })
      : api<Customer>(`/api/customers/${id}`, { method: "PUT", body: input }),
  );
}

export function useDeleteCustomer() {
  return useCustomerMutation((id: number) => api<void>(`/api/customers/${id}`, { method: "DELETE" }));
}

/** +919876543210 reads better as +91 98765 43210. Other countries are shown as stored. */
export function formatPhone(phone: string) {
  const indian = /^\+91(\d{5})(\d{5})$/.exec(phone);
  return indian ? `+91 ${indian[1]} ${indian[2]}` : phone;
}
