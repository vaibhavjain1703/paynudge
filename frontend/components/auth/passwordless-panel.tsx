"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { REGEXP_ONLY_DIGITS } from "input-otp";
import { AnimatePresence, motion } from "motion/react";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";

import { GoogleButton } from "@/components/auth/google-button";
import { applyServerError, FormAlert, SubmitButton, TextField, useShake } from "@/components/forms";
import { Button } from "@/components/ui/button";
import { FieldGroup, FieldSeparator } from "@/components/ui/field";
import { InputOTP, InputOTPGroup, InputOTPSlot } from "@/components/ui/input-otp";
import { ApiError } from "@/lib/api";
import {
  type AuthResult,
  type BusinessNameInput,
  businessNameSchema,
  type EmailInput,
  emailSchema,
  useCompleteSignIn,
  useGoogleSignIn,
  useProviders,
  useSendEmailCode,
  useVerifyEmailCode,
} from "@/lib/auth";
import { easeOut } from "@/lib/motion";

type Step =
  | { kind: "start" }
  | { kind: "code"; email: string; resendAt: number }
  | { kind: "business"; email: string; finish: (businessName: string) => Promise<AuthResult> };

const stepMotion = {
  initial: { opacity: 0, x: 12 },
  animate: { opacity: 1, x: 0 },
  exit: { opacity: 0, x: -12 },
  transition: { duration: 0.2, ease: easeOut },
};

function messageOf(error: unknown) {
  return error instanceof ApiError ? error.message : "Something went wrong. Please try again.";
}

/**
 * Sign in or sign up without a password: Google, or a six-digit code sent by email. A new email is asked for its
 * business name before the account is created.
 */
export function PasswordlessPanel() {
  const [step, setStep] = useState<Step>({ kind: "start" });

  return (
    <AnimatePresence mode="wait" initial={false}>
      <motion.div key={step.kind} {...stepMotion}>
        {step.kind === "start" && (
          <StartStep
            onCodeSent={(email, resendAfterSeconds) =>
              setStep({ kind: "code", email, resendAt: Date.now() + resendAfterSeconds * 1000 })
            }
            onNeedsBusiness={(email, finish) => setStep({ kind: "business", email, finish })}
          />
        )}
        {step.kind === "code" && (
          <CodeStep
            email={step.email}
            resendAt={step.resendAt}
            onResent={(resendAt) => setStep({ ...step, resendAt })}
            onBack={() => setStep({ kind: "start" })}
            onNeedsBusiness={(finish) => setStep({ kind: "business", email: step.email, finish })}
          />
        )}
        {step.kind === "business" && (
          <BusinessStep email={step.email} finish={step.finish} onStartOver={() => setStep({ kind: "start" })} />
        )}
      </motion.div>
    </AnimatePresence>
  );
}

function StartStep({
  onCodeSent,
  onNeedsBusiness,
}: {
  onCodeSent: (email: string, resendAfterSeconds: number) => void;
  onNeedsBusiness: (email: string, finish: (businessName: string) => Promise<AuthResult>) => void;
}) {
  const providers = useProviders();
  const google = useGoogleSignIn();
  const complete = useCompleteSignIn();
  const [googleError, setGoogleError] = useState<string>();
  const send = useSendEmailCode();
  const form = useForm<EmailInput>({ resolver: zodResolver(emailSchema), defaultValues: { email: "" } });
  const [formRef, shake] = useShake<HTMLFormElement>();

  const onGoogleCredential = (idToken: string) => {
    setGoogleError(undefined);
    google
      .mutateAsync({ idToken })
      .then((result) => {
        if (!complete(result) && result.status === "NEEDS_BUSINESS_NAME") {
          onNeedsBusiness(result.email, (businessName) => google.mutateAsync({ idToken, businessName }));
        }
      })
      .catch((error: unknown) => setGoogleError(messageOf(error)));
  };

  const onSubmit = form.handleSubmit((values) => {
    const email = values.email.toLowerCase();
    return send
      .mutateAsync({ email })
      .then((sent) => onCodeSent(email, sent.resendAfterSeconds))
      .catch((error: unknown) => {
        // A code is already on its way (for example after going back): just let them enter it.
        if (error instanceof ApiError && error.code === "CODE_RECENTLY_SENT") {
          onCodeSent(email, Number(error.details.retryAfterSeconds ?? 60));
          return;
        }
        applyServerError(error, form.setError);
        shake();
      });
  }, shake);

  const googleClientId = providers.data?.googleClientId;

  return (
    <FieldGroup>
      {googleClientId && (
        <>
          <GoogleButton clientId={googleClientId} onCredential={onGoogleCredential} />
          <FormAlert message={googleError} />
          <FieldSeparator>or</FieldSeparator>
        </>
      )}
      <form ref={formRef} onSubmit={onSubmit} noValidate>
        <FieldGroup>
          <FormAlert message={form.formState.errors.root?.server?.message} />
          <TextField control={form.control} name="email" label="Email" type="email" autoComplete="email" />
          <SubmitButton pending={form.formState.isSubmitting}>Continue with email</SubmitButton>
        </FieldGroup>
      </form>
    </FieldGroup>
  );
}

