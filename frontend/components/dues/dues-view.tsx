"use client";

import { BellOff, BellRing, CircleCheck, FileUp, Mail, Search, UserRound, X } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import Link from "next/link";
import { useState } from "react";

import { BulkActionDialog } from "@/components/dues/bulk-action-dialog";
import { type DueRef, DueActionDialog } from "@/components/dues/due-action-dialog";
import { StatusBadge } from "@/components/dues/status-badge";
import { EmptyState, ErrorState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useMe } from "@/lib/auth";
import { formatPhone } from "@/lib/customers";
import {
  type DueAction,
  type DueCounts,
  type DueFilter,
  DUES_PAGE_SIZE,
  type DueSummary,
  isOpen,
  useDues,
} from "@/lib/dues";
import { formatDate, formatDateTime, formatPaise } from "@/lib/money";
import { easeOut, useSafeTransition } from "@/lib/motion";
import { useDebounced } from "@/lib/use-debounced";
import { cn } from "@/lib/utils";

const CHIPS: { filter: DueFilter; label: string; count: keyof DueCounts }[] = [
  { filter: "all", label: "All", count: "all" },
  { filter: "open", label: "Open", count: "open" },
  { filter: "needs-you", label: "Needs you", count: "needsYou" },
  { filter: "overdue", label: "Overdue", count: "overdue" },
  { filter: "paid", label: "Paid", count: "paid" },
  { filter: "cancelled", label: "Cancelled", count: "cancelled" },
];

export function DuesView({ initialFilter = "all" }: { initialFilter?: DueFilter }) {
  const [filter, setFilter] = useState<DueFilter>(initialFilter);
  const [search, setSearch] = useState("");
  const query = useDebounced(search.trim(), 300);
  // The page and the selection belong to one view of the list: a new filter or search starts afresh.
  const view = `${filter}|${query}`;
  const [paging, setPaging] = useState({ view, page: 0 });
  const page = paging.view === view ? paging.page : 0;
  const [selection, setSelection] = useState<{ view: string; ids: Set<number> }>({ view, ids: new Set() });
  const selected = selection.view === view ? selection.ids : new Set<number>();
  const select = (ids: Set<number>) => setSelection({ view, ids });

  const dues = useDues(page, filter, query);
  const me = useMe();
  const timeZone = me.data?.business.timezone ?? "Asia/Kolkata";
  const data = dues.data;
  const noDuesAtAll = data && data.counts.all === 0 && !query;

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="space-y-1">
          <h1 className="text-2xl font-semibold tracking-tight">Dues</h1>
          <p className="text-sm text-muted-foreground">
            Who owes what, and the next reminder each one gets. Times are in {timeZone.replaceAll("_", " ")}.
          </p>
        </div>
        <Button asChild>
          <Link href="/import">
            <FileUp aria-hidden="true" />
            Import dues
          </Link>
        </Button>
      </div>

      {!noDuesAtAll && (
        <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
          <div className="-mx-1 flex gap-1.5 overflow-x-auto px-1 pb-1" role="group" aria-label="Show">
            {CHIPS.map((chip) => (
              <button
                key={chip.filter}
                type="button"
                aria-pressed={filter === chip.filter}
                onClick={() => setFilter(chip.filter)}
                className={cn(
                  "inline-flex shrink-0 items-center gap-1.5 rounded-full border px-3 py-1 text-sm transition-colors duration-150 focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none",
                  filter === chip.filter
                    ? "border-primary bg-primary/10 text-foreground"
                    : "text-muted-foreground hover:border-primary/50 hover:text-foreground",
                )}
              >
                {chip.label}
                {data && (
                  <span className="font-mono text-xs tabular-nums opacity-70">{data.counts[chip.count]}</span>
                )}
              </button>
            ))}
          </div>
          <div className="relative w-full lg:max-w-xs">
            <Search
              className="pointer-events-none absolute top-1/2 left-2.5 size-4 -translate-y-1/2 text-muted-foreground"
              aria-hidden="true"
            />
            <Input
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Search name, email or phone"
              aria-label="Search dues"
              className="pl-8"
            />
          </div>
        </div>
      )}

      {dues.isPending ? (
        <TableSkeleton />
      ) : dues.isError ? (
        <ErrorState title="Couldn't load your dues" error={dues.error} onRetry={() => dues.refetch()} />
      ) : noDuesAtAll ? (
        <EmptyState
          icon={BellRing}
          title="No dues yet"
          description="Import a spreadsheet of who owes what and when. Each due gets its reminders scheduled at once."
          action={
            <Button asChild>
              <Link href="/import">Import dues</Link>
            </Button>
          }
        />
      ) : data!.totalItems === 0 ? (
        <EmptyState
          icon={Search}
          title="No dues here"
          description={query ? `No ${chipLabel(filter)} due matches "${query}".` : `No ${chipLabel(filter)} dues.`}
        />
      ) : (
        <>
          <DueTable
            dues={data!.items}
            timeZone={timeZone}
            loading={dues.isPlaceholderData}
            selected={selected}
            onSelect={select}
          />
          {data!.totalPages > 1 && (
            <div className="flex items-center justify-between gap-4 text-sm text-muted-foreground">
              <p className="tabular-nums">
                {data!.page * DUES_PAGE_SIZE + 1}–{data!.page * DUES_PAGE_SIZE + data!.items.length} of{" "}
                {data!.totalItems}
              </p>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => setPaging({ view, page: page - 1 })}
                >
                  Previous
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page + 1 >= data!.totalPages}
                  onClick={() => setPaging({ view, page: page + 1 })}
                >
                  Next
                </Button>
              </div>
            </div>
          )}
        </>
      )}

      <BulkBar selected={selected} onClear={() => select(new Set())} />
    </div>
  );
}

