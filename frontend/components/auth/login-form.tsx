"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";

import { AuthCard } from "@/components/auth/auth-card";
import { applyServerError, FormAlert, SubmitButton, TextField, useShake } from "@/components/forms";
import { FieldGroup } from "@/components/ui/field";
import { type LoginInput, loginSchema, useLogin } from "@/lib/auth";

export function LoginForm() {
  const login = useLogin();
  const form = useForm<LoginInput>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  const [formRef, shake] = useShake<HTMLFormElement>();

  const onSubmit = form.handleSubmit(
    (values) =>
      login.mutateAsync(values).catch((error: unknown) => {
        applyServerError(error, form.setError);
        shake();
      }),
    shake,
  );

  const serverError = form.formState.errors.root?.server?.message;

  return (
    <AuthCard
      title="Welcome back"
      description="Log in to see who has paid and what goes out next."
      footer={{ prompt: "New to PayNudge?", href: "/signup", label: "Create an account" }}
    >
      <form ref={formRef} onSubmit={onSubmit} noValidate>
        <FieldGroup>
          <FormAlert message={serverError} />
          <TextField control={form.control} name="email" label="Email" type="email" autoComplete="email" />
          <TextField
            control={form.control}
            name="password"
            label="Password"
            type="password"
            autoComplete="current-password"
          />
          <SubmitButton pending={form.formState.isSubmitting}>Log in</SubmitButton>
        </FieldGroup>
      </form>
    </AuthCard>
  );
}
