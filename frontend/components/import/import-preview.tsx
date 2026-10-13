"use client";

import { AlertCircle, CheckCircle2, SkipForward } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useState } from "react";

import { AnimatedNumber } from "@/components/motion/animated-number";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  type ColumnMapping,
  FIELD_LABELS,
  type ImportField,
  type ImportPreview,
  type PreviewRow,
} from "@/lib/imports";
import { formatDate, formatPaise } from "@/lib/money";
import { cn } from "@/lib/utils";

/** Rendering thousands of rows at once makes the page sluggish; "Only problems" narrows the list. */
const MAX_VISIBLE_ROWS = 200;

const PICKABLE: { field: ImportField; required: boolean }[] = [
  { field: "name", required: true },
  { field: "email", required: false },
  { field: "phone", required: false },
  { field: "amount", required: true },
  { field: "dueDate", required: true },
];

export function ColumnPicker({
  preview,
  disabled,
  onChange,
}: {
  preview: ImportPreview;
  disabled: boolean;
  onChange: (mapping: ColumnMapping) => void;
}) {
  const missing = preview.missingColumns;
  return (
    <section aria-labelledby="columns-heading" className="space-y-3">
      <div className="space-y-1">
        <h2 id="columns-heading" className="font-medium">
          Columns
        </h2>
        <p className="text-sm text-muted-foreground">
          We matched these from your headers. Change any that are wrong. Email or phone: at least one.
        </p>
      </div>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        {PICKABLE.map(({ field, required }) => {
          const id = `column-${field}`;
          const isMissing = missing.includes(field) || (missing.includes("contact") && field !== "name" && !required);
          return (
            <div key={field} className="space-y-1.5">
              <label htmlFor={id} className="text-sm font-medium">
                {FIELD_LABELS[field]}
                {required && <span className="text-muted-foreground"> *</span>}
              </label>
              <select
                id={id}
                value={preview.mapping[field] ?? ""}
                disabled={disabled}
                aria-invalid={isMissing}
                onChange={(event) => onChange({ ...preview.mapping, [field]: event.target.value || null })}
                className="h-8 w-full min-w-0 rounded-lg border border-input bg-transparent px-2 text-base outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 aria-invalid:border-destructive md:text-sm dark:bg-input/30 [&>option]:bg-popover [&>option]:text-popover-foreground"
              >
                <option value="">Not in this file</option>
                {preview.headers.map((header) => (
                  <option key={header} value={header}>
                    {header}
                  </option>
                ))}
              </select>
            </div>
          );
        })}
      </div>
      <AnimatePresence initial={false}>
        {missing.length > 0 && (
          <motion.div key="missing" initial={{ opacity: 0, y: -4 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0 }}>
            <Alert variant="destructive">
              <AlertCircle aria-hidden="true" />
              <AlertDescription>
                Choose a column for: {missing.map((field) => FIELD_LABELS[field].toLowerCase()).join(", ")}.
              </AlertDescription>
            </Alert>
          </motion.div>
        )}
      </AnimatePresence>
    </section>
  );
}

export function PreviewSummary({ preview }: { preview: ImportPreview }) {
  const { summary } = preview;
  const stats = [
    { label: "rows", value: summary.rows, tone: "" },
    { label: "ready", value: summary.valid - summary.alreadyImported, tone: "text-success" },
    { label: "need fixing", value: summary.invalid, tone: summary.invalid > 0 ? "text-destructive" : "" },
    ...(summary.alreadyImported > 0
      ? [{ label: "imported before", value: summary.alreadyImported, tone: "" }]
      : []),
    { label: "new customers", value: summary.newCustomers, tone: "" },
    { label: "already in your list", value: summary.existingCustomers, tone: "" },
  ];
  return (
    <dl className="flex flex-wrap gap-x-6 gap-y-2 text-sm" aria-label="Summary">
      {stats.map(({ label, value, tone }) => (
        <div key={label} className="flex items-baseline gap-1.5">
          <dt className="sr-only">{label}</dt>
          <dd className={cn("font-mono text-lg font-semibold tabular-nums", tone)}>
            <AnimatedNumber value={value} />
          </dd>
          <span className="text-muted-foreground" aria-hidden="true">
            {label}
          </span>
        </div>
      ))}
    </dl>
  );
}

