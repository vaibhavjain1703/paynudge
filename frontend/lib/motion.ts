import { type Transition, type Variants, stagger } from "motion/react";
import { useSyncExternalStore } from "react";

/**
 * Shared motion language. Durations stay within 150-400 ms. MotionConfig (app/providers.tsx) already drops
 * transform animations for people who ask for reduced motion; anything else (height, timers, loops) checks
 * usePrefersReducedMotion itself.
 */
export const easeOut: [number, number, number, number] = [0.22, 1, 0.36, 1];

export const fadeUp: Variants = {
  hidden: { opacity: 0, y: 10 },
  show: { opacity: 1, y: 0, transition: { duration: 0.35, ease: easeOut } },
};

export function staggered(gap = 0.07, startDelay = 0): Variants {
  return { hidden: {}, show: { transition: { delayChildren: stagger(gap, { startDelay }) } } };
}

/** Expand-and-fade for content that appears inline (errors, alerts), so the layout does not jump. */
export const expand = {
  initial: { opacity: 0, height: 0 },
  animate: { opacity: 1, height: "auto" },
  exit: { opacity: 0, height: 0 },
};

const REDUCED_MOTION = "(prefers-reduced-motion: reduce)";

function subscribe(onChange: () => void) {
  const query = window.matchMedia(REDUCED_MOTION);
  query.addEventListener("change", onChange);
  return () => query.removeEventListener("change", onChange);
}

/** Hydration-safe: false on the server and during hydration, then the real preference. */
export function usePrefersReducedMotion(): boolean {
  return useSyncExternalStore(
    subscribe,
    () => window.matchMedia(REDUCED_MOTION).matches,
    () => false,
  );
}

/** Returns an instant transition when the user prefers reduced motion. */
export function useSafeTransition(transition: Transition): Transition {
  return usePrefersReducedMotion() ? { duration: 0 } : transition;
}
