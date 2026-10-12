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

/**
 * Every sign-in route answers with this. NEEDS_BUSINESS_NAME means the email is proven but has no account yet:
 * ask for a business name and repeat the same call with it.
 */
export type AuthResult =
  | { status: "SIGNED_IN"; token: string; expiresAt: string; me: Me }
  | { status: "NEEDS_BUSINESS_NAME"; email: string };

type Providers = { emailCode: boolean; googleClientId: string | null };

type EmailCodeSent = { expiresInSeconds: number; resendAfterSeconds: number };

export const queryKeys = {
  me: ["me"] as const,
  providers: ["auth", "providers"] as const,
};

// Mirrors the server rules (AuthDtos.java); the server stays the source of truth.
const email = z.string().trim().pipe(z.email("Enter a valid email address"));

const businessName = z
  .string()
  .trim()
  .min(1, "Enter your business name")
  .max(200, "Keep it under 200 characters");

export const loginSchema = z.object({
  email,
  password: z.string().min(1, "Enter your password"),
});

export const signupSchema = z.object({
  businessName,
  email,
  password: z
    .string()
    .min(8, "Use at least 8 characters")
    .max(72, "Use at most 72 characters"),
});

export const emailSchema = z.object({ email });

export const businessNameSchema = z.object({ businessName });

export type LoginInput = z.infer<typeof loginSchema>;
export type SignupInput = z.infer<typeof signupSchema>;
export type EmailInput = z.infer<typeof emailSchema>;
export type BusinessNameInput = z.infer<typeof businessNameSchema>;

export function useMe() {
  const token = useToken();
  return useQuery({
    queryKey: queryKeys.me,
    queryFn: ({ signal }) => api<Me>("/api/me", { signal }),
    enabled: Boolean(token),
  });
}

/** Which sign-in methods the server has switched on (Google needs a client ID configured). */
export function useProviders() {
  return useQuery({
    queryKey: queryKeys.providers,
    queryFn: ({ signal }) => api<Providers>("/api/auth/providers", { signal, auth: false }),
    staleTime: Infinity,
  });
}

/** Stores the session and goes to the dashboard. Returns false when a business name is still needed. */
export function useCompleteSignIn() {
  const queryClient = useQueryClient();
  const router = useRouter();
  return (result: AuthResult) => {
    if (result.status !== "SIGNED_IN") return false;
    queryClient.setQueryData(queryKeys.me, result.me);
    setToken(result.token);
    router.replace("/dashboard");
    return true;
  };
}

export function useLogin() {
  const complete = useCompleteSignIn();
  return useMutation({
    mutationFn: (input: LoginInput) =>
      api<AuthResult>("/api/auth/login", { body: input, auth: false }),
    onSuccess: complete,
  });
}

export function useSignup() {
  const complete = useCompleteSignIn();
  return useMutation({
    mutationFn: (input: SignupInput) =>
      api<AuthResult>("/api/auth/signup", { body: input, auth: false }),
    onSuccess: complete,
  });
}

export function useSendEmailCode() {
  return useMutation({
    mutationFn: (input: EmailInput) =>
      api<EmailCodeSent>("/api/auth/email/start", { body: input, auth: false }),
  });
}

export function useVerifyEmailCode() {
  return useMutation({
    mutationFn: (input: { email: string; code: string; businessName?: string }) =>
      api<AuthResult>("/api/auth/email/verify", { body: input, auth: false }),
  });
}

export function useGoogleSignIn() {
  return useMutation({
    mutationFn: (input: { idToken: string; businessName?: string }) =>
      api<AuthResult>("/api/auth/google", { body: input, auth: false }),
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
