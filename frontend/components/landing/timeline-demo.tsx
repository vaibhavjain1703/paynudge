"use client";

import { Check, Clock, IndianRupee, X } from "lucide-react";
import { AnimatePresence, motion, useInView } from "motion/react";
import { useEffect, useRef, useState } from "react";

import { easeOut, expand, usePrefersReducedMotion } from "@/lib/motion";
import { cn } from "@/lib/utils";

type StepState = "scheduled" | "sending" | "sent" | "cancelled";

const STEPS = [
  { label: "Friendly reminder", when: "2 days before" },
  { label: "Due today", when: "On the due date" },
  { label: "Firm reminder", when: "3 days late" },
  { label: "Final notice", when: "10 days late" },
];

/** The story, one frame at a time: two reminders go out, she pays, the rest cancel themselves. */
const FRAMES: { steps: StepState[]; paid: boolean; dwellMs: number }[] = [
  { steps: ["scheduled", "scheduled", "scheduled", "scheduled"], paid: false, dwellMs: 1200 },
  { steps: ["sending", "scheduled", "scheduled", "scheduled"], paid: false, dwellMs: 900 },
  { steps: ["sent", "scheduled", "scheduled", "scheduled"], paid: false, dwellMs: 1100 },
  { steps: ["sent", "sending", "scheduled", "scheduled"], paid: false, dwellMs: 900 },
  { steps: ["sent", "sent", "scheduled", "scheduled"], paid: false, dwellMs: 1300 },
  { steps: ["sent", "sent", "scheduled", "scheduled"], paid: true, dwellMs: 900 },
  { steps: ["sent", "sent", "cancelled", "cancelled"], paid: true, dwellMs: 3400 },
];

const FINAL_FRAME = FRAMES.length - 1;

/**
 * The landing page's signature visual: a due's reminder timeline playing out on a loop. It only runs while on
 * screen and in a visible tab; with reduced motion it shows the finished story without animating.
 */
export function TimelineDemo() {
  const ref = useRef<HTMLDivElement>(null);
  const inView = useInView(ref, { amount: 0.3 });
  const reduced = usePrefersReducedMotion();
  const [frame, setFrame] = useState(0);

  useEffect(() => {
    if (reduced || !inView) return;
    const timer = window.setTimeout(() => {
      if (!document.hidden) setFrame((current) => (current + 1) % FRAMES.length);
    }, FRAMES[frame].dwellMs);
    return () => window.clearTimeout(timer);
  }, [frame, inView, reduced]);

  const { steps, paid } = FRAMES[reduced ? FINAL_FRAME : frame];
  const sent = steps.filter((state) => state === "sent").length;
  const cancelled = steps.filter((state) => state === "cancelled").length;

  return (
    <div ref={ref} className="relative" data-testid="timeline-demo">
      <div
        aria-hidden="true"
        className="absolute -inset-6 -z-10 rounded-[2rem] bg-primary/15 opacity-70 blur-3xl dark:bg-primary/10"
      />
      <p className="sr-only">
        Example: Asha owes ₹2,500 in October fees. PayNudge sends a friendly reminder two days before the due date
        and another on the day. When she pays, the firm reminder and the final notice are cancelled automatically.
      </p>

      <div aria-hidden="true" className="rounded-2xl border bg-card p-5 shadow-sm">
        <div className="flex items-center gap-3">
          <div className="grid size-10 place-items-center rounded-full bg-accent text-sm font-semibold text-accent-foreground">
            AV
          </div>
          <div className="min-w-0 flex-1">
            <p className="truncate font-medium">Asha Verma</p>
            <p className="text-xs text-muted-foreground">October fees · due 5 Oct</p>
          </div>
          <div className="flex flex-col items-end gap-1">
            <span className="font-mono text-sm font-medium">₹2,500</span>
            <StatusPill paid={paid} />
          </div>
        </div>

        <div className="relative mt-5">
          <span className="absolute top-4 bottom-4 left-3 w-px bg-border" />
          <ol className="relative">
            {STEPS.map((step, index) => (
              <StepGroup key={step.label} index={index} paid={paid}>
                <StepRow label={step.label} when={step.when} state={steps[index]} />
              </StepGroup>
            ))}
          </ol>
        </div>

        <div className="mt-3 min-h-11 border-t pt-3 text-xs text-muted-foreground sm:min-h-8">
          <AnimatePresence mode="wait" initial={false}>
            <motion.p
              key={cancelled > 0 ? "done" : `sent-${sent}`}
              initial={{ opacity: 0, y: 4 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -4 }}
              transition={{ duration: 0.2, ease: easeOut }}
            >
              {cancelled > 0
                ? `Paid. ${cancelled} pending reminders cancelled automatically.`
                : `${sent} of ${STEPS.length} sent · stops the moment she pays`}
            </motion.p>
          </AnimatePresence>
        </div>
      </div>
    </div>
  );
}

