"use client";

import Link from "next/link";

import { Skeleton } from "@/components/ui/skeleton";
import { type Policy, summarizePolicy } from "@/lib/policies";

/** Which reminder policy new dues follow, whether they come from a file or are typed in. */
export function PolicyPicker({
  policies,
  loading,
  value,
  onChange,
  disabled,
  description,
}: {
  policies?: Policy[];
  loading: boolean;
  value?: number;
  onChange: (id: number) => void;
  disabled: boolean;
  description: string;
}) {
  const selected = policies?.find((policy) => policy.id === value);
  return (
    <section aria-labelledby="policy-heading" className="space-y-3">
      <div className="space-y-1">
        <h2 id="policy-heading" className="font-medium">
          Reminders
        </h2>
        <p className="text-sm text-muted-foreground">
          {description}{" "}
          <Link href="/policies" className="font-medium text-primary underline-offset-4 hover:underline">
            See the policies
          </Link>
        </p>
      </div>
      {loading || !policies ? (
        <Skeleton className="h-8 w-full max-w-sm" />
      ) : (
        <div className="space-y-1.5">
          <label htmlFor="policy" className="sr-only">
            Reminder policy
          </label>
          <select
            id="policy"
            value={value ?? ""}
            disabled={disabled}
            onChange={(event) => onChange(Number(event.target.value))}
            className="h-8 w-full max-w-sm rounded-lg border border-input bg-transparent px-2 text-base outline-none transition-colors focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 md:text-sm dark:bg-input/30 [&>option]:bg-popover [&>option]:text-popover-foreground"
          >
            {policies.map((policy) => (
              <option key={policy.id} value={policy.id}>
                {policy.name}
              </option>
            ))}
          </select>
          {selected && <p className="text-sm text-muted-foreground">{summarizePolicy(selected)}</p>}
        </div>
      )}
    </section>
  );
}
