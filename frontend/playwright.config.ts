import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests against the real API and a production build of the web app, on their own ports (API 8180,
 * web 3100) so they never touch a running `npm run dev` or `mvn spring-boot:run`.
 * Needs the local database and Mailpit: `docker compose up -d` from the repo root.
 */
export const E2E_API_URL = "http://localhost:8180";
const E2E_WEB_URL = "http://localhost:3100";

const inheritedEnv = Object.fromEntries(
  Object.entries(process.env).filter((entry): entry is [string, string] => entry[1] !== undefined),
);

export default defineConfig({
  testDir: "./e2e",
  globalSetup: "./e2e/global-setup.ts",
  // Eight browsers plus the API and web server share one machine; leave room for that.
  expect: { timeout: 10_000 },
  fullyParallel: true,
  // Two browsers keep an 8 GB laptop (and its Docker VM) responsive next to the API and web server; four were no
  // faster and once pushed Docker Desktop over.
  workers: 2,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: "list",
  use: {
    baseURL: E2E_WEB_URL,
    trace: "retain-on-failure",
  },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"] } },
    { name: "mobile", use: { ...devices["Pixel 7"] } },
  ],
  webServer: [
    {
      command: "mvn -q spring-boot:run",
      cwd: "../backend",
      url: `${E2E_API_URL}/actuator/health`,
      // Codes always go to Mailpit here, even when secrets.local.env sends real mail through Brevo.
      env: {
        ...inheritedEnv,
        SERVER_PORT: "8180",
        CORS_ALLOWED_ORIGINS: E2E_WEB_URL,
        MAIL_PROVIDER: "smtp",
        // Links in reminders (pay, unsubscribe) point at the e2e web server.
        APP_URL: E2E_WEB_URL,
      },
      reuseExistingServer: !process.env.CI,
      timeout: 180_000,
    },
    {
      command: "npm run build && npm run start -- --port 3100",
      url: E2E_WEB_URL,
      env: { ...inheritedEnv, NEXT_DIST_DIR: ".next-e2e", NEXT_PUBLIC_API_URL: E2E_API_URL },
      reuseExistingServer: !process.env.CI,
      timeout: 300_000,
    },
  ],
});
