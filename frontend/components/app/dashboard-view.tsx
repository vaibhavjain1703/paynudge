"use client";

import { ArrowRight, Clock, FileSpreadsheet, Globe, Mail, Send, UserRound } from "lucide-react";
import { motion } from "motion/react";
import Link from "next/link";

import { PageSkeleton } from "@/components/app/app-shell";
import { CollectionsChart } from "@/components/app/collections-chart";
import { AnimatedNumber } from "@/components/motion/animated-number";
import { EmptyState, ErrorState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { type Business, useMe } from "@/lib/auth";
import { type Dashboard, useDashboard } from "@/lib/dashboard";
import { formatDateTime, formatPaise } from "@/lib/money";
import { easeOut, useSafeTransition } from "@/lib/motion";
import { cn } from "@/lib/utils";

export function DashboardView() {
  const me = useMe();
  const dashboard = useDashboard();

  if (me.isPending) return <PageSkeleton />;
  if (me.isError) {
    return <ErrorState title="Couldn't load your dashboard" error={me.error} onRetry={() => me.refetch()} />;
  }

  const { business } = me.data;
  return (
    <div className="space-y-8">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{business.name}</h1>
        <p className="text-sm text-muted-foreground">
          {business.demo ? "A sample business with made-up customers. Look around." : `Signed in as ${me.data.email}`}
        </p>
      </div>

      {dashboard.isPending ? (
        <DashboardSkeleton />
      ) : dashboard.isError ? (
        <ErrorState
          title="Couldn't load your numbers"
          error={dashboard.error}
          onRetry={() => dashboard.refetch()}
        />
      ) : dashboard.data.dues === 0 ? (
        <EmptyState
          icon={FileSpreadsheet}
          title="No dues yet"
          description="Import a CSV of who owes what and when, or add dues one at a time. PayNudge schedules every reminder and stops the moment someone pays."
          action={
            <Button asChild>
              <Link href="/import">Import dues</Link>
            </Button>
          }
        />
      ) : (
        <Overview dashboard={dashboard.data} timeZone={business.timezone} />
      )}

      <SendingRules business={business} />
    </div>
  );
}

function Overview({ dashboard, timeZone }: { dashboard: Dashboard; timeZone: string }) {
  const transition = useSafeTransition({ duration: 0.25, ease: easeOut });
  const tiles = [
    {
      label: "Collected this month",
      total: dashboard.collectedThisMonth,
      href: "/dues?filter=paid",
      tone: "text-success",
    },
    { label: "Outstanding", total: dashboard.outstanding, href: "/dues?filter=open", tone: "" },
    { label: "Overdue", total: dashboard.overdue, href: "/dues?filter=overdue", tone: "text-warning" },
  ];

  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {tiles.map((tile, index) => (
          <motion.div
            key={tile.label}
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ ...transition, delay: index * 0.05 }}
          >
            <StatTile label={tile.label} href={tile.href}>
              <AnimatedNumber
                value={tile.total.paise / 100}
                format={(rupees) => countingRupees(rupees, tile.total.paise)}
                className={cn("text-2xl font-semibold", tile.total.paise > 0 && tile.tone)}
              />
              <span className="text-xs text-muted-foreground">
                {tile.total.dues} {tile.total.dues === 1 ? "due" : "dues"}
              </span>
            </StatTile>
          </motion.div>
        ))}
        <motion.div
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ ...transition, delay: 0.15 }}
        >
          <StatTile label="Needs you" href="/dues?filter=needs-you">
            <AnimatedNumber
              value={dashboard.needsYou}
              className={cn("text-2xl font-semibold", dashboard.needsYou > 0 && "text-warning")}
            />
            <span className="text-xs text-muted-foreground">
              {dashboard.needsYou === 0 ? "Nothing to chase yourself" : "Reminders ran out or failed"}
            </span>
          </StatTile>
        </motion.div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <Card>
          <CardContent>
            <CollectionsChart weeks={dashboard.collections} />
          </CardContent>
        </Card>
        <Upcoming upcoming={dashboard.upcoming} timeZone={timeZone} />
      </div>
    </div>
  );
}

function StatTile({ label, href, children }: { label: string; href: string; children: React.ReactNode }) {
  return (
    <Link
      href={href}
      className="group block h-full rounded-xl focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
    >
      <Card size="sm" className="h-full transition-shadow duration-200 group-hover:shadow-md">
        <CardHeader className="gap-1">
          <CardDescription className="flex items-center justify-between">
            {label}
            <ArrowRight
              className="size-3.5 opacity-0 transition-opacity duration-150 group-hover:opacity-100"
              aria-hidden="true"
            />
          </CardDescription>
          <div className="flex flex-col gap-0.5">{children}</div>
        </CardHeader>
      </Card>
    </Link>
  );
}

function Upcoming({ upcoming, timeZone }: { upcoming: Dashboard["upcoming"]; timeZone: string }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Coming up</CardTitle>
        <CardDescription>The next reminders, and when you hear about unpaid dues</CardDescription>
      </CardHeader>
      <CardContent>
        {upcoming.length === 0 ? (
          <p className="text-sm text-muted-foreground">Nothing scheduled. Every due is paid, cancelled or done.</p>
        ) : (
          <ul className="divide-y">
            {upcoming.map((item) => (
              <li key={`${item.dueId}-${item.stepKey}`}>
                <Link
                  href={`/dues/${item.dueId}`}
                  className="-mx-2 flex items-center gap-3 rounded-md px-2 py-2.5 transition-colors duration-150 hover:bg-muted/60"
                >
                  <span className="grid size-7 shrink-0 place-items-center rounded-full bg-accent text-accent-foreground">
                    {item.action === "SEND_EMAIL" ? (
                      <Mail className="size-3.5" aria-label="Email" />
                    ) : (
                      <UserRound className="size-3.5" aria-label="Tell you" />
                    )}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-medium">{item.customerName}</span>
                    <span className="block text-xs text-muted-foreground">
                      {formatDateTime(item.runAt, timeZone)}
                    </span>
                  </span>
                  <span className="font-mono text-sm">{formatPaise(item.amountPaise)}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

function DashboardSkeleton() {
  return (
    <div className="space-y-6" aria-busy="true" aria-label="Loading your numbers">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: 4 }, (_, index) => (
          <Skeleton key={index} className="h-24" />
        ))}
      </div>
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <Skeleton className="h-64" />
        <Skeleton className="h-64" />
      </div>
    </div>
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
    { icon: Send, label: "Daily send cap", value: `${business.dailySendCap} messages` },
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
          <Card key={label} size="sm">
            <CardHeader>
              <CardDescription className="flex items-center gap-2">
                <Icon className="size-4" aria-hidden="true" />
                {label}
              </CardDescription>
              <CardTitle className="font-mono text-base">{value}</CardTitle>
            </CardHeader>
          </Card>
        ))}
      </div>
    </section>
  );
}

/** While counting up, whole rupees (paise flickering past read as noise); once there, the exact amount. */
function countingRupees(rupees: number, finalPaise: number) {
  return Math.round(rupees * 100) === finalPaise ? formatPaise(finalPaise) : formatPaise(Math.round(rupees) * 100);
}
