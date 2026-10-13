"use client";

import { AlertTriangle, type LucideIcon } from "lucide-react";
import { motion } from "motion/react";

import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
import { fadeUp } from "@/lib/motion";
import { cn } from "@/lib/utils";

/** Shown when a view's data failed to load. Always offers a retry. */
export function ErrorState({
  title = "Couldn't load this",
  error,
  onRetry,
  className,
}: {
  title?: string;
  error: unknown;
  onRetry: () => void;
  className?: string;
}) {
  const message =
    error instanceof ApiError ? error.message : "Something went wrong. Please try again.";
  return (
    <motion.div
      role="alert"
      variants={fadeUp}
      initial="hidden"
      animate="show"
      className={cn(
        "flex flex-col items-center gap-3 rounded-xl border border-dashed p-8 text-center",
        className,
      )}
    >
      <AlertTriangle className="size-6 text-destructive" aria-hidden="true" />
      <div className="space-y-1">
        <p className="font-medium">{title}</p>
        <p className="text-sm text-muted-foreground">{message}</p>
      </div>
      <Button variant="outline" size="sm" onClick={onRetry}>
        Try again
      </Button>
    </motion.div>
  );
}

/** Shown when a view loaded fine but has nothing in it yet. */
export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
  className,
}: {
  icon: LucideIcon;
  title: string;
  description: string;
  action?: React.ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex flex-col items-center gap-3 rounded-xl border border-dashed p-10 text-center",
        className,
      )}
    >
      <motion.div
        initial={{ scale: 0.6, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ type: "spring", bounce: 0.4, duration: 0.3 }}
        className="rounded-full bg-accent p-3 text-accent-foreground"
      >
        <Icon className="size-5" aria-hidden="true" />
      </motion.div>
      <div className="max-w-sm space-y-1">
        <p className="font-medium">{title}</p>
        <p className="text-sm text-muted-foreground">{description}</p>
      </div>
      {action}
    </div>
  );
}
