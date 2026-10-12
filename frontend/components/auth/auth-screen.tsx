"use client";

import { AnimatePresence, motion } from "motion/react";
import { useState } from "react";

import { AuthCard } from "@/components/auth/auth-card";
import { PasswordLoginForm } from "@/components/auth/password-login-form";
import { PasswordSignupForm } from "@/components/auth/password-signup-form";
import { PasswordlessPanel } from "@/components/auth/passwordless-panel";
import { Button } from "@/components/ui/button";
import { easeOut } from "@/lib/motion";

const COPY = {
  login: {
    title: "Welcome back",
    description: "Sign in with Google or a code we email you. New here? The same steps create your account.",
    footer: { prompt: "New to PayNudge?", href: "/signup", label: "Create an account" },
  },
  signup: {
    title: "Create your account",
    description: "No password needed: continue with Google or a code we email you.",
    footer: { prompt: "Already have an account?", href: "/login", label: "Log in" },
  },
} as const;

/** Sign-in and sign-up share one screen: passwordless first, with a password as the alternative. */
export function AuthScreen({ intent }: { intent: "login" | "signup" }) {
  const [method, setMethod] = useState<"passwordless" | "password">("passwordless");
  const copy = COPY[intent];

  return (
    <AuthCard title={copy.title} description={copy.description} footer={copy.footer}>
      <AnimatePresence mode="wait" initial={false}>
        <motion.div
          key={method}
          initial={{ opacity: 0, y: 6 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -6 }}
          transition={{ duration: 0.2, ease: easeOut }}
        >
          {method === "passwordless" ? (
            <PasswordlessPanel />
          ) : intent === "login" ? (
            <PasswordLoginForm />
          ) : (
            <PasswordSignupForm />
          )}
        </motion.div>
      </AnimatePresence>
      <div className="mt-3 flex justify-center">
        <Button
          type="button"
          variant="link"
          size="sm"
          onClick={() => setMethod(method === "passwordless" ? "password" : "passwordless")}
        >
          {method === "passwordless" ? "Use a password instead" : "Use Google or an email code instead"}
        </Button>
      </div>
    </AuthCard>
  );
}
