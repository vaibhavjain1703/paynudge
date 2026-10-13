"use client";

import { BellRing, FileUp, Mail, UserRound } from "lucide-react";
import Link from "next/link";
import { useState } from "react";

import { EmptyState, ErrorState } from "@/components/states";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useMe } from "@/lib/auth";
import { formatPhone } from "@/lib/customers";
import { DUE_STATUS_LABELS, DUES_PAGE_SIZE, type DueStatus, type DueSummary, useDues } from "@/lib/dues";
import { formatDate, formatDateTime, formatPaise } from "@/lib/money";
import { cn } from "@/lib/utils";

export function DuesView() {
  const [page, setPage] = useState(0);
  const dues = useDues(page);
  const me = useMe();
  const timeZone = me.data?.business.timezone ?? "Asia/Kolkata";

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

      {dues.isPending ? (
        <TableSkeleton />
      ) : dues.isError ? (
        <ErrorState title="Couldn't load your dues" error={dues.error} onRetry={() => dues.refetch()} />
      ) : dues.data.totalItems === 0 ? (
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
      ) : (
        <>
          <DueTable dues={dues.data.items} timeZone={timeZone} loading={dues.isPlaceholderData} />
          {dues.data.totalPages > 1 && (
            <div className="flex items-center justify-between gap-4 text-sm text-muted-foreground">
              <p className="tabular-nums">
                {dues.data.page * DUES_PAGE_SIZE + 1}–{dues.data.page * DUES_PAGE_SIZE + dues.data.items.length} of{" "}
                {dues.data.totalItems}
              </p>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>
                  Previous
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page + 1 >= dues.data.totalPages}
                  onClick={() => setPage(page + 1)}
                >
                  Next
                </Button>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function DueTable({ dues, timeZone, loading }: { dues: DueSummary[]; timeZone: string; loading: boolean }) {
  return (
    <div
      className="overflow-hidden rounded-xl border transition-opacity duration-200 data-[loading=true]:opacity-60"
      data-loading={loading}
      aria-busy={loading}
    >
      <Table>
        <TableHeader>
          <TableRow className="hover:bg-transparent">
            <TableHead>Customer</TableHead>
            <TableHead className="text-right">Amount</TableHead>
            <TableHead>Due date</TableHead>
            <TableHead>Status</TableHead>
            <TableHead className="hidden md:table-cell">Next reminder</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {dues.map((due) => (
            <TableRow key={due.id}>
              <TableCell className="max-w-0 sm:max-w-none">
                <div className="truncate font-medium">{due.customer.name}</div>
                <div className="truncate text-xs text-muted-foreground">
                  {due.customer.email ?? (due.customer.phone && formatPhone(due.customer.phone))}
                </div>
              </TableCell>
              <TableCell className="text-right font-mono">{formatPaise(due.amountPaise)}</TableCell>
              <TableCell className="whitespace-nowrap">{formatDate(due.dueDate)}</TableCell>
              <TableCell>
                <StatusBadge status={due.status} />
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
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

const STATUS_STYLES: Record<DueStatus, string> = {
  SCHEDULED: "",
  ACTIVE: "text-info",
  ESCALATED: "text-warning",
  PAID: "text-success",
  CANCELLED: "text-muted-foreground",
};

function StatusBadge({ status }: { status: DueStatus }) {
  return (
    <Badge variant={status === "CANCELLED" ? "outline" : "secondary"} className={cn(STATUS_STYLES[status])}>
      {DUE_STATUS_LABELS[status]}
    </Badge>
  );
}

function TableSkeleton() {
  return (
    <div className="space-y-px overflow-hidden rounded-xl border" aria-busy="true" aria-label="Loading dues">
      {Array.from({ length: 6 }, (_, index) => (
        <div key={index} className="flex items-center gap-4 px-3 py-3">
          <Skeleton className="h-4 w-40" />
          <Skeleton className="ml-auto h-4 w-16" />
          <Skeleton className="h-4 w-20" />
          <Skeleton className="h-4 w-16" />
        </div>
      ))}
    </div>
  );
}
