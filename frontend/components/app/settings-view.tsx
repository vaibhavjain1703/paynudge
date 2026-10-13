"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Check, Loader2 } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";

import { PageSkeleton } from "@/components/app/app-shell";
import { applyServerError, FormAlert, SelectField, TextField, useShake } from "@/components/forms";
import { ErrorState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { FieldGroup } from "@/components/ui/field";
import { type Business, useMe } from "@/lib/auth";
import {
  type BusinessSettings,
  type BusinessSettingsInput,
  businessSettingsSchema,
  settingsFormValues,
  useUpdateBusiness,
} from "@/lib/business";

export function SettingsView() {
  const me = useMe();

  if (me.isPending) return <PageSkeleton />;
  if (me.isError) {
    return <ErrorState title="Couldn't load your settings" error={me.error} onRetry={() => me.refetch()} />;
  }
  return <SettingsForm business={me.data.business} email={me.data.email} />;
}

function SettingsForm({ business, email }: { business: Business; email: string }) {
  const update = useUpdateBusiness();
  const [saved, setSaved] = useState(false);
  const [scope, shake] = useShake<HTMLFormElement>();
  const timezones = useTimezoneOptions(business.timezone);
  const form = useForm<BusinessSettingsInput, unknown, BusinessSettings>({
    resolver: zodResolver(businessSettingsSchema),
    defaultValues: settingsFormValues(business),
  });
  const { isDirty } = form.formState;

  useEffect(() => {
    if (!saved) return;
    const timer = setTimeout(() => setSaved(false), 2500);
    return () => clearTimeout(timer);
  }, [saved]);

  const onSubmit = form.handleSubmit(
    async (settings) => {
      try {
        const updated = await update.mutateAsync(settings);
        form.reset(settingsFormValues(updated));
        setSaved(true);
      } catch (error) {
        applyServerError(error, form.setError);
        shake();
      }
    },
    () => shake(),
  );

  return (
    <form ref={scope} onSubmit={onSubmit} noValidate>
      <div className="space-y-6">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight">Settings</h1>
          <p className="text-sm text-muted-foreground">How your business appears in reminders, and when they may go out.</p>
        </div>

        <div>
          <Card>
            <CardHeader>
              <CardTitle>Business profile</CardTitle>
              <CardDescription>Signed in as {email}</CardDescription>
            </CardHeader>
            <CardContent>
              <FieldGroup className="grid gap-5 sm:grid-cols-2">
                <TextField control={form.control} name="name" label="Business name" autoComplete="organization" />
                <TextField
                  control={form.control}
                  name="senderName"
                  label="Sender name (optional)"
                  description="Shown as who the reminder is from, e.g. Asha Ma'am. Defaults to the business name."
                />
              </FieldGroup>
            </CardContent>
          </Card>
        </div>

        <div>
          <Card>
            <CardHeader>
              <CardTitle>Sending rules</CardTitle>
              <CardDescription>Reminders that fall inside quiet hours wait until they end.</CardDescription>
            </CardHeader>
            <CardContent>
              <FieldGroup className="grid gap-5 sm:grid-cols-2">
                <div className="sm:col-span-2">
                  <SelectField control={form.control} name="timezone" label="Timezone" options={timezones} />
                </div>
                <TextField
                  control={form.control}
                  name="quietStart"
                  label="Quiet hours start"
                  type="time"
                />
                <TextField
                  control={form.control}
                  name="quietEnd"
                  label="Quiet hours end"
                  type="time"
                  description="Can be the next morning, e.g. 21:00 to 09:00."
                />
                <TextField
                  control={form.control}
                  name="dailySendCap"
                  label="Daily send limit"
                  inputMode="numeric"
                  description="The most reminder emails sent for you in one day (1 to 500)."
                />
              </FieldGroup>
            </CardContent>
          </Card>
        </div>

        <div className="space-y-4">
          <FormAlert message={form.formState.errors.root?.server?.message} />
          <div className="flex items-center justify-end gap-3">
            <AnimatePresence>
              {saved && !isDirty && (
                <motion.p
                  key="saved"
                  role="status"
                  initial={{ opacity: 0, x: 6 }}
                  animate={{ opacity: 1, x: 0 }}
                  exit={{ opacity: 0 }}
                  transition={{ duration: 0.2 }}
                  className="flex items-center gap-1.5 text-sm text-success"
                >
                  <Check className="size-4" aria-hidden="true" />
                  Saved
                </motion.p>
              )}
            </AnimatePresence>
            <Button
              type="button"
              variant="ghost"
              disabled={!isDirty || update.isPending}
              onClick={() => form.reset()}
            >
              Discard changes
            </Button>
            <Button type="submit" disabled={!isDirty || update.isPending}>
              {update.isPending && <Loader2 className="motion-safe:animate-spin" aria-hidden="true" />}
              Save changes
            </Button>
          </div>
        </div>
      </div>
    </form>
  );
}

/**
 * Labels for every IANA timezone the browser knows, e.g. "Asia/Kolkata (GMT+5:30)". Building ~400 formatters blocks
 * the page for ~100 ms, so it happens once per session, in idle time after Settings first paints.
 */
let timezoneLabels: Map<string, string> | null = null;

function buildTimezoneLabels() {
  const zones = typeof Intl.supportedValuesOf === "function" ? Intl.supportedValuesOf("timeZone") : [];
  const now = new Date();
  return new Map(zones.map((zone) => [zone, `${zone.replaceAll("_", " ")} (${offsetOf(zone, now)})`]));
}

function useTimezoneOptions(current: string) {
  const [labels, setLabels] = useState(timezoneLabels);

  useEffect(() => {
    if (labels) return;
    const build = () => {
      timezoneLabels ??= buildTimezoneLabels();
      setLabels(timezoneLabels);
    };
    if (typeof window.requestIdleCallback === "function") {
      const id = window.requestIdleCallback(build, { timeout: 500 });
      return () => window.cancelIdleCallback(id);
    }
    const id = window.setTimeout(build, 50);
    return () => window.clearTimeout(id);
  }, [labels]);

  return useMemo(() => {
    const zones = labels ? [...labels.keys()] : [];
    const all = zones.includes(current) ? zones : [current, ...zones];
    return all.map((zone) => ({ value: zone, label: labels?.get(zone) ?? zone.replaceAll("_", " ") }));
  }, [labels, current]);
}

function offsetOf(zone: string, at: Date) {
  try {
    const parts = new Intl.DateTimeFormat("en-US", { timeZone: zone, timeZoneName: "shortOffset" }).formatToParts(at);
    return parts.find((part) => part.type === "timeZoneName")?.value ?? "GMT";
  } catch {
    return "GMT";
  }
}
