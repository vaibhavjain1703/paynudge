"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";

import { API_URL } from "@/lib/api";
import { type DueDetail, dueKeys } from "@/lib/dues";
import { useToken } from "@/lib/session";

type ServerEvent = { name: string; data: string };

/**
 * Keeps the signed-in owner's pages live. The API's event stream is read with fetch (EventSource cannot send the
 * bearer token, and a token in the URL would end up in logs):
 * - "due": a due changed; it goes straight into the cache, so its open timeline updates without a request, and the
 *   lists and dashboard refetch;
 * - "ready" (on every connect, so a reconnect is never stale) and "dues" (many changed): refetch what is on screen.
 * A dropped stream reconnects after 1 s, then 2, 4, ... up to 30 s.
 */
export function useLiveUpdates() {
  const token = useToken();
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!token) return;
    const stop = new AbortController();
    const refetchShown = () => queryClient.invalidateQueries({ queryKey: dueKeys.all });

    const run = async () => {
      let wait = 1_000;
      while (!stop.signal.aborted) {
        try {
          const response = await fetch(`${API_URL}/api/events`, {
            headers: { Authorization: `Bearer ${token}`, Accept: "text/event-stream" },
            signal: stop.signal,
          });
          // A rejected session: the app signs the owner out on its next request; nothing to stream.
          if (response.status === 401) return;
          if (!response.ok || !response.body) throw new Error(`HTTP ${response.status}`);
          wait = 1_000;
          for await (const event of eventsOf(response.body)) {
            if (event.name === "due") {
              const due = JSON.parse(event.data) as DueDetail;
              queryClient.setQueryData(dueKeys.detail(due.id), due);
              await queryClient.invalidateQueries({
                queryKey: dueKeys.all,
                predicate: (query) => query.queryKey[1] !== "detail",
              });
            } else if (event.name === "ready" || event.name === "dues") {
              await refetchShown();
            }
          }
        } catch {
          if (stop.signal.aborted) return;
        }
        await pause(wait, stop.signal);
        wait = Math.min(wait * 2, 30_000);
      }
    };
    void run();
    return () => stop.abort();
  }, [token, queryClient]);
}

/** Server-sent events from a response body: blocks split by a blank line, comments (":") skipped. */
async function* eventsOf(body: ReadableStream<Uint8Array>): AsyncGenerator<ServerEvent> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  while (true) {
    const { value, done } = await reader.read();
    if (done) return;
    buffer += decoder.decode(value, { stream: true }).replaceAll("\r\n", "\n");
    let end = buffer.indexOf("\n\n");
    while (end >= 0) {
      const event = parse(buffer.slice(0, end));
      buffer = buffer.slice(end + 2);
      if (event) yield event;
      end = buffer.indexOf("\n\n");
    }
  }
}

function parse(block: string): ServerEvent | null {
  let name = "message";
  const data: string[] = [];
  for (const line of block.split("\n")) {
    if (line.startsWith(":")) continue;
    const colon = line.indexOf(":");
    const field = colon < 0 ? line : line.slice(0, colon);
    const value = colon < 0 ? "" : line.slice(colon + 1).replace(/^ /, "");
    if (field === "event") name = value;
    if (field === "data") data.push(value);
  }
  return data.length > 0 ? { name, data: data.join("\n") } : null;
}

function pause(ms: number, signal: AbortSignal) {
  return new Promise<void>((resolve) => {
    const timer = setTimeout(resolve, ms);
    signal.addEventListener("abort", () => {
      clearTimeout(timer);
      resolve();
    });
  });
}
