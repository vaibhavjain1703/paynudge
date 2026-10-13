"use client";

import { motion } from "motion/react";

import { fadeUp, staggered } from "@/lib/motion";

/** Children fade up one after another, either on load or when scrolled into view. */
export function Stagger({
  children,
  className,
  onView = false,
  gap,
  as = "div",
}: {
  children: React.ReactNode;
  className?: string;
  onView?: boolean;
  gap?: number;
  as?: "div" | "section";
}) {
  const Component = as === "section" ? motion.section : motion.div;
  const trigger = onView
    ? { whileInView: "show", viewport: { once: true, amount: 0.3 } }
    : { animate: "show" };
  return (
    <Component className={className} variants={staggered(gap)} initial="hidden" {...trigger}>
      {children}
    </Component>
  );
}

export function StaggerItem({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <motion.div className={className} variants={fadeUp}>
      {children}
    </motion.div>
  );
}