function useSecondsUntil(deadline: number) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);
  return Math.max(0, Math.ceil((deadline - now) / 1000));
}

function CodeStep({
  email,
  resendAt,
  onResent,
  onBack,
  onNeedsBusiness,
}: {
  email: string;
  resendAt: number;
  onResent: (resendAt: number) => void;
  onBack: () => void;
  onNeedsBusiness: (finish: (businessName: string) => Promise<AuthResult>) => void;
}) {
  const verify = useVerifyEmailCode();
  const send = useSendEmailCode();
  const complete = useCompleteSignIn();
  const [code, setCode] = useState("");
  const [error, setError] = useState<string>();
  const [notice, setNotice] = useState<string>();
  const [scope, shake] = useShake<HTMLDivElement>();
  const secondsLeft = useSecondsUntil(resendAt);

  async function submit(value: string) {
    if (value.length !== 6 || verify.isPending) return;
    setError(undefined);
    try {
      const result = await verify.mutateAsync({ email, code: value });
      if (!complete(result)) {
        onNeedsBusiness((businessName) => verify.mutateAsync({ email, code: value, businessName }));
      }
    } catch (failure) {
      setError(messageOf(failure));
      setCode("");
      shake();
    }
  }

  async function resend() {
    setError(undefined);
    try {
      const sent = await send.mutateAsync({ email });
      onResent(Date.now() + sent.resendAfterSeconds * 1000);
      setCode("");
      setNotice("New code sent. Earlier codes no longer work.");
    } catch (failure) {
      setError(messageOf(failure));
    }
  }

  return (
    <div className="space-y-5">
      <div className="space-y-1">
        <p className="text-sm text-muted-foreground">We emailed a 6-digit code to</p>
        <p className="font-medium break-all">{email}</p>
      </div>

      <form
        onSubmit={(event) => {
          event.preventDefault();
          void submit(code);
        }}
        className="space-y-4"
      >
        <div ref={scope} className="flex justify-center">
          <InputOTP
            maxLength={6}
            value={code}
            onChange={(value) => {
              setCode(value);
              setNotice(undefined);
            }}
            onComplete={(value: string) => void submit(value)}
            pattern={REGEXP_ONLY_DIGITS}
            inputMode="numeric"
            autoComplete="one-time-code"
            autoFocus
            disabled={verify.isPending}
            aria-label="Sign-in code"
          >
            <InputOTPGroup>
              {[0, 1, 2, 3, 4, 5].map((index) => (
                <InputOTPSlot key={index} index={index} aria-invalid={Boolean(error)} className="size-11 text-lg" />
              ))}
            </InputOTPGroup>
          </InputOTP>
        </div>
        <FormAlert message={error} />
        <AnimatePresence initial={false}>
          {notice && (
            <motion.p
              key="notice"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="text-center text-sm text-success"
              role="status"
            >
              {notice}
            </motion.p>
          )}
        </AnimatePresence>
        <SubmitButton pending={verify.isPending}>Verify and continue</SubmitButton>
      </form>

      <div className="flex items-center justify-between text-sm">
        <Button type="button" variant="link" size="sm" className="px-0" onClick={onBack}>
          Use a different email
        </Button>
        <Button
          type="button"
          variant="link"
          size="sm"
          className="px-0 tabular-nums"
          onClick={() => void resend()}
          disabled={secondsLeft > 0 || send.isPending}
        >
          {secondsLeft > 0 ? `Resend in ${secondsLeft}s` : "Resend code"}
        </Button>
      </div>
    </div>
  );
}

function BusinessStep({
  email,
  finish,
  onStartOver,
}: {
  email: string;
  finish: (businessName: string) => Promise<AuthResult>;
  onStartOver: () => void;
}) {
  const complete = useCompleteSignIn();
  const form = useForm<BusinessNameInput>({
    resolver: zodResolver(businessNameSchema),
    defaultValues: { businessName: "" },
  });
  const [formRef, shake] = useShake<HTMLFormElement>();

  const onSubmit = form.handleSubmit(
    (values) =>
      finish(values.businessName)
        .then(complete)
        .catch((error: unknown) => {
          applyServerError(error, form.setError);
          shake();
        }),
    shake,
  );

  return (
    <form ref={formRef} onSubmit={onSubmit} noValidate>
      <FieldGroup>
        <div className="space-y-1">
          <p className="font-medium">Welcome to PayNudge</p>
          <p className="text-sm text-muted-foreground">
            <span className="font-medium break-all text-foreground">{email}</span> is new here. What is your business
            called?
          </p>
        </div>
        <FormAlert message={form.formState.errors.root?.server?.message} />
        <TextField control={form.control} name="businessName" label="Business name" autoComplete="organization" />
        <SubmitButton pending={form.formState.isSubmitting}>Create account</SubmitButton>
        <Button type="button" variant="link" size="sm" onClick={onStartOver}>
          Start over
        </Button>
      </FieldGroup>
    </form>
  );
}
