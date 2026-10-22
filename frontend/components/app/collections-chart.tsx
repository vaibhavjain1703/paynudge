"use client";

import { motion } from "motion/react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { formatDayMonth, formatPaise, formatPaiseCompact } from "@/lib/money";
import { easeOut, useSafeTransition } from "@/lib/motion";
import { cn } from "@/lib/utils";

type Week = { weekStart: string; paise: number };

/**
 * Money collected per week, one bar a week, this week last. One series in one hue, so no legend: the card's title
 * names it. The tallest bar carries its value; every bar shows its own on hover or keyboard focus; and the same
 * numbers are one click away as a table.
 */
export function CollectionsChart({ weeks }: { weeks: Week[] }) {
  const [asTable, setAsTable] = useState(false);
  const [active, setActive] = useState<number | null>(null);
  const transition = useSafeTransition({ duration: 0.3, ease: easeOut });
  const max = Math.max(0, ...weeks.map((week) => week.paise));
  const top = niceCeiling(max);
  const tallest = max > 0 ? weeks.findIndex((week) => week.paise === max) : -1;

  return (
    <div className="space-y-3">
      <div className="flex items-start justify-between gap-4">
        <div className="space-y-0.5">
          <h2 className="font-medium">Collections</h2>
          <p className="text-sm text-muted-foreground">Paid each week, over the last 12 weeks</p>
        </div>
        <Button variant="ghost" size="sm" aria-pressed={asTable} onClick={() => setAsTable(!asTable)}>
          {asTable ? "Chart" : "Table"}
        </Button>
      </div>

      {asTable ? (
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead>Week of</TableHead>
              <TableHead className="text-right">Collected</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {weeks.map((week) => (
              <TableRow key={week.weekStart}>
                <TableCell>{formatDayMonth(week.weekStart)}</TableCell>
                <TableCell className="text-right font-mono tabular-nums">{formatPaise(week.paise)}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      ) : (
        <div className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-2">
          {/* Axis: zero, half and the top, compact. */}
          <div className="relative h-40 w-12 text-right text-[11px] text-muted-foreground tabular-nums" aria-hidden="true">
            {[1, 0.5, 0].map((share) => (
              <span key={share} className="absolute right-0 -translate-y-1/2" style={{ top: `${(1 - share) * 100}%` }}>
                {formatPaiseCompact(top * share)}
              </span>
            ))}
          </div>
          <div className="relative h-40">
            {[0, 0.5, 1].map((share) => (
              <span
                key={share}
                aria-hidden="true"
                className="absolute inset-x-0 h-px bg-border"
                style={{ top: `${share * 100}%` }}
              />
            ))}
            {max === 0 && (
              <p className="absolute inset-0 grid place-items-center text-sm text-muted-foreground">
                No payments in the last 12 weeks
              </p>
            )}
            <div className="absolute inset-0 flex" role="list" aria-label="Collected per week">
              {weeks.map((week, index) => {
                const height = (week.paise / top) * 100;
                const label = `Week of ${formatDayMonth(week.weekStart)}: ${formatPaise(week.paise)}`;
                return (
                  <div key={week.weekStart} role="listitem" className="relative flex flex-1 items-end justify-center">
                    {week.paise > 0 && (
                      <motion.div
                        aria-hidden="true"
                        className={cn(
                          "w-[min(24px,60%)] origin-bottom rounded-t-[4px] bg-chart-1 transition-opacity duration-150",
                          active === index && "opacity-80",
                        )}
                        style={{ height: `${height}%` }}
                        initial={{ scaleY: 0 }}
                        animate={{ scaleY: 1 }}
                        transition={{ ...transition, delay: index * 0.02 }}
                      />
                    )}
                    {index === tallest && active !== index && (
                      <span
                        aria-hidden="true"
                        className="absolute -translate-y-1 text-[11px] font-medium whitespace-nowrap text-foreground"
                        style={{ bottom: `${height}%` }}
                      >
                        {formatPaiseCompact(week.paise)}
                      </span>
                    )}
                    {active === index && (
                      <span
                        aria-hidden="true"
                        className="pointer-events-none absolute z-10 -translate-y-2 rounded-md border bg-popover px-2 py-1 text-center whitespace-nowrap shadow-md"
                        style={{ bottom: `${height}%` }}
                      >
                        <span className="block text-sm font-semibold">{formatPaise(week.paise)}</span>
                        <span className="block text-[11px] text-muted-foreground">
                          Week of {formatDayMonth(week.weekStart)}
                        </span>
                      </span>
                    )}
                    {/* The hit area is the whole column, not just the painted bar. */}
                    <button
                      type="button"
                      aria-label={label}
                      className="absolute inset-0 rounded-sm focus-visible:ring-2 focus-visible:ring-ring/50 focus-visible:outline-none"
                      onPointerEnter={() => setActive(index)}
                      onPointerLeave={() => setActive((current) => (current === index ? null : current))}
                      onFocus={() => setActive(index)}
                      onBlur={() => setActive((current) => (current === index ? null : current))}
                    />
                  </div>
                );
              })}
            </div>
          </div>
          <div aria-hidden="true" />
          <div className="mt-1.5 flex text-[11px] text-muted-foreground" aria-hidden="true">
            {weeks.map((week, index) => (
              <span key={week.weekStart} className="flex-1 text-center whitespace-nowrap">
                {index === weeks.length - 1 ? "This week" : index % 4 === 0 ? formatDayMonth(week.weekStart) : ""}
              </span>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

/** The axis top: the smallest 1, 2, 2.5 or 5 × 10ⁿ rupees at or above the largest week (₹1,000 when there is none). */
function niceCeiling(paise: number) {
  if (paise <= 0) return 100_000;
  const rupees = paise / 100;
  const magnitude = 10 ** Math.floor(Math.log10(rupees));
  const step = [1, 2, 2.5, 5, 10].find((candidate) => candidate * magnitude >= rupees)!;
  return step * magnitude * 100;
}
