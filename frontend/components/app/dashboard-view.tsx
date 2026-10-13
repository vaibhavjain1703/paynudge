"use client";

import { Clock, FileSpreadsheet, Globe, Send } from "lucide-react";
import { motion } from "motion/react";
import Link from "next/link";

import { PageSkeleton } from "@/components/app/app-shell";
import { AnimatedNumber } from "@/components/motion/animated-number";
import { Stagger, StaggerItem } from "@/components/motion/stagger";
import { EmptyState, ErrorState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { type Business, useMe } from "@/lib/auth";

export function DashboardView() {
  const me = useMe();

  if (me.isPending) return <PageSkeleton />;
  if (me.isError) {
    return <ErrorState title="Couldn't load your dashboard" error={me.error} onRetry={() => me.refetch()} />;
  }

  const { business } = me.data;
  return (
    <Stagger className="space-y-8" gap={0.08}>
      <StaggerItem className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{business.name}</h1>
        <p className="text-sm text-muted-foreground">Signed in as {me.data.email}</p>
      </StaggerItem>

      <StaggerItem>
        <SendingRules business={business} />
      </StaggerItem>

      <StaggerItem>
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
      </StaggerItem>
    </Stagger>
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
          <AnimatedNumber value={business.dailySendCap} /> messages
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
      <Stagger className="grid gap-4 sm:grid-cols-3" gap={0.06}>
        {rules.map(({ icon: Icon, label, value }) => (
          <StaggerItem key={label}>
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
          </StaggerItem>
        ))}
      </Stagger>
    </section>
  );
}
