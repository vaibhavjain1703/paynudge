import { expect, test } from "@playwright/test";

import { signInAsNewOwner } from "./session";

test("an owner changes their business settings and the changes stick", async ({ page, request }) => {
  await signInAsNewOwner(page, request);
  await page.goto("/dashboard");
  await page.getByRole("link", { name: "Edit" }).click();
  await expect(page).toHaveURL(/\/settings$/);

  await page.getByLabel("Business name").fill("Bright Tuition Centre");
  await page.getByLabel("Sender name (optional)").fill("Asha Ma'am");
  await page.getByLabel("Timezone").selectOption("Asia/Dubai");
  await page.getByLabel("Quiet hours start").fill("22:30");
  await page.getByLabel("Quiet hours end").fill("07:00");
  await page.getByLabel("Daily send limit").fill("120");
  await page.getByRole("button", { name: "Save changes" }).click();

  await expect(page.getByRole("status").filter({ hasText: "Saved" })).toBeVisible();
  await expect(page.getByRole("banner").getByText("Bright Tuition Centre")).toBeVisible();
  await page.screenshot({ path: test.info().outputPath("settings.png"), fullPage: true });

  await page.reload();
  await expect(page.getByLabel("Business name")).toHaveValue("Bright Tuition Centre");
  await expect(page.getByLabel("Timezone")).toHaveValue("Asia/Dubai");

  await page.getByRole("link", { name: "Dashboard" }).click();
  await expect(page.getByText("22:30 to 07:00")).toBeVisible();
});

test("settings say what is wrong before anything is saved", async ({ page, request }) => {
  await signInAsNewOwner(page, request);
  await page.goto("/settings");

  await page.getByLabel("Quiet hours start").fill("09:00");
  await page.getByLabel("Quiet hours end").fill("09:00");
  await page.getByLabel("Daily send limit").fill("900");
  await page.getByRole("button", { name: "Save changes" }).click();

  await expect(page.getByText("Quiet hours need different start and end times")).toBeVisible();
  await expect(page.getByText("The limit is 500 a day")).toBeVisible();
  await expect(page.getByRole("status").filter({ hasText: "Saved" })).toHaveCount(0);
});
