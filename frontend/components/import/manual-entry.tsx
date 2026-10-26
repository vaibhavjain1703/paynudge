"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { BellOff, BellRing, CheckCircle2, CopyCheck, Loader2 } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import Link from "next/link";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";

import { applyServerError, FormAlert, TextField, useShake } from "@/components/forms";
import { PolicyPicker } from "@/components/import/policy-picker";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { FieldDescription, FieldGroup, FieldLegend, FieldSet } from "@/components/ui/field";
import { ApiError } from "@/lib/api";
import { useMe } from "@/lib/auth";
import { loadImportDraft, saveImportDraft } from "@/lib/import-draft";
import {
  DUE_EXISTS,
  type ManualEntryInput,
  type ManualEntryResult,
  manualEntrySchema,
  useAddDue,
} from "@/lib/imports";
import { formatDate, formatDateTime, formatPaise } from "@/lib/money";
import { easeOut, expand, swap, useSafeTransition } from "@/lib/motion";
import type { Policy } from "@/lib/policies";

const EMPTY: ManualEntryInput = { name: "", email: "", phone: "", amount: "", dueDate: "" };

type PolicyProps = {
  policies?: Policy[];
  policiesLoading: boolean;
  policyId?: number;
  onPolicyChange: (id: number) => void;
};

/**
 * One customer and what they owe, typed in by hand: the same checks as a CSV row, the same customer when the email
 * (or phone) is already in the list, and the same due is never added twice.
 */
export function ManualEntry(props: PolicyProps) {
  const [added, setAdded] = useState<ManualEntryResult>();
  const transition = useSafeTransition({ duration: 0.15, ease: easeOut });
  return (
    <AnimatePresence mode="wait" initial={false}>
      {added ? (
        <motion.div key="done" {...swap} transition={transition}>
          <ManualDone result={added} onAgain={() => setAdded(undefined)} />
        </motion.div>
      ) : (
        <motion.div key="form" {...swap} transition={transition}>
          <ManualForm {...props} onAdded={setAdded} />
        </motion.div>
      )}
    </AnimatePresence>
  );
}

