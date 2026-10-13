"use client";

import { FileSpreadsheet, Loader2, Upload } from "lucide-react";
import { motion } from "motion/react";
import { useState } from "react";

import { useSafeTransition } from "@/lib/motion";
import { cn } from "@/lib/utils";

/** A drop zone that is also a file picker (click, or focus and press Enter). */
export function FileDrop({ onFile, busy }: { onFile: (file: File) => void; busy: boolean }) {
  const [dragging, setDragging] = useState(false);
  const transition = useSafeTransition({ duration: 0.2 });

  return (
    <motion.label
      htmlFor="csv-file"
      onDragOver={(event) => {
        event.preventDefault();
        if (!busy) setDragging(true);
      }}
      onDragLeave={() => setDragging(false)}
      onDrop={(event) => {
        event.preventDefault();
        setDragging(false);
        const file = event.dataTransfer.files[0];
        if (file && !busy) onFile(file);
      }}
      animate={{ scale: dragging ? 1.01 : 1 }}
      transition={transition}
      className={cn(
        "flex cursor-pointer flex-col items-center gap-3 rounded-xl border-2 border-dashed p-10 text-center transition-colors focus-within:ring-3 focus-within:ring-ring/50",
        dragging ? "border-primary bg-primary/5" : "hover:border-primary/50",
        busy && "cursor-wait opacity-70",
      )}
    >
      <motion.div
        animate={{ y: dragging ? -4 : 0 }}
        transition={transition}
        className="rounded-full bg-accent p-3 text-accent-foreground"
      >
        {busy ? (
          <Loader2 className="size-5 motion-safe:animate-spin" aria-hidden="true" />
        ) : dragging ? (
          <FileSpreadsheet className="size-5" aria-hidden="true" />
        ) : (
          <Upload className="size-5" aria-hidden="true" />
        )}
      </motion.div>
      <div className="space-y-1">
        <p className="font-medium">{busy ? "Reading your file…" : "Drop a CSV file here, or click to choose one"}</p>
        <p className="text-sm text-muted-foreground">
          One row per due: name, email or phone, amount and due date (day/month/year). Up to 5,000 rows.
        </p>
      </div>
      <input
        id="csv-file"
        type="file"
        accept=".csv,text/csv,text/plain"
        className="sr-only"
        aria-label="Choose a CSV file"
        disabled={busy}
        onChange={(event) => {
          const file = event.target.files?.[0];
          if (file) onFile(file);
          event.target.value = ""; // choosing the same file again still triggers a change
        }}
      />
    </motion.label>
  );
}
