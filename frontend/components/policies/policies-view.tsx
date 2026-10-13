"use client";

import { Mail, UserRound } from "lucide-react";

import { PageSkeleton } from "@/components/app/app-shell";
import { ErrorState } from "@/components/states";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { describeDay, type Policy, type PolicyStep, summarizePolicy, usePolicies } from "@/lib/policies";

export function PoliciesView() {
  const policies = usePolicies();

  if (policies.isPending) return <PageSkeleton />;
  if (policies.isError) {
    return <ErrorState title="Couldn't load your policies" error={policies.error} onRetry={() => policies.refetch()} />;
  }

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-2xl font-semibold tracking-tight">Reminder policies</h1>
        <p className="text-sm text-muted-foreground">
          The plan every due follows. Reminders stop the moment the customer pays or opts out, and every email ends
          with an unsubscribe link. Amounts, dates and pay links are filled in for you.
        </p>
      </div>
      <div className="grid gap-4 lg:grid-cols-3">
        {policies.data.map((policy) => (
          <PolicyCard key={policy.id} policy={policy} />
        ))}
      </div>
    </div>
  );
}

function PolicyCard({ policy }: { policy: Policy }) {
  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between gap-2">
          <CardTitle>{policy.name}</CardTitle>
          {policy.starter && <Badge variant="secondary">Starter</Badge>}
        </div>
        <CardDescription>{summarizePolicy(policy)}</CardDescription>
      </CardHeader>
      <CardContent>
        <ol className="relative space-y-4 border-l pl-5">
          {policy.steps.map((step) => (
            <StepItem key={step.key} step={step} />
          ))}
        </ol>
      </CardContent>
    </Card>
  );
}

function StepItem({ step }: { step: PolicyStep }) {
  const email = step.action === "SEND_EMAIL";
  const Icon = email ? Mail : UserRound;
  return (
    <li className="relative">
      <span className="absolute top-0.5 -left-[1.95rem] flex size-5 items-center justify-center rounded-full border bg-background">
        <Icon className="size-3 text-muted-foreground" aria-hidden="true" />
      </span>
      <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
        {describeDay(step.offsetDays)}
      </p>
      {email ? (
        <details className="group">
          <summary className="cursor-pointer text-sm font-medium marker:text-muted-foreground">
            {step.subject}
          </summary>
          <p className="mt-2 rounded-lg bg-muted/50 p-3 text-xs whitespace-pre-wrap text-muted-foreground">
            {step.body}
          </p>
        </details>
      ) : (
        <p className="text-sm">You get an email to follow up yourself</p>
      )}
    </li>
  );
}
