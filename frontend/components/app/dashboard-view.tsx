"use client";

import { Clock, FileSpreadsheet, Globe, Send } from "lucide-react";
import { motion } from "motion/react";
import Link from "next/link";

import { PageSkeleton } from "@/components/app/app-shell";
import { EmptyState, ErrorState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { type Business, useMe } from "@/lib/auth";
import { useDues } from "@/lib/dues";
import { formatDateTime } from "@/lib/money";

export function DashboardView() {
  const me = useMe();

  if (me.isPending) return <PageSkeleton />;
  if (me.isError) {
    return <ErrorState title="Couldn't load your dashboard" error={me.error} onRetry={() => me.refetch()} />;
  }

  const { business } = me.data;
  return (
    <div className="space-y-8">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{business.name}</h1>
        <p className="text-sm text-muted-foreground">Signed in as {me.data.email}</p>
      </div>

      <div>
        <SendingRules business={business} />
      </div>

      <DuesSummary timeZone={business.timezone} />
    </div>
  );
}

/** The full dashboard (totals, chart, upcoming reminders) comes on Day 6; for now, a way into the dues. */
function DuesSummary({ timeZone }: { timeZone: string }) {
  const dues = useDues(0);

  if (dues.isPending) return <Skeleton className="h-28" />;
  if (dues.isError) {
    return <ErrorState title="Couldn't load your dues" error={dues.error} onRetry={() => dues.refetch()} />;
  }
  if (dues.data.totalItems === 0) {
    return (
      <EmptyState
        icon={FileSpreadsheet}
        title="No dues yet"
        description="Import a CSV of who owes what and when. PayNudge schedules every reminder and stops the moment someone pays."
        action={
          <Button asChild>
            <Link href="/import">Import dues</Link>
          </Button>
        }
      />
    );
  }
  const next = dues.data.items
    .flatMap((due) => (due.nextStep ? [due.nextStep.runAt] : []))
    .sort()
    .at(0);
  return (
    <Card>
      <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-4">
        <div className="space-y-1">
          <CardTitle>
            {dues.data.totalItems} {dues.data.totalItems === 1 ? "due" : "dues"}
          </CardTitle>
          <CardDescription>
            {next ? `Next reminder ${formatDateTime(next, timeZone)}` : "No reminders waiting"}
          </CardDescription>
        </div>
        <Button asChild variant="outline">
          <Link href="/dues">View dues</Link>
        </Button>
      </CardHeader>
    </Card>
  );
}

function SendingRules({ business }: { business: Business }) {
  const rules = [
    { icon: Globe, label: "Timezone", value: business.timezone },
    {
      icon: Clock,
      label: "Quiet hours",
      value: `${business.quietStart.slice(0, 5)} to ${business.quietEnd.slice(0, 5)}`,
    },
    {
      icon: Send,
      label: "Daily send cap",
      value: (
        <>
          {business.dailySendCap} messages
        </>
      ),
    },
  ];

  return (
    <section aria-labelledby="sending-rules" className="space-y-3">
      <div className="flex items-center justify-between">
        <h2 id="sending-rules" className="text-sm font-medium text-muted-foreground">
          Sending rules
        </h2>
        <Button asChild variant="link" size="sm" className="px-0">
          <Link href="/settings">Edit</Link>
        </Button>
      </div>
      <div className="grid gap-4 sm:grid-cols-3">
        {rules.map(({ icon: Icon, label, value }) => (
          <div key={label}>
            <motion.div whileHover={{ y: -2 }} transition={{ duration: 0.15 }}>
              <Card size="sm" className="transition-shadow duration-200 hover:shadow-md">
                <CardHeader>
                  <CardDescription className="flex items-center gap-2">
                    <Icon className="size-4" aria-hidden="true" />
                    {label}
                  </CardDescription>
                  <CardTitle className="font-mono text-base">{value}</CardTitle>
                </CardHeader>
              </Card>
            </motion.div>
          </div>
        ))}
      </div>
    </section>
  );
}
