"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";
import { useForm } from "react-hook-form";

import { applyServerError, FormAlert, TextField, useShake } from "@/components/forms";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { FieldGroup } from "@/components/ui/field";
import { type Customer, type CustomerInput, customerSchema, useSaveCustomer } from "@/lib/customers";

/** Adds a customer (customer is null) or edits one. */
export function CustomerDialog({
  customer,
  open,
  onOpenChange,
}: {
  customer: Customer | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{customer ? `Edit ${customer.name}` : "Add a customer"}</DialogTitle>
          <DialogDescription>Reminders go to the email. A phone number is kept for later channels.</DialogDescription>
        </DialogHeader>
        {/* Remount per customer so the form always starts from that customer's details. */}
        <CustomerForm key={customer?.id ?? "new"} customer={customer} onSaved={() => onOpenChange(false)} />
      </DialogContent>
    </Dialog>
  );
}

function CustomerForm({ customer, onSaved }: { customer: Customer | null; onSaved: () => void }) {
  const save = useSaveCustomer();
  const [scope, shake] = useShake<HTMLFormElement>();
  const form = useForm<CustomerInput>({
    resolver: zodResolver(customerSchema),
    defaultValues: { name: customer?.name ?? "", email: customer?.email ?? "", phone: customer?.phone ?? "" },
  });

  const onSubmit = form.handleSubmit(
    async (input) => {
      try {
        await save.mutateAsync({ id: customer?.id, input });
        onSaved();
      } catch (error) {
        applyServerError(error, form.setError, { CUSTOMER_EMAIL_TAKEN: "email" });
        shake();
      }
    },
    () => shake(),
  );

  return (
    <form ref={scope} onSubmit={onSubmit} noValidate className="space-y-5">
      <FieldGroup>
        <TextField control={form.control} name="name" label="Name" autoComplete="off" />
        <TextField control={form.control} name="email" label="Email" type="email" autoComplete="off" />
        <TextField
          control={form.control}
          name="phone"
          label="Phone (optional)"
          inputMode="numeric"
          autoComplete="off"
          description="Indian mobiles can skip +91."
        />
      </FieldGroup>
      <FormAlert message={form.formState.errors.root?.server?.message} />
      <DialogFooter>
        <Button type="submit" disabled={save.isPending}>
          {save.isPending && <Loader2 className="motion-safe:animate-spin" aria-hidden="true" />}
          {customer ? "Save changes" : "Add customer"}
        </Button>
      </DialogFooter>
    </form>
  );
}