export function PreviewTable({ preview }: { preview: ImportPreview }) {
  const [onlyProblems, setOnlyProblems] = useState(false);
  const hasEmail = preview.mapping.email !== null;
  const hasPhone = preview.mapping.phone !== null;
  const rows = onlyProblems ? preview.rows.filter((row) => Object.keys(row.errors).length > 0) : preview.rows;
  const visible = rows.slice(0, MAX_VISIBLE_ROWS);

  return (
    <section aria-labelledby="rows-heading" className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="rows-heading" className="font-medium">
          Rows
        </h2>
        <div className="flex rounded-lg border p-0.5" role="group" aria-label="Which rows to show">
          {[
            { value: false, label: "All rows" },
            { value: true, label: `Only problems (${preview.summary.invalid})` },
          ].map((option) => (
            <Button
              key={option.label}
              type="button"
              size="sm"
              variant={onlyProblems === option.value ? "secondary" : "ghost"}
              aria-pressed={onlyProblems === option.value}
              onClick={() => setOnlyProblems(option.value)}
            >
              {option.label}
            </Button>
          ))}
        </div>
      </div>

      <div className="overflow-hidden rounded-xl border">
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead className="w-14">Row</TableHead>
              <TableHead>Name</TableHead>
              {hasEmail && <TableHead>Email</TableHead>}
              {hasPhone && <TableHead>Phone</TableHead>}
              <TableHead className="text-right">Amount</TableHead>
              <TableHead>Due date</TableHead>
              <TableHead>Status</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {visible.map((row) => (
              <TableRow key={row.row} className="align-top">
                <TableCell className="font-mono text-xs text-muted-foreground">{row.row}</TableCell>
                <Cell row={row} field="name" />
                {hasEmail && <Cell row={row} field="email" />}
                {hasPhone && <Cell row={row} field="phone" />}
                <Cell
                  row={row}
                  field="amount"
                  className="text-right font-mono"
                  display={row.amountPaise !== null ? formatPaise(row.amountPaise) : undefined}
                />
                <Cell
                  row={row}
                  field="dueDate"
                  display={row.dueDate !== null ? formatDate(row.dueDate) : undefined}
                />
                <TableCell>
                  <RowStatus row={row} />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      {rows.length > MAX_VISIBLE_ROWS && (
        <p className="text-sm text-muted-foreground">
          Showing the first {MAX_VISIBLE_ROWS} of {rows.length} rows.
        </p>
      )}
      {rows.length === 0 && <p className="text-sm text-muted-foreground">No problems: every row is ready.</p>}
    </section>
  );
}

function Cell({
  row,
  field,
  display,
  className,
}: {
  row: PreviewRow;
  field: ImportField;
  display?: string;
  className?: string;
}) {
  const error = row.errors[field];
  const raw = row.values[field] ?? "";
  return (
    <TableCell className={cn("whitespace-normal", error && "bg-destructive/10", className)}>
      <span className={cn(!raw && "text-muted-foreground")}>{display ?? (raw || "—")}</span>
      {error && <p className="mt-0.5 font-sans text-xs text-destructive">{error}</p>}
    </TableCell>
  );
}

function RowStatus({ row }: { row: PreviewRow }) {
  if (Object.keys(row.errors).length > 0) {
    return (
      <Badge variant="destructive">
        <AlertCircle aria-hidden="true" />
        Needs fixing
      </Badge>
    );
  }
  if (row.alreadyImported) {
    return (
      <div className="space-y-0.5">
        <Badge variant="outline">
          <SkipForward aria-hidden="true" />
          Skipped
        </Badge>
        {row.note && <p className="text-xs text-muted-foreground">{row.note}</p>}
      </div>
    );
  }
  return (
    <div className="space-y-0.5">
      <Badge variant="secondary" className="text-success">
        <CheckCircle2 aria-hidden="true" />
        Ready
      </Badge>
      {row.existingCustomer && <p className="text-xs text-muted-foreground">Already a customer</p>}
      {row.note && <p className="text-xs text-muted-foreground">{row.note}</p>}
    </div>
  );
}
