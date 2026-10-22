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
import { type DueAction, useBulkDueAction } from "@/lib/dues";

/** Confirms marking the selected dues paid, or stopping their reminders, all at once. */
export function BulkActionDialog({
  ids,
  action,
  onOpenChange,
  onDone,
}: {
  ids: number[];
  action: DueAction | null;
  onOpenChange: (open: boolean) => void;
  onDone: () => void;
}) {
  const bulk = useBulkDueAction();
  const [error, setError] = useState<string>();
  // The last action stays on screen while the dialog animates closed.
  const [shown, setShown] = useState(action);
  if (action && action !== shown) setShown(action);
  const dues = `${ids.length} ${ids.length === 1 ? "due" : "dues"}`;

  const close = (open: boolean) => {
    if (!open) setError(undefined);
    onOpenChange(open);
  };

  const confirm = async (event: React.MouseEvent) => {
    event.preventDefault(); // keep the dialog open until the request has finished
    if (!action) return;
    try {
      await bulk.mutateAsync({ ids, action });
      close(false);
      onDone();
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : "Something went wrong. Please try again.");
    }
  };

  return (
    <AlertDialog open={action !== null} onOpenChange={close}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{shown === "cancel" ? `Stop reminders for ${dues}?` : `Mark ${dues} as paid?`}</AlertDialogTitle>
          <AlertDialogDescription>
            {shown === "cancel"
              ? "They are cancelled and no more reminders go out for them. This can't be undone."
              : "Their remaining reminders stop. Use this when they were paid outside PayNudge."}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <FormAlert message={error} />
        <AlertDialogFooter>
          <AlertDialogCancel disabled={bulk.isPending}>Keep reminding</AlertDialogCancel>
          <AlertDialogAction
            variant={shown === "cancel" ? "destructive" : "default"}
            onClick={confirm}
            disabled={bulk.isPending}
          >
            {bulk.isPending && <Loader2 className="motion-safe:animate-spin" aria-hidden="true" />}
            {shown === "cancel" ? "Stop reminders" : "Mark as paid"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
