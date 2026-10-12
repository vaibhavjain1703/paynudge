"use client";

import { animate, useInView } from "motion/react";
import { useEffect, useRef } from "react";

import { easeOut, usePrefersReducedMotion } from "@/lib/motion";
import { cn } from "@/lib/utils";

const formatInteger = (value: number) => Math.round(value).toLocaleString("en-IN");

/**
 * Counts up to `value` the first time it scrolls into view, and animates between values after that.
 * Screen readers get the final value only.
 */
export function AnimatedNumber({
  value,
  format = formatInteger,
  className,
}: {
  value: number;
  format?: (value: number) => string;
  className?: string;
}) {
  const ref = useRef<HTMLSpanElement>(null);
  const inView = useInView(ref, { once: true });
  const reduced = usePrefersReducedMotion();
  const shown = useRef(0);
  const formatRef = useRef(format);

  useEffect(() => {
    formatRef.current = format;
  }, [format]);

  useEffect(() => {
    const node = ref.current;
    if (!node || !inView) return;
    if (reduced) {
      node.textContent = formatRef.current(value);
      shown.current = value;
      return;
    }
    const controls = animate(shown.current, value, {
      duration: 0.4,
      ease: easeOut,
      onUpdate: (latest) => {
        node.textContent = formatRef.current(latest);
        shown.current = latest;
      },
    });
    return () => controls.stop();
  }, [value, inView, reduced]);

  return (
    <span className={cn("tabular-nums", className)}>
      <span ref={ref} aria-hidden="true">
        {format(0)}
      </span>
      <span className="sr-only">{format(value)}</span>
    </span>
  );
}
