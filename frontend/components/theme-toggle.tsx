"use client";

import { Moon, Sun } from "lucide-react";

import { Button } from "@/components/ui/button";

/** Flips the .dark class on <html> and remembers the choice. Icons swap through CSS, so no hydration mismatch. */
export function ThemeToggle() {
  function toggle() {
    const isDark = document.documentElement.classList.toggle("dark");
    try {
      window.localStorage.setItem("paynudge.theme", isDark ? "dark" : "light");
    } catch {
      // Storage blocked: the choice lasts for this page view.
    }
  }

  return (
    <Button variant="ghost" size="icon" onClick={toggle} aria-label="Toggle light and dark theme">
      <Sun className="hidden dark:block" />
      <Moon className="dark:hidden" />
    </Button>
  );
}
