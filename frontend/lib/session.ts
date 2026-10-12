"use client";

import { useSyncExternalStore } from "react";

/**
 * The access token lives in localStorage because the API is on another origin. It is short-lived (2h) and
 * cleared on any 401. Other tabs see sign-in and sign-out through the storage event.
 */
const TOKEN_KEY = "paynudge.token";

const listeners = new Set<() => void>();

export function getToken(): string | null {
  try {
    return window.localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string | null) {
  try {
    if (token) {
      window.localStorage.setItem(TOKEN_KEY, token);
    } else {
      window.localStorage.removeItem(TOKEN_KEY);
    }
  } catch {
    // Storage blocked (private mode): the session lasts until reload.
  }
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  const onStorage = (event: StorageEvent) => {
    if (event.key === TOKEN_KEY) listener();
  };
  window.addEventListener("storage", onStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", onStorage);
  };
}

/** The current token, `null` when signed out, or `undefined` before hydration (on the server we cannot know). */
export function useToken(): string | null | undefined {
  return useSyncExternalStore(subscribe, getToken, () => undefined);
}
