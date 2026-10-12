"use client";

import { Clock, FileSpreadsheet, Globe, Send } from "lucide-react";
import { motion } from "motion/react";

import { PageSkeleton } from "@/components/app/app-shell";
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
    <motion.div
      initial={{ opacity: 0, y: 6 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.2, ease: "easeOut" }}
      className="space-y-8"
    >
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">{business.name}</h1>
        <p className="text-sm text-muted-foreground">Signed in as {me.data.email}</p>
      </div>

      <SendingRules business={business} />

      <EmptyState
        icon={FileSpreadsheet}
        title="No dues yet"
        description="Import a CSV of who owes what and when. PayNudge schedules every reminder and stops the moment someone pays."
        action={<Button disabled>Import dues</Button>}
      />
    </motion.div>
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
      value: `${business.dailySendCap.toLocaleString("en-IN")} messages`,
    },
  ];

  return (
    <section aria-labelledby="sending-rules" className="space-y-3">
      <h2 id="sending-rules" className="text-sm font-medium text-muted-foreground">
        Sending rules
      </h2>
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
