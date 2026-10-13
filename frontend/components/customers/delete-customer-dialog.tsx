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
import { type Customer, useDeleteCustomer } from "@/lib/customers";

/** Confirms before deleting: it also removes the customer's dues and reminder history, and can't be undone. */
export function DeleteCustomerDialog({
  customer,
  onOpenChange,
}: {
  customer: Customer | null;
  onOpenChange: (open: boolean) => void;
}) {
  const remove = useDeleteCustomer();
  const [error, setError] = useState<string>();

  const close = (open: boolean) => {
    if (!open) setError(undefined);
    onOpenChange(open);
  };

  const confirm = async (event: React.MouseEvent) => {
    event.preventDefault(); // keep the dialog open until the delete has finished
    if (!customer) return;
    try {
      await remove.mutateAsync(customer.id);
      close(false);
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : "Something went wrong. Please try again.");
    }
  };

  return (
    <AlertDialog open={customer !== null} onOpenChange={close}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Delete {customer?.name}?</AlertDialogTitle>
          <AlertDialogDescription>
            This also deletes their dues and reminder history. It can&apos;t be undone.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <FormAlert message={error} />
        <AlertDialogFooter>
          <AlertDialogCancel disabled={remove.isPending}>Cancel</AlertDialogCancel>
          <AlertDialogAction variant="destructive" onClick={confirm} disabled={remove.isPending}>
            {remove.isPending && <Loader2 className="motion-safe:animate-spin" aria-hidden="true" />}
            Delete customer
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
