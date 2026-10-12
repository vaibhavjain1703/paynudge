"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";

import { AuthCard } from "@/components/auth/auth-card";
import { applyServerError, SubmitButton, TextField } from "@/components/forms";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { FieldDescription, FieldGroup } from "@/components/ui/field";
import { type SignupInput, signupSchema, useSignup } from "@/lib/auth";

export function SignupForm() {
  const signup = useSignup();
  const form = useForm<SignupInput>({
    resolver: zodResolver(signupSchema),
    defaultValues: { businessName: "", email: "", password: "" },
  });

  const onSubmit = form.handleSubmit((values) =>
    signup
      .mutateAsync(values)
      .catch((error: unknown) => applyServerError(error, form.setError, { EMAIL_TAKEN: "email" })),
  );

  const serverError = form.formState.errors.root?.server?.message;

  return (
    <AuthCard
      title="Create your account"
      description="Set up your business once. Reminders run on their own after that."
      footer={{ prompt: "Already have an account?", href: "/login", label: "Log in" }}
    >
      <form onSubmit={onSubmit} noValidate>
        <FieldGroup>
          {serverError && (
            <Alert variant="destructive">
              <AlertDescription>{serverError}</AlertDescription>
            </Alert>
          )}
          <TextField
            control={form.control}
            name="businessName"
            label="Business name"
            autoComplete="organization"
          />
          <TextField control={form.control} name="email" label="Email" type="email" autoComplete="email" />
          <TextField
            control={form.control}
            name="password"
            label="Password"
            type="password"
            autoComplete="new-password"
          />
          <FieldDescription>At least 8 characters.</FieldDescription>
          <SubmitButton pending={form.formState.isSubmitting}>Create account</SubmitButton>
        </FieldGroup>
      </form>
    </AuthCard>
  );
}
