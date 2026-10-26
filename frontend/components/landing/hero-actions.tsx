"use client";

import { ArrowRight, Loader2, Play } from "lucide-react";
import Link from "next/link";

import { FormAlert } from "@/components/forms";
import { Button } from "@/components/ui/button";
import { ApiError } from "@/lib/api";
import { useStartDemo } from "@/lib/auth";

/** "Try the live demo" opens a sample business straight away; signing up is the other way in. */
export function HeroActions() {
  const demo = useStartDemo();
  const opening = demo.isPending || demo.isSuccess;
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-3">
        <Button size="lg" onClick={() => demo.mutate()} disabled={opening}>
          {opening ? (
            <Loader2 className="motion-safe:animate-spin" aria-hidden="true" />
          ) : (
            <Play aria-hidden="true" />
          )}
          {opening ? "Opening the demo…" : "Try the live demo"}
        </Button>
        <Button asChild size="lg" variant="outline">
          <Link href="/signup">
            Get started
            <ArrowRight
              className="transition-transform duration-200 group-hover/button:translate-x-0.5 motion-reduce:transition-none"
              aria-hidden="true"
            />
          </Link>
        </Button>
      </div>
      <p className="text-sm text-muted-foreground">
        No sign-up for the demo: a sample tuition centre opens with two months of reminders and payments.
      </p>
      <FormAlert
        message={
          demo.isError
            ? demo.error instanceof ApiError
              ? demo.error.message
              : "Couldn't open the demo. Please try again."
            : undefined
        }
      />
    </div>
  );
}
