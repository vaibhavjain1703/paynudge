import Link from "next/link";

import { cn } from "@/lib/utils";

/** The PayNudge mark: a bell on the brand colour. The bell gives a little nudge when its link is hovered. */
export function Logo({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" className={cn("size-6", className)}>
      <rect width="24" height="24" rx="6" className="fill-primary" />
      <g className="origin-top [transform-box:fill-box] motion-safe:group-hover/brand:animate-nudge">
        <path
          d="M12 5.5a4.5 4.5 0 0 0-4.5 4.5v3.2L6.3 15.4a.6.6 0 0 0 .5.9h10.4a.6.6 0 0 0 .5-.9l-1.2-2.2V10A4.5 4.5 0 0 0 12 5.5Z"
          className="fill-primary-foreground"
        />
        <path d="M10.2 17.6a1.9 1.9 0 0 0 3.6 0Z" className="fill-primary-foreground" />
      </g>
    </svg>
  );
}

export function Wordmark({ href = "/", className }: { href?: string; className?: string }) {
  return (
    <Link
      href={href}
      className={cn("group/brand inline-flex items-center gap-2 text-base font-semibold tracking-tight", className)}
    >
      <Logo />
      PayNudge
    </Link>
  );
}
