"use client";

import { Loader2 } from "lucide-react";
import { AnimatePresence, motion, useAnimate } from "motion/react";
import {
  Controller,
  type Control,
  type FieldPath,
  type FieldValues,
  type UseFormSetError,
} from "react-hook-form";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { ApiError } from "@/lib/api";
import { easeOut, expand, usePrefersReducedMotion, useSafeTransition } from "@/lib/motion";

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
  const transition = useSafeTransition({ duration: 0.2, ease: easeOut });
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
          <AnimatePresence initial={false}>
            {fieldState.invalid && (
              <motion.div key="error" {...expand} transition={transition} className="overflow-hidden">
                <FieldError errors={[fieldState.error]} />
              </motion.div>
            )}
          </AnimatePresence>
        </Field>
      )}
    />
  );
}

/** The form-level error (wrong credentials, server down). Slides open instead of pushing the form down at once. */
export function FormAlert({ message }: { message?: string }) {
  const transition = useSafeTransition({ duration: 0.25, ease: easeOut });
  return (
    <AnimatePresence initial={false}>
      {message && (
        <motion.div key="alert" {...expand} transition={transition} className="overflow-hidden">
          <Alert variant="destructive">
            <AlertDescription>{message}</AlertDescription>
          </Alert>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

/** A short horizontal shake for a rejected submit. Attach `scope` to the element to shake. */
export function useShake<T extends HTMLElement>() {
  const [scope, animate] = useAnimate<T>();
  const reduced = usePrefersReducedMotion();
  const shake = () => {
    if (reduced || !scope.current) return;
    animate(scope.current, { x: [0, -6, 6, -4, 4, 0] }, { duration: 0.35, ease: "easeInOut" });
  };
  return [scope, shake] as const;
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