function chipLabel(filter: DueFilter) {
  return filter === "all" ? "" : CHIPS.find((chip) => chip.filter === filter)!.label.toLowerCase();
}

function DueTable({
  dues,
  timeZone,
  loading,
  selected,
  onSelect,
}: {
  dues: DueSummary[];
  timeZone: string;
  loading: boolean;
  selected: Set<number>;
  onSelect: (ids: Set<number>) => void;
}) {
  const [acting, setActing] = useState<{ due: DueRef; action: DueAction } | null>(null);
  const selectable = dues.filter((due) => isOpen(due.status));
  const allSelected = selectable.length > 0 && selectable.every((due) => selected.has(due.id));
  const toggle = (id: number) => {
    const next = new Set(selected);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    onSelect(next);
  };

  return (
    <div
      className="overflow-hidden rounded-xl border transition-opacity duration-200 data-[loading=true]:opacity-60"
      data-loading={loading}
      aria-busy={loading}
    >
      <DueActionDialog target={acting} onOpenChange={(open) => !open && setActing(null)} />
      <Table>
        <TableHeader>
          <TableRow className="hover:bg-transparent">
            <TableHead className="w-8 pr-0">
              <input
                type="checkbox"
                aria-label="Select every open due on this page"
                checked={allSelected}
                disabled={selectable.length === 0}
                onChange={() => onSelect(allSelected ? new Set() : new Set(selectable.map((due) => due.id)))}
                className="size-4 accent-primary"
              />
            </TableHead>
            <TableHead>Customer</TableHead>
            <TableHead className="text-right">Amount</TableHead>
            <TableHead className="hidden sm:table-cell">Due date</TableHead>
            <TableHead>Status</TableHead>
            <TableHead className="hidden md:table-cell">Next reminder</TableHead>
            <TableHead className="w-20 text-right">
              <span className="sr-only">Actions</span>
            </TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {dues.map((due) => (
            <TableRow key={due.id} data-state={selected.has(due.id) ? "selected" : undefined}>
              <TableCell className="pr-0">
                <input
                  type="checkbox"
                  aria-label={`Select ${due.customer.name}'s due`}
                  checked={selected.has(due.id)}
                  disabled={!isOpen(due.status)}
                  onChange={() => toggle(due.id)}
                  className="size-4 accent-primary disabled:opacity-30"
                />
              </TableCell>
              <TableCell className="max-w-0 sm:max-w-none">
                <Link
                  href={`/dues/${due.id}`}
                  className="block truncate font-medium underline-offset-4 hover:text-primary hover:underline"
                >
                  {due.customer.name}
                </Link>
                <div className="truncate text-xs text-muted-foreground">
                  {due.customer.email ?? (due.customer.phone && formatPhone(due.customer.phone))}
                </div>
              </TableCell>
              <TableCell className="text-right">
                <div className="font-mono">{formatPaise(due.amountPaise)}</div>
                {/* On a phone the date sits under the amount, leaving room for the actions. */}
                <div className="text-xs whitespace-nowrap text-muted-foreground sm:hidden">{formatDate(due.dueDate)}</div>
              </TableCell>
              <TableCell className="hidden whitespace-nowrap sm:table-cell">{formatDate(due.dueDate)}</TableCell>
              <TableCell>
                <StatusBadge status={due.status} unsubscribed={due.customer.optedOut} />
              </TableCell>
              <TableCell className="hidden text-sm text-muted-foreground md:table-cell">
                {due.nextStep ? (
                  <span className="inline-flex items-center gap-1.5">
                    {due.nextStep.action === "SEND_EMAIL" ? (
                      <Mail className="size-3.5" aria-label="Email" />
                    ) : (
                      <UserRound className="size-3.5" aria-label="Tell you" />
                    )}
                    {formatDateTime(due.nextStep.runAt, timeZone)}
                  </span>
                ) : (
                  "—"
                )}
              </TableCell>
              <TableCell className="text-right whitespace-nowrap">
                {isOpen(due.status) && (
                  <>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label={`Mark ${due.customer.name}'s due as paid`}
                      onClick={() => setActing({ due, action: "mark-paid" })}
                      className="text-muted-foreground hover:text-success"
                    >
                      <CircleCheck aria-hidden="true" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label={`Stop reminders for ${due.customer.name}`}
                      onClick={() => setActing({ due, action: "cancel" })}
                      className="text-muted-foreground hover:text-destructive"
                    >
                      <BellOff aria-hidden="true" />
                    </Button>
                  </>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

/** Appears while dues are selected: act on all of them at once. */
function BulkBar({ selected, onClear }: { selected: Set<number>; onClear: () => void }) {
  const [action, setAction] = useState<DueAction | null>(null);
  const transition = useSafeTransition({ duration: 0.2, ease: easeOut });
  const count = selected.size;

  return (
    <>
      <AnimatePresence>
        {count > 0 && (
          <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: 16 }}
            transition={transition}
            className="sticky bottom-4 z-20 mx-auto flex w-fit max-w-full flex-wrap items-center gap-2 rounded-full border bg-popover/95 py-1.5 pr-1.5 pl-4 shadow-lg backdrop-blur"
            role="region"
            aria-label="Selected dues"
          >
            <span className="text-sm font-medium tabular-nums">
              {count} selected
            </span>
            <Button size="sm" variant="ghost" onClick={() => setAction("mark-paid")}>
              <CircleCheck aria-hidden="true" />
              Mark paid
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setAction("cancel")}>
              <BellOff aria-hidden="true" />
              Stop reminders
            </Button>
            <Button size="icon-sm" variant="ghost" aria-label="Clear the selection" onClick={onClear}>
              <X aria-hidden="true" />
            </Button>
          </motion.div>
        )}
      </AnimatePresence>
      <BulkActionDialog
        ids={[...selected]}
        action={action}
        onOpenChange={(open) => !open && setAction(null)}
        onDone={onClear}
      />
    </>
  );
}

function TableSkeleton() {
  return (
    <div className="space-y-px overflow-hidden rounded-xl border" aria-busy="true" aria-label="Loading dues">
      {Array.from({ length: 6 }, (_, index) => (
        <div key={index} className="flex items-center gap-4 px-3 py-3">
          <Skeleton className="size-4" />
          <Skeleton className="h-4 w-40" />
          <Skeleton className="ml-auto h-4 w-16" />
          <Skeleton className="h-4 w-20" />
          <Skeleton className="h-4 w-16" />
        </div>
      ))}
    </div>
  );
}
