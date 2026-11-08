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
  as?: "div" | "section" | "ol";
}) {
  const Component = as === "section" ? motion.section : as === "ol" ? motion.ol : motion.div;
  const trigger = onView
    ? { whileInView: "show", viewport: { once: true, amount: 0.3 } }
    : { animate: "show" };
  return (
    <Component className={className} variants={staggered(gap)} initial="hidden" {...trigger}>
      {children}
    </Component>
  );
}

export function StaggerItem({
  children,
  className,
  as = "div",
}: {
  children: React.ReactNode;
  className?: string;
  as?: "div" | "li";
}) {
  const Component = as === "li" ? motion.li : motion.div;
  return (
    <Component className={className} variants={fadeUp}>
      {children}
    </Component>
  );
}
