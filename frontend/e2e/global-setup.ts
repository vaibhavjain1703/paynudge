import type { FullConfig } from "@playwright/test";

import { E2E_API_URL } from "../playwright.config";

const PASSWORD = "warm-up-password";

/**
 * A freshly started JVM runs BCrypt and Hibernate interpreted until the JIT catches up: the first sign-ups take
 * several seconds each (measured 6.6 s cold vs ~0.6 s warm). Warm both servers once so test timings reflect the
 * app rather than JIT compilation, and so no test owns the cold-start penalty.
 */
export default async function warmUp(config: FullConfig) {
  const baseURL = config.projects[0]?.use.baseURL ?? "http://localhost:3100";
  const post = (path: string, body: unknown) =>
    fetch(`${E2E_API_URL}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });

  const email = `warm-up-${Date.now()}@example.com`;
  await post("/api/auth/signup", { email, password: PASSWORD, businessName: "Warm-up" });
  for (let i = 0; i < 6; i++) {
    await post("/api/auth/login", { email, password: PASSWORD });
  }

  for (const path of ["/", "/login", "/signup", "/dashboard"]) {
    await fetch(`${baseURL}${path}`);
  }
}
