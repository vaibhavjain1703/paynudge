import Link from "next/link";

import { Wordmark } from "@/components/brand";
import { ThemeToggle } from "@/components/theme-toggle";

/** Privacy and terms: plain reading pages. */
export default function LegalLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-svh flex-col">
      <header className="mx-auto flex w-full max-w-3xl items-center justify-between px-4 py-4 sm:px-6">
        <Wordmark />
        <ThemeToggle />
      </header>
      <main className="mx-auto w-full max-w-3xl flex-1 px-4 pt-6 pb-16 sm:px-6">
        <article className="space-y-6 text-sm leading-relaxed text-muted-foreground [&_h1]:text-3xl [&_h1]:font-semibold [&_h1]:tracking-tight [&_h1]:text-foreground [&_h2]:pt-2 [&_h2]:text-base [&_h2]:font-medium [&_h2]:text-foreground [&_li]:ml-5 [&_li]:list-disc [&_ul]:space-y-1">
          {children}
        </article>
        <p className="mt-10 text-sm">
          <Link href="/" className="font-medium text-primary underline-offset-4 hover:underline">
            Back to PayNudge
          </Link>
        </p>
      </main>
    </div>
  );
}
