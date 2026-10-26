import { BellRing, CircleCheck, FileSpreadsheet, IndianRupee, ShieldCheck, SlidersHorizontal } from "lucide-react";
import Link from "next/link";

import { Wordmark } from "@/components/brand";
import { HeroActions } from "@/components/landing/hero-actions";
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

const steps = [
  {
    icon: FileSpreadsheet,
    title: "Bring your dues",
    body: "Upload a CSV from Excel or Google Sheets, or add dues one at a time. Every row is checked before anything is saved.",
  },
  {
    icon: SlidersHorizontal,
    title: "Pick a policy once",
    body: "How firmly to follow up, and when. PayNudge plans every reminder around your quiet hours.",
  },
  {
    icon: IndianRupee,
    title: "Get paid",
    body: "Each reminder carries a pay link for UPI, cards or netbanking. You see the payment the moment it lands.",
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
            <StaggerItem>
              <HeroActions />
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

        <section aria-labelledby="how-heading" className="space-y-6">
          <h2 id="how-heading" className="text-2xl font-semibold tracking-tight">
            How it works
          </h2>
          <Stagger as="ol" onView className="grid gap-4 sm:grid-cols-3" gap={0.08}>
            {steps.map(({ icon: Icon, title, body }, index) => (
              <StaggerItem key={title} as="li" className="space-y-3 rounded-xl border bg-card p-5">
                <div className="flex items-center gap-3">
                  <span className="grid size-8 place-items-center rounded-full bg-accent text-accent-foreground">
                    <Icon className="size-4" aria-hidden="true" />
                  </span>
                  <span className="font-mono text-xs text-muted-foreground">Step {index + 1}</span>
                </div>
                <h3 className="font-medium">{title}</h3>
                <p className="text-sm text-muted-foreground">{body}</p>
              </StaggerItem>
            ))}
          </Stagger>
        </section>
      </main>

      <footer className="border-t">
        <div className="mx-auto flex w-full max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-6 text-sm text-muted-foreground sm:px-6">
          <p>PayNudge: payment reminders for small businesses in India.</p>
          <nav aria-label="Footer" className="flex gap-4">
            <Link href="/privacy" className="hover:text-foreground">
              Privacy
            </Link>
            <Link href="/terms" className="hover:text-foreground">
              Terms
            </Link>
            <a href="https://github.com/vaibhavjain1703/paynudge" className="hover:text-foreground">
              Source on GitHub
            </a>
          </nav>
        </div>
      </footer>
    </div>
  );
}
