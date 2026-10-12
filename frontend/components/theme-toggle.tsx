"use client";

import { Moon, Sun } from "lucide-react";

import { Button } from "@/components/ui/button";

/**
 * Flips the .dark class on <html> and remembers the choice. The icons swap through CSS (no hydration mismatch),
 * and where the browser supports view transitions the whole page crossfades instead of snapping.
 */
export function ThemeToggle() {
  function toggle() {
    const apply = () => {
      const isDark = document.documentElement.classList.toggle("dark");
      try {
        window.localStorage.setItem("paynudge.theme", isDark ? "dark" : "light");
      } catch {
        // Storage blocked: the choice lasts for this page view.
      }
    };
    const reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    if (!reduced && typeof document.startViewTransition === "function") {
      document.startViewTransition(apply);
    } else {
      apply();
    }
  }

  return (
    <Button variant="ghost" size="icon" onClick={toggle} aria-label="Toggle light and dark theme">
      <span className="relative grid size-4 place-items-center">
        <Sun className="absolute scale-0 -rotate-90 transition-transform duration-300 ease-out motion-reduce:transition-none dark:scale-100 dark:rotate-0" />
        <Moon className="absolute scale-100 rotate-0 transition-transform duration-300 ease-out motion-reduce:transition-none dark:scale-0 dark:rotate-90" />
      </span>
    </Button>
  );
}
