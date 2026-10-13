import { expect, type Page, test } from "@playwright/test";

import { E2E_API_URL } from "../playwright.config";
import { latestCode } from "./mailpit";

function uniqueEmail() {
  return `code-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;
}

async function requestCode(page: Page, email: string) {
  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  await page.getByRole("button", { name: "Continue with email" }).click();
  // Sending goes through Docker to Mailpit, which can take several seconds when the whole suite runs at once.
  await expect(page.getByText("We emailed a 6-digit code to")).toBeVisible({ timeout: 20_000 });
}

async function enterCode(page: Page, code: string) {
  await page.getByLabel("Sign-in code").fill(code);
}

test("a new owner signs up with an emailed code and names their business", async ({ page }) => {
  const email = uniqueEmail();

  await requestCode(page, email);
  await enterCode(page, await latestCode(email));

  await expect(page.getByText("is new here. What is your business called?")).toBeVisible();
  await page.getByLabel("Business name").fill("Sunrise PG");
  await page.getByRole("button", { name: "Create account" }).click();

  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("heading", { name: "Sunrise PG" })).toBeVisible();
  await page.screenshot({ path: test.info().outputPath("code-dashboard.png"), fullPage: true });
});

test("an existing owner goes straight to the dashboard with a code", async ({ page, request }) => {
  const email = uniqueEmail();
  const created = await request.post(`${E2E_API_URL}/api/auth/signup`, {
    data: { email, password: "correct-horse-battery", businessName: "Iron Gym" },
  });
  expect(created.status()).toBe(201);

  await requestCode(page, email);
  await page.screenshot({ path: test.info().outputPath("code-step.png"), fullPage: true });
  await enterCode(page, await latestCode(email));

  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("heading", { name: "Iron Gym" })).toBeVisible();
});

test("a wrong code is rejected and the owner can try again", async ({ page }) => {
  const email = uniqueEmail();
  await requestCode(page, email);
  const code = await latestCode(email);
  const wrong = code === "000000" ? "111111" : "000000";

  await enterCode(page, wrong);
  await expect(page.getByText("That code is wrong or has expired")).toBeVisible();
  await page.screenshot({ path: test.info().outputPath("code-wrong.png"), fullPage: true });

  await enterCode(page, code);
  await expect(page.getByText("is new here. What is your business called?")).toBeVisible();
});

test("resend is held back for a minute after a code is sent", async ({ page }) => {
  await requestCode(page, uniqueEmail());

  await expect(page.getByRole("button", { name: /Resend in \d+s/ })).toBeDisabled();
});
