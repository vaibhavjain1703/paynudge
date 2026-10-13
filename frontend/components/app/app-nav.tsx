"use client";

import { motion } from "motion/react";
import Link from "next/link";
import { usePathname } from "next/navigation";

import { useSafeTransition } from "@/lib/motion";
import { cn } from "@/lib/utils";

const links = [
  { href: "/dashboard", label: "Dashboard" },
  { href: "/customers", label: "Customers" },
  { href: "/import", label: "Import" },
  { href: "/settings", label: "Settings" },
];

/** The section tabs under the header. The underline glides to the active tab. */
export function AppNav() {
  const pathname = usePathname();
  const transition = useSafeTransition({ type: "spring", bounce: 0.2, duration: 0.35 });

  return (
    <nav aria-label="Main" className="-mb-px flex gap-1 overflow-x-auto">
      {links.map(({ href, label }) => {
        const active = pathname === href || pathname.startsWith(`${href}/`);
        return (
          <Link
            key={href}
            href={href}
            aria-current={active ? "page" : undefined}
            className={cn(
              "relative px-3 py-2.5 text-sm whitespace-nowrap transition-colors",
              active ? "text-foreground" : "text-muted-foreground hover:text-foreground",
            )}
          >
            {label}
            {active && (
              <motion.span
                layoutId="app-nav-underline"
                transition={transition}
                className="absolute inset-x-2 bottom-0 h-0.5 rounded-full bg-primary"
                aria-hidden="true"
              />
            )}
          </Link>
        );
      })}
    </nav>
  );
}
