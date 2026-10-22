"use client";

import { ArrowLeft, BellOff, CircleCheck, SearchX } from "lucide-react";
import { motion } from "motion/react";
import Link from "next/link";
import { useState } from "react";

import { DueActionDialog } from "@/components/dues/due-action-dialog";
import { DueTimeline } from "@/components/dues/due-timeline";
import { StatusBadge } from "@/components/dues/status-badge";
import { EmptyState, ErrorState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiError } from "@/lib/api";
import { useMe } from "@/lib/auth";
import { formatPhone } from "@/lib/customers";
import { type DueAction, type DueDetail, isOpen, useDue } from "@/lib/dues";
import { formatDate, formatDateTime, formatPaise } from "@/lib/money";
import { easeOut, useSafeTransition } from "@/lib/motion";

/** One due: who owes what, its reminder timeline, and what the owner can still do about it. */
export function DueDetailView({ id }: { id: number }) {
  const due = useDue(id);
  const me = useMe();
  const timeZone = me.data?.business.timezone ?? "Asia/Kolkata";
  const [action, setAction] = useState<DueAction | null>(null);
  const transition = useSafeTransition({ duration: 0.25, ease: easeOut });

  if (due.isPending) return <DetailSkeleton />;
  if (due.isError) {
    if (due.error instanceof ApiError && due.error.status === 404) {
      return (
        <EmptyState
          icon={SearchX}
          title="This due isn't here"
          description="It may have been deleted with its customer, or the link is wrong."
          action={
            <Button asChild variant="outline">
              <Link href="/dues">All dues</Link>
            </Button>
          }
        />
      );
    }
    return <ErrorState title="Couldn't load this due" error={due.error} onRetry={() => due.refetch()} />;
  }

  const detail = due.data;
  // "Now" on the timeline is when this data was fetched, so the marker and the step states always agree.
  const now = new Date(due.dataUpdatedAt).toISOString();

  return (
    <div className="space-y-6">
      <Button asChild variant="ghost" size="sm" className="-ml-2 text-muted-foreground">
        <Link href="/dues">
          <ArrowLeft aria-hidden="true" />
          All dues
        </Link>
      </Button>

      <motion.div
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        transition={transition}
        className="flex flex-wrap items-start justify-between gap-4"
      >
        <div className="flex min-w-0 items-center gap-3">
          <div className="grid size-11 shrink-0 place-items-center rounded-full bg-accent text-sm font-semibold text-accent-foreground">
            {initials(detail.customer.name)}
          </div>
          <div className="min-w-0 space-y-0.5">
            <h1 className="truncate text-2xl font-semibold tracking-tight">{detail.customer.name}</h1>
            <p className="truncate text-sm text-muted-foreground">
              {detail.customer.email ?? (detail.customer.phone && formatPhone(detail.customer.phone))}
            </p>
          </div>
        </div>
        {isOpen(detail.status) && (
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => setAction("cancel")}>
              <BellOff aria-hidden="true" />
              Stop reminders
            </Button>
            <Button onClick={() => setAction("mark-paid")}>
              <CircleCheck aria-hidden="true" />
              Mark as paid
            </Button>
          </div>
        )}
      </motion.div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_18rem]">
        <Card>
          <CardHeader>
            <CardTitle>Reminders</CardTitle>
          </CardHeader>
          <CardContent>
            <DueTimeline due={detail} timeZone={timeZone} now={now} />
          </CardContent>
        </Card>
        <Facts due={detail} timeZone={timeZone} />
      </div>

      <DueActionDialog
        target={action && { due: detail, action }}
        onOpenChange={(open) => !open && setAction(null)}
      />
    </div>
  );
}

function Facts({ due, timeZone }: { due: DueDetail; timeZone: string }) {
  const payment = due.payments.at(-1);
  const facts: { label: string; value: React.ReactNode }[] = [
    { label: "Amount", value: <span className="font-mono">{formatPaise(due.amountPaise)}</span> },
    { label: "Due date", value: formatDate(due.dueDate) },
    {
      label: "Status",
      value: <StatusBadge status={due.status} unsubscribed={due.customer.optedOut} />,
    },
    {
      label: "Payment",
      value:
        due.status === "PAID" && due.paidAt
          ? `${payment ? `Paid${payment.method ? ` by ${payment.method.toUpperCase()}` : ""}` : "Marked paid"} ${formatDateTime(due.paidAt, timeZone)}`
          : "Not paid yet",
    },
    { label: "Policy", value: due.policyName ?? "Its own copy (the policy was deleted)" },
    { label: "Added", value: formatDateTime(due.createdAt, timeZone) },
  ];
  return (
    <Card size="sm" className="h-fit">
      <CardContent>
        <dl className="space-y-3 text-sm">
          {facts.map(({ label, value }) => (
            <div key={label} className="flex items-start justify-between gap-4">
              <dt className="text-muted-foreground">{label}</dt>
              <dd className="text-right">{value}</dd>
            </div>
          ))}
        </dl>
        {due.customer.optedOut && (
          <p className="mt-4 rounded-lg bg-warning/10 p-3 text-xs text-warning">
            {due.customer.name} unsubscribed, so no more reminders go out for any of their dues.
          </p>
        )}
      </CardContent>
    </Card>
  );
}

function initials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]!.toUpperCase())
    .join("");
}

function DetailSkeleton() {
  return (
    <div className="space-y-6" aria-busy="true" aria-label="Loading the due">
      <Skeleton className="h-7 w-24" />
      <div className="flex items-center gap-3">
        <Skeleton className="size-11 rounded-full" />
        <div className="space-y-2">
          <Skeleton className="h-6 w-48" />
          <Skeleton className="h-4 w-36" />
        </div>
      </div>
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_18rem]">
        <div className="space-y-4 rounded-xl border p-6">
          {Array.from({ length: 5 }, (_, index) => (
            <div key={index} className="flex items-center gap-3">
              <Skeleton className="size-6 rounded-full" />
              <div className="flex-1 space-y-1.5">
                <Skeleton className="h-4 w-1/2" />
                <Skeleton className="h-3 w-1/3" />
              </div>
            </div>
          ))}
        </div>
        <Skeleton className="h-56" />
      </div>
    </div>
  );
}
