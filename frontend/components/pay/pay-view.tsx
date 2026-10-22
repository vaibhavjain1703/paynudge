"use client";

import { CircleCheck, Link2Off, Loader2, Lock } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useRouter } from "next/navigation";
import { useEffect, useRef } from "react";

import { FormAlert } from "@/components/forms";
import { ErrorState } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { ApiError } from "@/lib/api";
import { formatDate, formatPaise } from "@/lib/money";
import { easeOut, useSafeTransition } from "@/lib/motion";
import { type PayPage, type ReturnTrip, useConfirmPayment, usePayLink, usePayPage } from "@/lib/pay";

/**
 * Where the pay link in every reminder leads. Shows who is owed what and sends the customer to Razorpay to pay;
 * when Razorpay sends them back, confirms the payment (once) and says thank you.
 */
export function PayView({ token, trip }: { token: string; trip: ReturnTrip | null }) {
  const page = usePayPage(token);
  const confirm = useConfirmPayment(token);
  const router = useRouter();
  const confirmed = useRef(false);
  const transition = useSafeTransition({ duration: 0.25, ease: easeOut });

  // Back from Razorpay: confirm once, then drop the query so a refresh does not send it again.
  useEffect(() => {
    if (!trip || confirmed.current) return;
    confirmed.current = true;
    confirm.mutate(trip, { onSettled: () => router.replace(`/pay/${encodeURIComponent(token)}`) });
  }, [trip, confirm, router, token]);

  if (page.isPending || confirm.isPending) {
    return trip ? <Confirming /> : <PaySkeleton />;
  }
  if (page.isError) {
    if (page.error instanceof ApiError && page.error.status === 404) {
      return (
        <Notice icon={Link2Off} title="This link doesn't work">
          It may have been cut short when it was copied. Use the link in the latest reminder email.
        </Notice>
      );
    }
    return (
      <ErrorState
        className="w-full max-w-sm"
        title="Couldn't open this payment"
        error={page.error}
        onRetry={() => page.refetch()}
      />
    );
  }

  const due = page.data;
  return (
    <AnimatePresence mode="wait" initial={false}>
      <motion.div
        key={due.status}
        className="w-full max-w-sm"
        initial={{ opacity: 0, y: 8 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: -8 }}
        transition={transition}
      >
        {due.status === "PAID" ? (
          <Paid due={due} />
        ) : due.status === "CANCELLED" ? (
          <Notice icon={CircleCheck} title="There's nothing to pay here">
            {due.businessName} has closed this payment request.
          </Notice>
        ) : (
          <Open
            token={token}
            due={due}
            returnedUnpaid={Boolean(trip && trip.status !== "paid")}
            confirmError={confirm.error}
          />
        )}
      </motion.div>
    </AnimatePresence>
  );
}

function Open({
  token,
  due,
  returnedUnpaid,
  confirmError,
}: {
  token: string;
  due: PayPage;
  returnedUnpaid: boolean;
  confirmError: Error | null;
}) {
  const link = usePayLink(token);
  const pay = () =>
    link.mutate(undefined, {
      onSuccess: ({ url }) => window.location.assign(url),
    });
  const error = link.isError
    ? messageOf(link.error)
    : confirmError
      ? messageOf(confirmError)
      : returnedUnpaid
        ? "The payment didn't go through. You can try again."
        : undefined;

  return (
    <Card>
      <CardHeader className="space-y-1">
        <p className="text-sm text-muted-foreground">Payment to</p>
        <h1 className="text-xl font-semibold tracking-tight">{due.businessName}</h1>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="space-y-1">
          <p className="text-sm text-muted-foreground">Hi {due.customerFirstName},</p>
          <p className="text-4xl font-semibold tracking-tight">{formatPaise(due.amountPaise)}</p>
          <p className="text-sm text-muted-foreground">Due on {formatDate(due.dueDate)}</p>
        </div>
        <FormAlert message={error} />
        {!due.payable && (
          <p className="rounded-lg bg-muted p-3 text-sm text-muted-foreground">
            Online payment isn&apos;t set up yet. Please pay {due.businessName} directly.
          </p>
        )}
      </CardContent>
      <CardFooter className="flex-col items-stretch gap-3">
        <Button size="lg" onClick={pay} disabled={!due.payable || link.isPending || link.isSuccess}>
          {(link.isPending || link.isSuccess) && <Loader2 className="motion-safe:animate-spin" aria-hidden="true" />}
          Pay {formatPaise(due.amountPaise)}
        </Button>
        <p className="flex items-center justify-center gap-1.5 text-xs text-muted-foreground">
          <Lock className="size-3" aria-hidden="true" />
          Secure payment by Razorpay: UPI, cards or netbanking
        </p>
      </CardFooter>
    </Card>
  );
}

function Paid({ due }: { due: PayPage }) {
  const when = due.paidAt
    ? new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" }).format(new Date(due.paidAt))
    : null;
  return (
    <Notice icon={CircleCheck} title="Paid. Thank you!" tone="success" role="status">
      {formatPaise(due.amountPaise)} to {due.businessName}
      {when && ` on ${when}`}. You won&apos;t get any more reminders for it.
    </Notice>
  );
}

function Notice({
  icon: Icon,
  title,
  tone,
  role,
  children,
}: {
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  tone?: "success";
  role?: "status";
  children: React.ReactNode;
}) {
  return (
    <Card className="w-full max-w-sm">
      <CardContent className="flex flex-col items-center gap-4 py-8 text-center">
        <motion.div
          initial={{ scale: 0.5, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ type: "spring", bounce: 0.45, duration: 0.4 }}
          className={
            tone === "success"
              ? "rounded-full bg-success/15 p-3 text-success"
              : "rounded-full bg-accent p-3 text-accent-foreground"
          }
        >
          <Icon className="size-6" />
        </motion.div>
        <div className="space-y-1" role={role}>
          <p className="text-lg font-semibold">{title}</p>
          <p className="text-sm text-muted-foreground">{children}</p>
        </div>
      </CardContent>
    </Card>
  );
}

function Confirming() {
  return (
    <Card className="w-full max-w-sm" aria-busy="true">
      <CardContent className="flex flex-col items-center gap-3 py-10 text-center">
        <Loader2 className="size-6 text-primary motion-safe:animate-spin" aria-hidden="true" />
        <p className="font-medium">Confirming your payment…</p>
      </CardContent>
    </Card>
  );
}

function PaySkeleton() {
  return (
    <Card className="w-full max-w-sm" aria-busy="true" aria-label="Loading">
      <CardHeader className="space-y-2">
        <Skeleton className="h-4 w-20" />
        <Skeleton className="h-6 w-40" />
      </CardHeader>
      <CardContent className="space-y-2">
        <Skeleton className="h-4 w-24" />
        <Skeleton className="h-10 w-36" />
        <Skeleton className="h-4 w-32" />
      </CardContent>
      <CardFooter>
        <Skeleton className="h-10 w-full" />
      </CardFooter>
    </Card>
  );
}

function messageOf(error: unknown) {
  return error instanceof ApiError ? error.message : "Something went wrong. Please try again.";
}
