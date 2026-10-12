"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";

import { applyServerError, FormAlert, SubmitButton, TextField, useShake } from "@/components/forms";
import { FieldGroup } from "@/components/ui/field";
import { type LoginInput, loginSchema, useLogin } from "@/lib/auth";

export function PasswordLoginForm() {
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

  return (
    <form ref={formRef} onSubmit={onSubmit} noValidate>
      <FieldGroup>
        <FormAlert message={form.formState.errors.root?.server?.message} />
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
  );
}
