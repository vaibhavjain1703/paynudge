import { expect, test } from "@playwright/test";

// A stranger's first visit to the live site. Run: npx playwright test -c e2e-live
test("a stranger signs up, adds a due and sees it", async ({ page }) => {
  const email = `smoke-${Date.now()}@example.com`;
  await page.goto("/");
  await page.goto("/signup");
  await page.getByRole("button", { name: "Use a password instead" }).click();
  await page.getByLabel("Business name").fill("Smoke Test Tuition");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill("correct-horse-battery");
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page).toHaveURL(/\/dashboard$/, { timeout: 60_000 });

  await page.goto("/import");
  await page.getByRole("tab", { name: "Add manually" }).click();
  await page.getByLabel("Name", { exact: true }).fill("Rohan Gupta");
  await page.getByLabel("Email", { exact: true }).fill(`rohan-${Date.now()}@example.com`);
  await page.getByLabel("Amount (₹)").fill("8,500");
  await page.getByLabel("Due date").fill(new Date(Date.now() - 864e5).toISOString().slice(0, 10));
  await page.getByRole("button", { name: "Add due" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Added ₹8,500 for Rohan Gupta" })).toBeVisible({
    timeout: 30_000,
  });
  await page.getByRole("link", { name: "Open this due" }).click();
  const timeline = page.getByRole("list", { name: "Reminder timeline" });
  // Due yesterday: the first reminder goes out now (into the outbox), and the page shows it live.
  await expect(timeline.locator('[data-step="remind_2"]')).toHaveAttribute("data-status", "DONE", { timeout: 60_000 });
  await page.screenshot({ path: test.info().outputPath("live-due.png"), fullPage: true });
});