/** Inserts the payment row after the second reminder, where it lands in the story. */
function StepGroup({ index, paid, children }: { index: number; paid: boolean; children: React.ReactNode }) {
  return (
    <>
      {children}
      {index === 1 && (
        <AnimatePresence initial={false}>
          {paid && (
            <motion.li key="payment" {...expand} transition={{ duration: 0.3, ease: easeOut }} className="overflow-hidden">
              <div className="relative -mx-2 my-1 flex items-center gap-3 rounded-lg border border-success/30 bg-success/10 px-2 py-2">
                <motion.span
                  initial={{ scale: 0.4 }}
                  animate={{ scale: 1 }}
                  transition={{ type: "spring", bounce: 0.5, duration: 0.4 }}
                  className="relative z-10 grid size-6 shrink-0 place-items-center rounded-full bg-success text-success-foreground"
                >
                  <IndianRupee className="size-3.5" />
                </motion.span>
                <div className="flex-1">
                  <p className="text-sm font-medium">Payment received</p>
                  <p className="text-xs text-muted-foreground">₹2,500 via UPI · webhook verified</p>
                </div>
              </div>
            </motion.li>
          )}
        </AnimatePresence>
      )}
    </>
  );
}

function StepRow({ label, when, state }: { label: string; when: string; state: StepState }) {
  const cancelled = state === "cancelled";
  return (
    <li className="relative flex items-center gap-3 py-2">
      <StepNode state={state} />
      <motion.div
        animate={{ opacity: cancelled ? 0.5 : 1 }}
        transition={{ duration: 0.3 }}
        className="flex min-w-0 flex-1 items-center justify-between gap-3"
      >
        <div className="min-w-0">
          <p className={cn("truncate text-sm font-medium", cancelled && "line-through decoration-muted-foreground")}>
            {label}
          </p>
          <p className="text-xs text-muted-foreground">{when}</p>
        </div>
        <StepBadge state={state} />
      </motion.div>
    </li>
  );
}

const NODE_STYLES: Record<StepState, string> = {
  scheduled: "border border-dashed border-muted-foreground/40 bg-card text-muted-foreground",
  sending: "border border-info bg-info/15 text-info",
  sent: "bg-primary text-primary-foreground",
  cancelled: "bg-muted text-muted-foreground",
};

function StepNode({ state }: { state: StepState }) {
  return (
    <span
      className={cn(
        "relative z-10 grid size-6 shrink-0 place-items-center rounded-full transition-colors duration-300",
        NODE_STYLES[state],
      )}
    >
      {state === "sending" && (
        <motion.span
          className="absolute inset-0 rounded-full border border-info"
          initial={{ scale: 1, opacity: 0.7 }}
          animate={{ scale: 1.8, opacity: 0 }}
          transition={{ duration: 0.9, repeat: Infinity, ease: "easeOut" }}
        />
      )}
      <AnimatePresence mode="popLayout" initial={false}>
        <motion.span
          key={state}
          initial={{ scale: 0.4, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          exit={{ scale: 0.4, opacity: 0 }}
          transition={{ type: "spring", bounce: 0.45, duration: 0.35 }}
          className="grid place-items-center"
        >
          {state === "sent" && <Check className="size-3.5" strokeWidth={3} />}
          {state === "cancelled" && <X className="size-3.5" />}
          {state === "scheduled" && <Clock className="size-3" />}
          {state === "sending" && <span className="size-1.5 rounded-full bg-info" />}
        </motion.span>
      </AnimatePresence>
    </span>
  );
}

const BADGES: Record<StepState, { text: string; className: string }> = {
  scheduled: { text: "Scheduled", className: "bg-muted text-muted-foreground" },
  sending: { text: "Sending", className: "bg-info/15 text-info" },
  sent: { text: "Sent", className: "bg-success/15 text-success" },
  cancelled: { text: "Cancelled", className: "bg-muted text-muted-foreground" },
};

function StepBadge({ state }: { state: StepState }) {
  const badge = BADGES[state];
  return (
    <span
      className={cn(
        "relative inline-flex h-5 min-w-20 items-center justify-center overflow-hidden rounded-full px-2 text-[11px] font-medium transition-colors duration-300",
        badge.className,
      )}
    >
      <AnimatePresence mode="popLayout" initial={false}>
        <motion.span
          key={state}
          initial={{ y: 10, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          exit={{ y: -10, opacity: 0 }}
          transition={{ duration: 0.25, ease: easeOut }}
        >
          {badge.text}
        </motion.span>
      </AnimatePresence>
    </span>
  );
}

function StatusPill({ paid }: { paid: boolean }) {
  return (
    <span
      className={cn(
        "relative inline-flex h-5 min-w-14 items-center justify-center overflow-hidden rounded-full px-2 text-[11px] font-semibold transition-colors duration-300",
        paid ? "bg-success/15 text-success" : "bg-info/15 text-info",
      )}
    >
      <AnimatePresence mode="popLayout" initial={false}>
        <motion.span
          key={paid ? "paid" : "active"}
          initial={{ y: 10, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          exit={{ y: -10, opacity: 0 }}
          transition={{ duration: 0.25, ease: easeOut }}
        >
          {paid ? "Paid" : "Active"}
        </motion.span>
      </AnimatePresence>
    </span>
  );
}
