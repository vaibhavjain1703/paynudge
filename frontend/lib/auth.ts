"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { z } from "zod";

import { api } from "@/lib/api";
import { setToken, useToken } from "@/lib/session";

export type Business = {
  id: number;
  name: string;
  timezone: string;
  quietStart: string;
  quietEnd: string;
  senderName: string | null;
  dailySendCap: number;
};

export type Me = {
  userId: number;
  email: string;
  business: Business;
};

type AuthResponse = {
  token: string;
  expiresAt: string;
  me: Me;
};

export const queryKeys = {
  me: ["me"] as const,
};

// Mirrors the server rules (AuthDtos.java); the server stays the source of truth.
export const loginSchema = z.object({
  email: z.string().trim().pipe(z.email("Enter a valid email address")),
  password: z.string().min(1, "Enter your password"),
});

export const signupSchema = z.object({
  businessName: z
    .string()
    .trim()
    .min(1, "Enter your business name")
    .max(200, "Keep it under 200 characters"),
  email: z.string().trim().pipe(z.email("Enter a valid email address")),
  password: z
    .string()
    .min(8, "Use at least 8 characters")
    .max(72, "Use at most 72 characters"),
});

export type LoginInput = z.infer<typeof loginSchema>;
export type SignupInput = z.infer<typeof signupSchema>;

export function useMe() {
  const token = useToken();
  return useQuery({
    queryKey: queryKeys.me,
    queryFn: ({ signal }) => api<Me>("/api/me", { signal }),
    enabled: Boolean(token),
  });
}

function useSignedIn() {
  const queryClient = useQueryClient();
  const router = useRouter();
  return (response: AuthResponse) => {
    queryClient.setQueryData(queryKeys.me, response.me);
    setToken(response.token);
    router.replace("/dashboard");
  };
}

export function useLogin() {
  const onSignedIn = useSignedIn();
  return useMutation({
    mutationFn: (input: LoginInput) =>
      api<AuthResponse>("/api/auth/login", { body: input, auth: false }),
    onSuccess: onSignedIn,
  });
}

export function useSignup() {
  const onSignedIn = useSignedIn();
  return useMutation({
    mutationFn: (input: SignupInput) =>
      api<AuthResponse>("/api/auth/signup", { body: input, auth: false }),
    onSuccess: onSignedIn,
  });
}

export function useLogout() {
  const queryClient = useQueryClient();
  const router = useRouter();
  return () => {
    setToken(null);
    queryClient.clear();
    router.replace("/login");
  };
}
