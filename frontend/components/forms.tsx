"use client";

import { Loader2 } from "lucide-react";
import {
  Controller,
  type Control,
  type FieldPath,
  type FieldValues,
  type UseFormSetError,
} from "react-hook-form";

import { Button } from "@/components/ui/button";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { ApiError } from "@/lib/api";

export function TextField<T extends FieldValues>({
  control,
  name,
  label,
  type = "text",
  autoComplete,
}: {
  control: Control<T>;
  name: FieldPath<T>;
  label: string;
  type?: "text" | "email" | "password";
  autoComplete?: string;
}) {
  return (
    <Controller
      control={control}
      name={name}
      render={({ field, fieldState }) => (
        <Field data-invalid={fieldState.invalid}>
          <FieldLabel htmlFor={name}>{label}</FieldLabel>
          <Input
            {...field}
            id={name}
            type={type}
            autoComplete={autoComplete}
            aria-invalid={fieldState.invalid}
          />
          {fieldState.invalid && <FieldError errors={[fieldState.error]} />}
        </Field>
      )}
    />
  );
}

export function SubmitButton({ pending, children }: { pending: boolean; children: React.ReactNode }) {
  return (
    <Button type="submit" size="lg" className="w-full" disabled={pending}>
      {pending && <Loader2 className="motion-safe:animate-spin" aria-hidden="true" />}
      {children}
    </Button>
  );
}

/**
 * Puts a server error where the user will see it: field errors (VALIDATION_FAILED details, or a code tied to one
 * field) go under that field; anything else becomes the form-level message.
 */
export function applyServerError<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fieldForCode: Partial<Record<string, FieldPath<T>>> = {},
) {
  if (!(error instanceof ApiError)) {
    setError("root.server", { message: "Something went wrong. Please try again." });
    return;
  }
  const field = fieldForCode[error.code];
  if (field) {
    setError(field, { message: error.message });
    return;
  }
  const fieldErrors = Object.entries(error.details);
  if (error.code === "VALIDATION_FAILED" && fieldErrors.length > 0) {
    for (const [name, message] of fieldErrors) {
      setError(name as FieldPath<T>, { message: String(message) });
    }
    return;
  }
  setError("root.server", { message: error.message });
}
