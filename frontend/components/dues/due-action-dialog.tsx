"use client";

import { Loader2 } from "lucide-react";
import { useState } from "react";

import { FormAlert } from "@/components/forms";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { ApiError } from "@/lib/api";
import { type DueAction, useDueAction } from "@/lib/dues";
import { formatPaise } from "@/lib/money";

const COPY: Record<DueAction, { title: string; description: string; confirm: string }> = {
  "mark-paid": {
    title: "Mark as paid?",
    description: "Their remaining reminders stop. Use this when they paid outside PayNudge, in cash or by transfer.",
    confirm: "Mark as paid",
  },
  cancel: {
    title: "Stop reminders?",
    description: "The due is cancelled and no more reminders go out for it. This can't be undone.",
    confirm: "Stop reminders",
  },
};

/** What the dialog needs to know about the due: a row of the list or the detail page both fit. */
export type DueRef = { id: number; amountPaise: number; customer: { name: string } };

/** Confirms before marking a due paid or cancelling it: either stops its reminders for good. */
export function DueActionDialog({
  target,
  onOpenChange,
}: {
  target: { due: DueRef; action: DueAction } | null;
  onOpenChange: (open: boolean) => void;
}) {
  const act = useDueAction();
  const [error, setError] = useState<string>();
  // The last target stays on screen while the dialog animates closed.
  const [shown, setShown] = useState(target);
  if (target && target !== shown) setShown(target);
  const copy = shown && COPY[shown.action];

  const close = (open: boolean) => {
    if (!open) setError(undefined);
    onOpenChange(open);
  };

  const confirm = async (event: React.MouseEvent) => {
    event.preventDefault(); // keep the dialog open until the request has finished
    if (!target) return;
    try {
      await act.mutateAsync({ id: target.due.id, action: target.action });
      close(false);
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : "Something went wrong. Please try again.");
    }
  };

  return (
    <AlertDialog open={target !== null} onOpenChange={close}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{copy?.title}</AlertDialogTitle>
          <AlertDialogDescription>
            {shown && `${shown.due.customer.name} · ${formatPaise(shown.due.amountPaise)}. `}
            {copy?.description}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <FormAlert message={error} />
        <AlertDialogFooter>
          <AlertDialogCancel disabled={act.isPending}>Keep reminding</AlertDialogCancel>
          <AlertDialogAction
            variant={shown?.action === "cancel" ? "destructive" : "default"}
            onClick={confirm}
            disabled={act.isPending}
          >
            {act.isPending && <Loader2 className="motion-safe:animate-spin" aria-hidden="true" />}
            {copy?.confirm}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
