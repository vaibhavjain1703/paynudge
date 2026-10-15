"use client";

import { BellOff, Link2Off, Loader2, type LucideIcon } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";

import { FormAlert } from "@/components/forms";
import { ErrorState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiError } from "@/lib/api";
import { easeOut, useSafeTransition } from "@/lib/motion";
import { useUnsubscribe, useUnsubscribeStatus } from "@/lib/unsubscribe";

/**
 * Where the "Stop these reminders" link in every reminder leads. Opening the link changes nothing (mail scanners
 * open links too); the customer confirms with one button.
 */
export function UnsubscribeView({ token }: { token: string }) {
  const status = useUnsubscribeStatus(token);
  const unsubscribe = useUnsubscribe(token);
  const transition = useSafeTransition({ duration: 0.25, ease: easeOut });

  if (status.isPending) {
    return (
      <Card className="w-full max-w-sm" aria-busy="true" aria-label="Loading">
        <CardHeader className="space-y-2">
          <Skeleton className="h-5 w-3/4" />
          <Skeleton className="h-4 w-full" />
          <Skeleton className="h-4 w-2/3" />
        </CardHeader>
        <CardFooter>
          <Skeleton className="h-8 w-28" />
        </CardFooter>
      </Card>
    );
  }

  if (status.isError) {
    if (status.error instanceof ApiError && status.error.status === 404) {
      return (
        <Notice icon={Link2Off} title="This link doesn't work">
          It may have been cut short when it was copied. Use the link at the bottom of the latest reminder email.
        </Notice>
      );
    }
    return (
      <ErrorState
        className="w-full max-w-sm"
        title="Couldn't open this link"
        error={status.error}
        onRetry={() => status.refetch()}
      />
    );
  }

  const { businessName, optedOut } = status.data;
  return (
    <AnimatePresence mode="wait" initial={false}>
      {optedOut ? (
        <motion.div key="done" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={transition}>
          <Notice icon={BellOff} title="You're unsubscribed" role="status">
            {businessName} won&apos;t send you any more payment reminders through PayNudge.
          </Notice>
        </motion.div>
      ) : (
        <motion.div
          key="confirm"
          className="w-full max-w-sm"
          initial={{ opacity: 0, y: 8 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -8 }}
          transition={transition}
        >
          <Card>
            <CardHeader>
              <CardTitle className="text-lg">Stop payment reminders from {businessName}?</CardTitle>
              <CardDescription>
                You won&apos;t get any more reminder emails from {businessName} through PayNudge, for anything you
                owe them.
              </CardDescription>
            </CardHeader>
            {unsubscribe.isError && (
              <CardContent>
                <FormAlert
                  message={
                    unsubscribe.error instanceof ApiError
                      ? unsubscribe.error.message
                      : "Something went wrong. Please try again."
                  }
                />
              </CardContent>
            )}
            <CardFooter>
              <Button onClick={() => unsubscribe.mutate()} disabled={unsubscribe.isPending}>
                {unsubscribe.isPending && <Loader2 className="motion-safe:animate-spin" aria-hidden="true" />}
                Stop reminders
              </Button>
            </CardFooter>
          </Card>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

function Notice({
  icon: Icon,
  title,
  role,
  children,
}: {
  icon: LucideIcon;
  title: string;
  role?: "status";
  children: React.ReactNode;
}) {
  return (
    <Card className="w-full max-w-sm">
      <CardContent className="flex flex-col items-center gap-4 py-8 text-center">
        <motion.div
          initial={{ scale: 0.5, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ type: "spring", bounce: 0.4, duration: 0.4 }}
          className="rounded-full bg-accent p-3 text-accent-foreground"
        >
          <Icon className="size-6" aria-hidden="true" />
        </motion.div>
        <div className="space-y-1" role={role}>
          <p className="text-lg font-semibold">{title}</p>
          <p className="text-sm text-muted-foreground">{children}</p>
        </div>
      </CardContent>
    </Card>
  );
}
