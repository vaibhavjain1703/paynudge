"use client";

import { BellRing, CheckCircle2, Download, FileSpreadsheet, Loader2 } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import Link from "next/link";
import { useState } from "react";

import { FormAlert } from "@/components/forms";
import { FileDrop } from "@/components/import/file-drop";
import { ColumnPicker, PreviewSummary, PreviewTable } from "@/components/import/import-preview";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiError } from "@/lib/api";
import { useMe } from "@/lib/auth";
import {
  type ColumnMapping,
  type ImportPreview,
  type ImportResult,
  MAX_FILE_BYTES,
  useCommitImport,
  usePreviewImport,
} from "@/lib/imports";
import { formatDateTime } from "@/lib/money";
import { easeOut, useSafeTransition } from "@/lib/motion";
import { type Policy, summarizePolicy, usePolicies } from "@/lib/policies";

type Stage =
  | { step: "pick" }
  | { step: "review"; file: File; preview: ImportPreview }
  | { step: "done"; result: ImportResult };

export function ImportView() {
  const [stage, setStage] = useState<Stage>({ step: "pick" });
  const [error, setError] = useState<string>();
  const [chosenPolicy, setChosenPolicy] = useState<number>();
  const preview = usePreviewImport();
  const commit = useCommitImport();
  const policies = usePolicies();
  const transition = useSafeTransition({ duration: 0.15, ease: easeOut });
  const policyId = chosenPolicy ?? policies.data?.[0]?.id;

  const check = async (file: File, mapping?: ColumnMapping) => {
    setError(undefined);
    if (file.size > MAX_FILE_BYTES) {
      setError("This file is larger than 2 MB. Split it into smaller files.");
      return;
    }
    try {
      setStage({ step: "review", file, preview: await preview.mutateAsync({ file, mapping }) });
    } catch (failure) {
      setError(messageOf(failure));
      if (!mapping) setStage({ step: "pick" });
    }
  };

  const importRows = async (file: File, mapping: ColumnMapping, policy: number) => {
    setError(undefined);
    try {
      setStage({ step: "done", result: await commit.mutateAsync({ file, mapping, policyId: policy }) });
    } catch (failure) {
      setError(messageOf(failure));
    }
  };

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">Import dues from a spreadsheet</h1>
        <p className="text-sm text-muted-foreground">
          Check every row before anything is saved. Importing the same file twice never reminds anyone twice.
        </p>
      </div>

      <AnimatePresence mode="wait" initial={false}>
        {stage.step === "pick" && (
          <motion.div key="pick" {...stepMotion} transition={transition} className="space-y-4">
            <FileDrop onFile={(file) => void check(file)} busy={preview.isPending} />
            <FormAlert message={error} />
            <p className="text-sm text-muted-foreground">
              Exporting from Excel or Google Sheets? Save as CSV first.{" "}
              <a
                href="/samples/paynudge-sample-dues.csv"
                download
                className="inline-flex items-center gap-1 font-medium text-primary underline-offset-4 hover:underline"
              >
                <Download className="size-3.5" aria-hidden="true" />
                Download a sample file
              </a>
            </p>
          </motion.div>
        )}

        {stage.step === "review" && (
          <motion.div key="review" {...stepMotion} transition={transition} className="space-y-6">
            <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border p-3">
              <div className="flex min-w-0 items-center gap-2 text-sm">
                <FileSpreadsheet className="size-4 shrink-0 text-muted-foreground" aria-hidden="true" />
                <span className="truncate font-medium">{stage.file.name}</span>
                {preview.isPending && <Loader2 className="size-4 motion-safe:animate-spin" aria-label="Checking" />}
              </div>
              <Button variant="ghost" size="sm" onClick={() => setStage({ step: "pick" })}>
                Choose another file
              </Button>
            </div>

            <ColumnPicker
              preview={stage.preview}
              disabled={preview.isPending || commit.isPending}
              onChange={(mapping) => void check(stage.file, mapping)}
            />

            {stage.preview.missingColumns.length === 0 && (
              <>
                <PreviewSummary preview={stage.preview} />
                <PreviewTable preview={stage.preview} />
              </>
            )}

            <PolicyPicker
              policies={policies.data}
              loading={policies.isPending}
              value={policyId}
              onChange={setChosenPolicy}
              disabled={commit.isPending}
            />

            <ImportBar
              preview={stage.preview}
              error={error}
              busy={preview.isPending || commit.isPending || policyId === undefined}
              importing={commit.isPending}
              onImport={() => policyId !== undefined && void importRows(stage.file, stage.preview.mapping, policyId)}
            />
          </motion.div>
        )}

        {stage.step === "done" && (
          <motion.div key="done" {...stepMotion} transition={transition}>
            <ImportDone result={stage.result} onAgain={() => setStage({ step: "pick" })} />
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function PolicyPicker({
  policies,
  loading,
  value,
  onChange,
  disabled,
}: {
  policies?: Policy[];
  loading: boolean;
  value?: number;
  onChange: (id: number) => void;
  disabled: boolean;
}) {
  const selected = policies?.find((policy) => policy.id === value);
  return (
    <section aria-labelledby="policy-heading" className="space-y-3">
      <div className="space-y-1">
        <h2 id="policy-heading" className="font-medium">
          Reminders
        </h2>
        <p className="text-sm text-muted-foreground">
          Every imported due follows this policy. It stops the moment someone pays.{" "}
          <Link href="/policies" className="font-medium text-primary underline-offset-4 hover:underline">
            See the policies
          </Link>
        </p>
      </div>
      {loading || !policies ? (
        <Skeleton className="h-8 w-full max-w-sm" />
      ) : (
        <div className="space-y-1.5">
          <label htmlFor="policy" className="sr-only">
            Reminder policy
          </label>
          <select
            id="policy"
            value={value ?? ""}
            disabled={disabled}
            onChange={(event) => onChange(Number(event.target.value))}
            className="h-8 w-full max-w-sm rounded-lg border border-input bg-transparent px-2 text-base outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 md:text-sm dark:bg-input/30 [&>option]:bg-popover [&>option]:text-popover-foreground"
          >
            {policies.map((policy) => (
              <option key={policy.id} value={policy.id}>
                {policy.name}
              </option>
            ))}
          </select>
          {selected && <p className="text-sm text-muted-foreground">{summarizePolicy(selected)}</p>}
        </div>
      )}
    </section>
  );
}

function ImportBar({
  preview,
  error,
  busy,
  importing,
  onImport,
}: {
  preview: ImportPreview;
  error?: string;
  busy: boolean;
  importing: boolean;
  onImport: () => void;
}) {
  const { summary } = preview;
  const ready = summary.valid - summary.alreadyImported;
  const notes = [
    summary.invalid > 0 && `${summary.invalid} with problems will be skipped; fix them in the file and import it again.`,
    summary.alreadyImported > 0 && `${summary.alreadyImported} were imported before and are skipped.`,
  ].filter(Boolean);
  return (
    <div className="sticky bottom-0 -mx-4 space-y-3 border-t bg-background/90 px-4 py-4 backdrop-blur sm:-mx-6 sm:px-6">
      <FormAlert message={error} />
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted-foreground">
          {preview.missingColumns.length > 0
            ? "Choose the missing columns first."
            : ready === 0 && summary.alreadyImported > 0
              ? "Every valid row was imported before. Nothing new to add."
              : notes.length > 0
                ? `Rows: ${notes.join(" ")}`
                : "Every row is ready."}
        </p>
        <Button disabled={preview.missingColumns.length > 0 || ready === 0 || busy} onClick={onImport}>
          {importing && <Loader2 className="motion-safe:animate-spin" aria-hidden="true" />}
          Import {ready} {ready === 1 ? "due" : "dues"}
        </Button>
      </div>
    </div>
  );
}

function ImportDone({ result, onAgain }: { result: ImportResult; onAgain: () => void }) {
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
            {result.importedRows > 0
              ? `Imported ${result.importedRows} ${result.importedRows === 1 ? "due" : "dues"}`
              : "Nothing new to import"}
          </p>
          <p className="text-sm text-muted-foreground">
            {result.customersCreated} new {result.customersCreated === 1 ? "customer" : "customers"} ·{" "}
            {result.customersMatched} already in your list · {result.alreadyImported} imported before ·{" "}
            {result.skippedRows} with problems
          </p>
          {result.firstReminderAt && (
            <p className="flex items-center justify-center gap-1.5 text-sm">
              <BellRing className="size-4 text-primary" aria-hidden="true" />
              First reminder goes out {formatDateTime(result.firstReminderAt, timeZone)}
            </p>
          )}
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={onAgain}>
            Import another file
          </Button>
          <Button asChild>
            <Link href="/dues">View dues</Link>
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}

const stepMotion = {
  initial: { opacity: 0, y: 8 },
  animate: { opacity: 1, y: 0 },
  exit: { opacity: 0, y: -8 },
};

function messageOf(error: unknown) {
  return error instanceof ApiError ? error.message : "Something went wrong. Please try again.";
}
