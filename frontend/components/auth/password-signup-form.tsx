"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";

import { applyServerError, FormAlert, SubmitButton, TextField, useShake } from "@/components/forms";
import { FieldDescription, FieldGroup } from "@/components/ui/field";
import { type SignupInput, signupSchema, useSignup } from "@/lib/auth";

export function PasswordSignupForm() {
  const signup = useSignup();
  const form = useForm<SignupInput>({
    resolver: zodResolver(signupSchema),
    defaultValues: { businessName: "", email: "", password: "" },
  });

  const [formRef, shake] = useShake<HTMLFormElement>();

  const onSubmit = form.handleSubmit(
    (values) =>
      signup.mutateAsync(values).catch((error: unknown) => {
        applyServerError(error, form.setError, { EMAIL_TAKEN: "email" });
        shake();
      }),
    shake,
  );

  return (
    <form ref={formRef} onSubmit={onSubmit} noValidate>
      <FieldGroup>
        <FormAlert message={form.formState.errors.root?.server?.message} />
        <TextField control={form.control} name="businessName" label="Business name" autoComplete="organization" />
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
  );
}
