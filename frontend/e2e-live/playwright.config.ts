import { defineConfig, devices } from "@playwright/test";

/** Checks against the live site (no local servers). LIVE_URL overrides the address. */
export default defineConfig({
  testDir: ".",
  timeout: 180_000,
  expect: { timeout: 15_000 },
  reporter: "list",
  use: { baseURL: process.env.LIVE_URL ?? "https://getpaynudge.vercel.app", trace: "retain-on-failure" },
  projects: [{ name: "desktop", use: { ...devices["Desktop Chrome"] } }],
});
