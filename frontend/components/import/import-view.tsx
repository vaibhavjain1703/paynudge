"use client";

import { CheckCircle2, Download, FileSpreadsheet, Loader2 } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import Link from "next/link";
import { useState } from "react";

import { FormAlert } from "@/components/forms";
import { FileDrop } from "@/components/import/file-drop";
import { ColumnPicker, PreviewSummary, PreviewTable } from "@/components/import/import-preview";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ApiError } from "@/lib/api";
import {
  type ColumnMapping,
  type ImportPreview,
  type ImportResult,
  MAX_FILE_BYTES,
  useCommitImport,
  usePreviewImport,
} from "@/lib/imports";
import { easeOut, fadeUp, useSafeTransition } from "@/lib/motion";

type Stage =
  | { step: "pick" }
  | { step: "review"; file: File; preview: ImportPreview }
  | { step: "done"; result: ImportResult };

export function ImportView() {
  const [stage, setStage] = useState<Stage>({ step: "pick" });
  const [error, setError] = useState<string>();
  const preview = usePreviewImport();
  const commit = useCommitImport();
  const transition = useSafeTransition({ duration: 0.25, ease: easeOut });

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

  const importRows = async (file: File, mapping: ColumnMapping) => {
    setError(undefined);
    try {
      setStage({ step: "done", result: await commit.mutateAsync({ file, mapping }) });
    } catch (failure) {
      setError(messageOf(failure));
    }
  };

  return (
    <div className="space-y-6">
      <motion.div variants={fadeUp} initial="hidden" animate="show" className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">Import from a spreadsheet</h1>
        <p className="text-sm text-muted-foreground">
          Check every row before anything is saved. Importing the same file twice never creates duplicates.
        </p>
      </motion.div>

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

            <div className="sticky bottom-0 -mx-4 space-y-3 border-t bg-background/90 px-4 py-4 backdrop-blur sm:-mx-6 sm:px-6">
              <FormAlert message={error} />
              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="text-sm text-muted-foreground">
                  {stage.preview.summary.invalid > 0
                    ? `${stage.preview.summary.invalid} rows with problems will be skipped. Fix them in the file and import it again.`
                    : "Every row is ready."}
                </p>
                <Button
                  disabled={
                    stage.preview.missingColumns.length > 0 ||
                    stage.preview.summary.valid === 0 ||
                    preview.isPending ||
                    commit.isPending
                  }
                  onClick={() => void importRows(stage.file, stage.preview.mapping)}
                >
                  {commit.isPending && <Loader2 className="motion-safe:animate-spin" aria-hidden="true" />}
                  Import {stage.preview.summary.valid} {stage.preview.summary.valid === 1 ? "row" : "rows"}
                </Button>
              </div>
            </div>
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

function ImportDone({ result, onAgain }: { result: ImportResult; onAgain: () => void }) {
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
            Imported {result.importedRows} {result.importedRows === 1 ? "row" : "rows"}
          </p>
          <p className="text-sm text-muted-foreground">
            {result.customersCreated} new {result.customersCreated === 1 ? "customer" : "customers"} ·{" "}
            {result.customersMatched} already in your list · {result.skippedRows} skipped
          </p>
          <p className="text-xs text-muted-foreground">Dues are added once reminder policies are set up.</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={onAgain}>
            Import another file
          </Button>
          <Button asChild>
            <Link href="/customers">View customers</Link>
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
