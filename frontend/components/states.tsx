"use client";

import { AlertTriangle, type LucideIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
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
    <div
      role="alert"
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
    </div>
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
      <div className="rounded-full bg-accent p-3 text-accent-foreground">
        <Icon className="size-5" aria-hidden="true" />
      </div>
      <div className="max-w-sm space-y-1">
        <p className="font-medium">{title}</p>
        <p className="text-sm text-muted-foreground">{description}</p>
      </div>
      {action}
    </div>
  );
}
