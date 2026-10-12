import { ArrowRight, BellRing, CircleCheck, ShieldCheck } from "lucide-react";
import Link from "next/link";

import { Wordmark } from "@/components/brand";
import { TimelineDemo } from "@/components/landing/timeline-demo";
import { Stagger, StaggerItem } from "@/components/motion/stagger";
import { ThemeToggle } from "@/components/theme-toggle";
import { Button } from "@/components/ui/button";

const points = [
  {
    icon: BellRing,
    title: "Reminders that escalate",
    body: "Friendly, then firm, then a final notice. Pick a policy once and every due follows it.",
  },
  {
    icon: CircleCheck,
    title: "Stops the moment they pay",
    body: "A verified payment cancels every remaining reminder. Nobody who paid hears from you again.",
  },
  {
    icon: ShieldCheck,
    title: "Never sends twice",
    body: "Quiet hours, daily caps and an unsubscribe link on every message. Crashes never cause a double send.",
  },
];

export default function LandingPage() {
  return (
    <div className="flex min-h-svh flex-col overflow-x-clip">
      <header className="mx-auto flex w-full max-w-6xl items-center justify-between px-4 py-4 sm:px-6">
        <Wordmark />
        <div className="flex items-center gap-1">
          <ThemeToggle />
          <Button asChild variant="ghost" size="sm">
            <Link href="/login">Log in</Link>
          </Button>
        </div>
      </header>

      <main className="mx-auto flex w-full max-w-6xl flex-1 flex-col justify-center gap-16 px-4 py-12 sm:px-6 lg:py-16">
        <div className="grid items-center gap-12 lg:grid-cols-[1.1fr_0.9fr]">
          <Stagger as="section" className="max-w-2xl space-y-6" gap={0.08}>
            <StaggerItem>
              <p className="font-mono text-xs tracking-widest text-primary uppercase">
                For tuition centres, gyms and PGs
              </p>
            </StaggerItem>
            <StaggerItem>
              <h1 className="text-4xl font-semibold tracking-tight text-balance sm:text-5xl">
                Dues that collect themselves.
              </h1>
            </StaggerItem>
            <StaggerItem>
              <p className="text-lg text-pretty text-muted-foreground">
                Import a sheet of who owes what, choose how firmly to follow up, and PayNudge sends every reminder on
                time until each customer pays.
              </p>
            </StaggerItem>
            <StaggerItem className="flex flex-wrap gap-3">
              <Button asChild size="lg">
                <Link href="/signup">
                  Get started
                  <ArrowRight
                    className="transition-transform duration-200 group-hover/button:translate-x-0.5 motion-reduce:transition-none"
                    aria-hidden="true"
                  />
                </Link>
              </Button>
              <Button asChild size="lg" variant="outline">
                <Link href="/login">I have an account</Link>
              </Button>
            </StaggerItem>
          </Stagger>

          <Stagger className="w-full max-w-md justify-self-center lg:justify-self-end" gap={0.1}>
            <StaggerItem>
              <TimelineDemo />
            </StaggerItem>
          </Stagger>
        </div>

        <Stagger as="section" onView className="grid gap-6 sm:grid-cols-3" gap={0.08}>
          {points.map(({ icon: Icon, title, body }) => (
            <StaggerItem key={title} className="space-y-2">
              <Icon className="size-5 text-primary" aria-hidden="true" />
              <h2 className="font-medium">{title}</h2>
              <p className="text-sm text-muted-foreground">{body}</p>
            </StaggerItem>
          ))}
        </Stagger>
      </main>
    </div>
  );
}
