"use client";

import { FileSpreadsheet, UserPlus } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useRef, useState } from "react";

import { CsvImport } from "@/components/import/csv-import";
import { ManualEntry } from "@/components/import/manual-entry";
import { type ImportMode, loadImportDraft, saveImportDraft } from "@/lib/import-draft";
import { easeOut, swap, useSafeTransition } from "@/lib/motion";
import { usePolicies } from "@/lib/policies";
import { cn } from "@/lib/utils";

const MODES = [
  {
    mode: "csv",
    label: "Upload a CSV",
    icon: FileSpreadsheet,
    lead: "Check every row before anything is saved. Importing the same file twice never reminds anyone twice.",
  },
  {
    mode: "manual",
    label: "Add manually",
    icon: UserPlus,
    lead: "Type in one customer and what they owe. Adding the same due twice never reminds anyone twice.",
  },
] as const;

/** Two ways to add dues: a spreadsheet of many, or one typed in by hand. Both follow the policy picked here. */
export function ImportView() {
  const [restored] = useState(loadImportDraft);
  const [mode, setMode] = useState<ImportMode>(restored?.mode ?? "csv");
  const [chosenPolicy, setChosenPolicy] = useState(restored?.policyId);
  const policies = usePolicies();
  const transition = useSafeTransition({ duration: 0.15, ease: easeOut });
  // The first policy, unless the owner chose another one that still exists.
  const policyId = policies.data?.some((policy) => policy.id === chosenPolicy)
    ? chosenPolicy
    : policies.data?.[0]?.id;
  const current = MODES.find((option) => option.mode === mode)!;

  const choose = (next: ImportMode) => {
    setMode(next);
    saveImportDraft({ mode: next });
  };
  const choosePolicy = (id: number) => {
    setChosenPolicy(id);
    saveImportDraft({ policyId: id });
  };
  const policyProps = {
    policies: policies.data,
    policiesLoading: policies.isPending,
    policyId,
    onPolicyChange: choosePolicy,
  };

  return (
    <div className="space-y-6">
      <div className="space-y-4">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight">Import dues</h1>
          <p className="text-sm text-muted-foreground">{current.lead}</p>
        </div>
        <ModeTabs mode={mode} onChange={choose} />
      </div>

      <AnimatePresence mode="wait" initial={false}>
        <motion.div
          key={mode}
          {...swap}
          transition={transition}
          role="tabpanel"
          id={`import-panel-${mode}`}
          aria-labelledby={`import-tab-${mode}`}
        >
          {mode === "csv" ? <CsvImport {...policyProps} /> : <ManualEntry {...policyProps} />}
        </motion.div>
      </AnimatePresence>
    </div>
  );
}

/** Tabs: click, or arrow keys between them (only the selected one is in the tab order). */
function ModeTabs({ mode, onChange }: { mode: ImportMode; onChange: (mode: ImportMode) => void }) {
  const tabs = useRef<(HTMLButtonElement | null)[]>([]);
  const transition = useSafeTransition({ duration: 0.25, ease: easeOut });

  const onKeyDown = (event: React.KeyboardEvent, index: number) => {
    if (event.key !== "ArrowRight" && event.key !== "ArrowLeft") return;
    event.preventDefault();
    const next = (index + (event.key === "ArrowRight" ? 1 : MODES.length - 1)) % MODES.length;
    onChange(MODES[next].mode);
    tabs.current[next]?.focus();
  };

  return (
    <div role="tablist" aria-label="How to add dues" className="inline-flex rounded-lg border bg-muted/50 p-0.5">
      {MODES.map(({ mode: value, label, icon: Icon }, index) => {
        const selected = value === mode;
        return (
          <button
            key={value}
            ref={(tab) => {
              tabs.current[index] = tab;
            }}
            type="button"
            role="tab"
            id={`import-tab-${value}`}
            aria-selected={selected}
            aria-controls={selected ? `import-panel-${value}` : undefined}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(value)}
            onKeyDown={(event) => onKeyDown(event, index)}
            className={cn(
              "relative flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm font-medium outline-none transition-colors focus-visible:ring-3 focus-visible:ring-ring/50",
              selected ? "text-foreground" : "text-muted-foreground hover:text-foreground",
            )}
          >
            {selected && (
              <motion.span
                layoutId="import-mode-tab"
                transition={transition}
                className="absolute inset-0 rounded-md bg-background shadow-sm ring-1 ring-border"
                aria-hidden="true"
              />
            )}
            <Icon className="relative size-4" aria-hidden="true" />
            <span className="relative">{label}</span>
          </button>
        );
      })}
    </div>
  );
}
