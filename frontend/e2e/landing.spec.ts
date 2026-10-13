import { expect, test } from "@playwright/test";

test("the timeline demo plays the story: reminders go out, she pays, the rest cancel", async ({ page }) => {
  await page.goto("/");
  const demo = page.getByTestId("timeline-demo");

  await expect(demo.getByText("Active", { exact: true })).toBeVisible();
  await expect(demo.getByText("Payment received")).toBeHidden();

  // The full loop takes about ten seconds; payment lands around the six-second mark.
  await expect(demo.getByText("Payment received")).toBeVisible({ timeout: 15_000 });
  await expect(demo.getByText("Paid", { exact: true })).toBeVisible();
  await expect(demo.getByText("Cancelled", { exact: true })).toHaveCount(2, { timeout: 3_000 });
  await page.screenshot({ path: test.info().outputPath("landing-paid.png"), fullPage: true });
});

test("with reduced motion the demo shows the finished story without playing it", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/");
  const demo = page.getByTestId("timeline-demo");

  await expect(demo.getByText("Payment received")).toBeVisible();
  await expect(demo.getByText("Paid", { exact: true })).toBeVisible();
  await expect(demo.getByText("Cancelled", { exact: true })).toHaveCount(2);
  await expect(demo.getByText("Sent", { exact: true })).toHaveCount(2);

  // Nothing advances: a few seconds later it is still the same frame.
  await page.waitForTimeout(2_500);
  await expect(demo.getByText("Active", { exact: true })).toHaveCount(0);
});