function ManualForm({
  policies,
  policiesLoading,
  policyId,
  onPolicyChange,
  onAdded,
}: PolicyProps & { onAdded: (result: ManualEntryResult) => void }) {
  const add = useAddDue();
  const [scope, shake] = useShake<HTMLFormElement>();
  const [existingDue, setExistingDue] = useState<number>();
  const form = useForm<ManualEntryInput>({
    resolver: zodResolver(manualEntrySchema),
    defaultValues: { ...EMPTY, ...loadImportDraft()?.entry },
  });
  const transition = useSafeTransition({ duration: 0.25, ease: easeOut });

  // What is typed survives a visit to another page; adding the due lets it go.
  useEffect(
    () =>
      form.subscribe({
        formState: { values: true },
        callback: ({ values }) => saveImportDraft({ entry: values }),
      }),
    [form],
  );

  const onSubmit = form.handleSubmit(
    async (entry) => {
      if (policyId === undefined) return;
      setExistingDue(undefined);
      try {
        const result = await add.mutateAsync({ entry, policyId });
        saveImportDraft({ entry: undefined }); // even when the owner has already left this page
        onAdded(result);
      } catch (error) {
        if (error instanceof ApiError && error.code === DUE_EXISTS) {
          setExistingDue(Number(error.details.dueId));
        } else if (error instanceof ApiError && typeof error.details.policyId === "string") {
          form.setError("root.server", { message: error.details.policyId });
        } else {
          applyServerError(error, form.setError);
        }
        shake();
      }
    },
    () => shake(),
  );

  return (
    <form ref={scope} onSubmit={onSubmit} noValidate className="space-y-8">
      <div className="grid gap-8 md:grid-cols-2">
        <FieldSet>
          <FieldLegend>Customer</FieldLegend>
          <FieldDescription>Already one of your customers? Use the same email and the due is added to them.</FieldDescription>
          <FieldGroup>
            <TextField control={form.control} name="name" label="Name" autoComplete="off" />
            <TextField
              control={form.control}
              name="email"
              label="Email"
              type="email"
              autoComplete="off"
              description="Reminders go to this email."
            />
            <TextField
              control={form.control}
              name="phone"
              label="Phone (optional)"
              inputMode="numeric"
              autoComplete="off"
              description="Indian mobiles can skip +91."
            />
          </FieldGroup>
        </FieldSet>
        <FieldSet>
          <FieldLegend>Due</FieldLegend>
          <FieldDescription>What they owe, and by when.</FieldDescription>
          <FieldGroup>
            <TextField
              control={form.control}
              name="amount"
              label="Amount (₹)"
              inputMode="decimal"
              autoComplete="off"
              placeholder="8,500"
            />
            <TextField control={form.control} name="dueDate" label="Due date" type="date" />
          </FieldGroup>
        </FieldSet>
      </div>

      <PolicyPicker
        policies={policies}
        loading={policiesLoading}
        value={policyId}
        onChange={onPolicyChange}
        disabled={add.isPending}
        description="This due follows this policy. It stops the moment they pay."
      />

      <div className="space-y-3 border-t pt-4">
        <FormAlert message={form.formState.errors.root?.server?.message} />
        <AnimatePresence initial={false}>
          {existingDue !== undefined && (
            <motion.div key="exists" {...expand} transition={transition} className="overflow-hidden">
              <Alert>
                <CopyCheck aria-hidden="true" />
                <AlertDescription>
                  <span>
                    This due is already in your list, so it was not added again.{" "}
                    <Link
                      href={`/dues/${existingDue}`}
                      className="font-medium text-primary underline-offset-4 hover:underline"
                    >
                      Open it
                    </Link>
                  </span>
                </AlertDescription>
              </Alert>
            </motion.div>
          )}
        </AnimatePresence>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="text-sm text-muted-foreground">Reminders are scheduled as soon as you add it.</p>
          <Button type="submit" disabled={add.isPending || policyId === undefined}>
            {add.isPending && <Loader2 className="motion-safe:animate-spin" aria-hidden="true" />}
            Add due
          </Button>
        </div>
      </div>
    </form>
  );
}

function ManualDone({ result, onAgain }: { result: ManualEntryResult; onAgain: () => void }) {
  const me = useMe();
  const timeZone = me.data?.business.timezone ?? "Asia/Kolkata";
  return (
    <Card>
      <CardContent className="flex flex-col items-center gap-4 py-8 text-center">
        <motion.div
          initial={{ scale: 0.5, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ type: "spring", bounce: 0.5, duration: 0.4 }}
          className="rounded-full bg-success/15 p-3 text-success"
        >
          <CheckCircle2 className="size-6" aria-hidden="true" />
        </motion.div>
        <div className="space-y-1" role="status">
          <p className="text-lg font-semibold">
            Added {formatPaise(result.amountPaise)} for {result.customerName}
          </p>
          <p className="text-sm text-muted-foreground">
            Due {formatDate(result.dueDate)} ·{" "}
            {result.newCustomer ? "New customer" : "Already one of your customers"}
          </p>
          {result.optedOut ? (
            <p className="flex items-center justify-center gap-1.5 text-sm">
              <BellOff className="size-4 text-muted-foreground" aria-hidden="true" />
              {result.customerName} unsubscribed from reminders, so none will go out.
            </p>
          ) : (
            result.firstReminderAt && (
              <p className="flex items-center justify-center gap-1.5 text-sm">
                <BellRing className="size-4 text-primary" aria-hidden="true" />
                First reminder goes out {formatDateTime(result.firstReminderAt, timeZone)}
              </p>
            )
          )}
        </div>
        <div className="flex flex-wrap justify-center gap-2">
          <Button variant="outline" onClick={onAgain}>
            Add another
          </Button>
          <Button variant="outline" asChild>
            <Link href={`/dues/${result.dueId}`}>Open this due</Link>
          </Button>
          <Button asChild>
            <Link href="/dues">View dues</Link>
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
