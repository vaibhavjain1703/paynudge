"use client";

import { AlertTriangle, Check, Clock, IndianRupee, SkipForward, UserRound, X } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";

import type { DueDetail, DueStep, StepStatus } from "@/lib/dues";
import { formatDateTime, formatPaise } from "@/lib/money";
import { easeOut, useSafeTransition } from "@/lib/motion";
import { describeDay } from "@/lib/policies";
import { cn } from "@/lib/utils";

type Entry =
  | { kind: "step"; at: string; step: DueStep; sentAt?: string; subject?: string | null }
  | { kind: "payment"; at: string; amountPaise: number; method: string | null; manual: boolean }
  | { kind: "now"; at: string };

const TONES: Record<NonNullable<DueStep["tone"]>, string> = {
  friendly: "Friendly reminder",
  neutral: "Reminder",
  firm: "Firm reminder",
  final_notice: "Final notice",
};

/**
 * The due's story so far and what comes next: every step in time order (sent, scheduled, skipped or cancelled), the
 * payment where it landed, and a marker for now. The rail fills up to the last thing that happened.
 */
export function DueTimeline({ due, timeZone, now }: { due: DueDetail; timeZone: string; now: string }) {
  const entries = timelineOf(due, now);
  const nowIndex = entries.findIndex((entry) => entry.kind === "now");
  // The rail is filled up to "now", or for a closed due up to its last real event (not the steps it cancelled).
  const reached = nowIndex >= 0 ? nowIndex : entries.findLastIndex(isEvent);
  const rail = useSafeTransition({ duration: 0.3, ease: easeOut });
  const item = useSafeTransition({ duration: 0.25, ease: easeOut });

  return (
    <ol className="space-y-1" aria-label="Reminder timeline">
      {entries.map((entry, index) => {
        const next = entries[index + 1];
        return (
          <motion.li
            key={entry.kind === "step" ? entry.step.key : entry.kind}
            className="relative"
            initial={{ opacity: 0, x: -6 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{ ...item, delay: Math.min(index, 8) * 0.04 }}
          >
            {next && (
              // The rail from this entry's node to the next one's: through the gap and down to its centre.
              <span
                aria-hidden="true"
                className="absolute left-3 w-px bg-border"
                style={{ top: NODE_CENTRE[entry.kind], bottom: -(GAP + NODE_CENTRE[next.kind]) }}
              >
                {index < reached && (
                  <motion.span
                    className="absolute inset-0 origin-top bg-primary"
                    initial={{ scaleY: 0 }}
                    animate={{ scaleY: 1 }}
                    transition={{ ...rail, delay: Math.min(index, 8) * 0.06 }}
                  />
                )}
              </span>
            )}
            {entry.kind === "step" && <StepEntry entry={entry} timeZone={timeZone} />}
            {entry.kind === "payment" && <PaymentEntry entry={entry} timeZone={timeZone} />}
            {entry.kind === "now" && <NowMarker />}
          </motion.li>
        );
      })}
    </ol>
  );
}

/** Where each kind of entry's node sits (its centre, from the entry's top), and the gap between entries, in px. */
const NODE_CENTRE: Record<Entry["kind"], number> = { step: 20, payment: 24, now: 16 };

const GAP = 4;

/** Steps by time, the customer email's subject and send time beside its step, the payment and "now" slotted in. */
function timelineOf(due: DueDetail, now: string): Entry[] {
  const sent = new Map(due.messages.map((message) => [message.stepKey, message]));
  const entries: Entry[] = due.steps.map((step) => {
    const message = sent.get(step.key);
    return {
      kind: "step",
      at: message?.sentAt ?? step.runAt,
      step,
      sentAt: message?.sentAt,
      subject: message?.channel === "EMAIL" ? message.subject : undefined,
    };
  });
  if (due.status === "PAID" && due.paidAt) {
    const payment = due.payments.at(-1);
    entries.push({
      kind: "payment",
      at: due.paidAt,
      amountPaise: payment?.amountPaise ?? due.amountPaise,
      method: payment?.method ?? null,
      manual: !payment,
    });
  }
  entries.sort((a, b) => a.at.localeCompare(b.at));
  if (due.status !== "PAID" && due.status !== "CANCELLED") {
    // "Now" goes after everything that has happened and before the first step still to come.
    const next = entries.findIndex((entry) => entry.kind === "step" && !hasHappened(entry));
    if (next > 0) entries.splice(next, 0, { kind: "now", at: now });
  }
  return entries;
}

function hasHappened(entry: Entry) {
  if (entry.kind !== "step") return true;
  return entry.step.status !== "PENDING" && entry.step.status !== "RUNNING";
}

/** Something that actually happened: an email or notice sent, a failure, the payment. */
function isEvent(entry: Entry) {
  if (entry.kind === "payment") return true;
  return entry.kind === "step" && (entry.step.status === "DONE" || entry.step.status === "FAILED");
}

type Look = { label: string; node: string; badge: string; icon: React.ComponentType<{ className?: string }> };

function lookOf(step: DueStep): Look {
  const owner = step.action === "NOTIFY_OWNER";
  const looks: Record<StepStatus, Look> = {
    DONE: owner
      ? { label: "Told you", node: "bg-info text-info-foreground", badge: "bg-info/15 text-info", icon: UserRound }
      : { label: "Sent", node: "bg-primary text-primary-foreground", badge: "bg-success/15 text-success", icon: Check },
    PENDING: {
      label: "Scheduled",
      node: "border border-dashed border-muted-foreground/50 bg-card text-muted-foreground",
      badge: "bg-muted text-muted-foreground",
      icon: Clock,
    },
    RUNNING: { label: "Sending", node: "border border-info bg-info/15 text-info", badge: "bg-info/15 text-info", icon: Clock },
    SKIPPED: { label: "Skipped", node: "bg-muted text-muted-foreground", badge: "bg-muted text-muted-foreground", icon: SkipForward },
    CANCELLED: { label: "Cancelled", node: "bg-muted text-muted-foreground", badge: "bg-muted text-muted-foreground", icon: X },
    FAILED: {
      label: "Failed",
      node: "bg-destructive text-white",
      badge: "bg-destructive/15 text-destructive",
      icon: AlertTriangle,
    },
  };
  return looks[step.status];
}

function StepEntry({ entry, timeZone }: { entry: Extract<Entry, { kind: "step" }>; timeZone: string }) {
  const { step } = entry;
  const look = lookOf(step);
  const Icon = look.icon;
  const faded = step.status === "CANCELLED" || step.status === "SKIPPED";
  const title = step.action === "NOTIFY_OWNER" ? "Alert to you" : step.tone ? TONES[step.tone] : "Reminder";
  const day = step.offsetDays === null ? null : describeDay(step.offsetDays);
  const when =
    step.status === "DONE"
      ? `${step.action === "NOTIFY_OWNER" ? "Emailed you" : "Sent"} ${formatDateTime(entry.sentAt ?? step.runAt, timeZone)}`
      : step.status === "PENDING" || step.status === "RUNNING"
        ? `Scheduled for ${formatDateTime(step.runAt, timeZone)}`
        : step.status === "SKIPPED"
          ? `Was due ${formatDateTime(step.runAt, timeZone)}, before this due was added`
          : formatDateTime(step.runAt, timeZone);

  return (
    <div className="relative flex gap-3 py-2" data-step={step.key} data-status={step.status}>
      <span className={cn("relative z-10 grid size-6 shrink-0 place-items-center rounded-full", look.node)}>
        {step.status === "RUNNING" && (
          <motion.span
            aria-hidden="true"
            className="absolute inset-0 rounded-full border border-info"
            initial={{ scale: 1, opacity: 0.7 }}
            animate={{ scale: 1.8, opacity: 0 }}
            transition={{ duration: 0.9, repeat: Infinity, ease: "easeOut" }}
          />
        )}
        <AnimatePresence mode="popLayout" initial={false}>
          <motion.span
            key={step.status}
            initial={{ scale: 0.4, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            exit={{ scale: 0.4, opacity: 0 }}
            transition={{ type: "spring", bounce: 0.45, duration: 0.35 }}
            className="grid place-items-center"
          >
            <Icon className="size-3.5" aria-hidden="true" />
          </motion.span>
        </AnimatePresence>
      </span>
      <div className={cn("min-w-0 flex-1 space-y-0.5", faded && "opacity-60")}>
        <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
          <p className={cn("text-sm font-medium", faded && "line-through decoration-muted-foreground")}>
            {title}
            {day && <span className="font-normal text-muted-foreground"> · {day}</span>}
          </p>
          <span className={cn("rounded-full px-2 py-0.5 text-[11px] font-medium", look.badge)}>{look.label}</span>
        </div>
        <p className="text-xs text-muted-foreground">{when}</p>
        {entry.subject && <p className="truncate text-xs text-muted-foreground">“{entry.subject}”</p>}
        {step.note && (step.status === "PENDING" || step.status === "RUNNING") && (
          <p className="text-xs text-info">{step.note}</p>
        )}
        {step.error && step.status === "FAILED" && <p className="text-xs text-destructive">{step.error}</p>}
        {step.error && step.status === "CANCELLED" && <p className="text-xs text-muted-foreground">{step.error}</p>}
      </div>
    </div>
  );
}

function PaymentEntry({ entry, timeZone }: { entry: Extract<Entry, { kind: "payment" }>; timeZone: string }) {
  return (
    // Padding, not margin, around the row: a margin would collapse into the list item and shift the node off the rail.
    <div className="py-1">
      <div className="relative -mx-2 flex items-center gap-3 rounded-lg border border-success/30 bg-success/10 px-2 py-2">
        <motion.span
          initial={{ scale: 0.4 }}
          animate={{ scale: 1 }}
          transition={{ type: "spring", bounce: 0.5, duration: 0.4 }}
          className="relative z-10 grid size-6 shrink-0 place-items-center rounded-full bg-success text-success-foreground"
        >
          <IndianRupee className="size-3.5" aria-hidden="true" />
        </motion.span>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-medium">{entry.manual ? "Marked paid" : "Payment received"}</p>
          <p className="text-xs text-muted-foreground">
            {formatPaise(entry.amountPaise)}
            {entry.method && ` via ${entry.method.toUpperCase()}`} · {formatDateTime(entry.at, timeZone)}
          </p>
        </div>
      </div>
    </div>
  );
}

function NowMarker() {
  return (
    <div className="relative flex items-center gap-3 py-1" aria-label="Now">
      <span className="relative z-10 grid size-6 shrink-0 place-items-center">
        <span className="size-2 rounded-full bg-primary ring-4 ring-primary/20" />
      </span>
      <span className="text-[11px] font-medium tracking-wide text-primary uppercase">Now</span>
      <span aria-hidden="true" className="h-px flex-1 bg-linear-to-r from-primary/40 to-transparent" />
    </div>
  );
}
