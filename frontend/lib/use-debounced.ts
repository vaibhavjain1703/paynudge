"use client";

import { useEffect, useState } from "react";

/** `value`, once it has stopped changing for `delayMs`: for search boxes that query as you type. */
export function useDebounced<T>(value: T, delayMs: number) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);
  return debounced;
}
