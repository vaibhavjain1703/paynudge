import { expect, type Page, test } from "@playwright/test";

import { E2E_API_URL as API_URL } from "../playwright.config";

const PASSWORD = "correct-horse-battery";

function uniqueEmail() {
  return `owner-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;
}

/** Switches to the password form and waits for it: the passwordless panel fades out first. */
async function switchToPassword(page: Page) {
  await page.getByRole("button", { name: "Use a password instead" }).click();
  await expect(page.getByLabel("Password")).toBeVisible();
}

async function openPasswordForm(page: Page, path: "/login" | "/signup") {
  await page.goto(path);
  await switchToPassword(page);
}

async function fillSignup(page: Page, email: string) {
  await openPasswordForm(page, "/signup");
  await page.getByLabel("Business name").fill("Bright Tuition");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Create account" }).click();
}

test("an owner signs up, lands on the dashboard, logs out and logs back in", async ({ page }) => {
  const email = uniqueEmail();

  await fillSignup(page, email);

  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("heading", { name: "Bright Tuition" })).toBeVisible();
  await expect(page.getByText("21:00 to 09:00")).toBeVisible();
  await expect(page.getByText("No dues yet")).toBeVisible();
  await page.screenshot({ path: test.info().outputPath("dashboard.png"), fullPage: true });

  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page).toHaveURL(/\/login$/);

  await page.goto("/dashboard");
  await expect(page).toHaveURL(/\/login$/);

  await switchToPassword(page);
  await page.getByLabel("Email").fill(email.toUpperCase());
  await page.getByLabel("Password").fill(PASSWORD);
  await page.getByRole("button", { name: "Log in" }).click();
  await expect(page.getByRole("heading", { name: "Bright Tuition" })).toBeVisible();
});

test("wrong credentials show one message that does not reveal which part was wrong", async ({ page }) => {
  await openPasswordForm(page, "/login");
  await page.getByLabel("Email").fill(uniqueEmail());
  await page.getByLabel("Password").fill("not-the-password");
  await page.getByRole("button", { name: "Log in" }).click();

  await expect(page.getByRole("alert").filter({ hasText: "Email or password is incorrect" })).toBeVisible();
  await page.screenshot({ path: test.info().outputPath("login-error.png"), fullPage: true });
});

test("the signup form catches bad input before calling the server", async ({ page }) => {
  await openPasswordForm(page, "/signup");
  await page.getByLabel("Email").fill("not-an-email");
  await page.getByLabel("Password").fill("short");
  await page.getByRole("button", { name: "Create account" }).click();

  await expect(page.getByText("Enter your business name")).toBeVisible();
  await expect(page.getByText("Enter a valid email address")).toBeVisible();
  await expect(page.getByText("Use at least 8 characters")).toBeVisible();
  await expect(page).toHaveURL(/\/signup$/);
});

test("a taken email is reported on the email field", async ({ page, request }) => {
  const email = uniqueEmail();
  const created = await request.post(`${API_URL}/api/auth/signup`, {
    data: { email, password: PASSWORD, businessName: "First Owner" },
  });
  expect(created.status()).toBe(201);

  await fillSignup(page, email);

  await expect(page.getByText("An account with this email already exists")).toBeVisible();
  await expect(page.getByLabel("Email")).toHaveAttribute("aria-invalid", "true");
});

test("a rejected session sends the owner back to log in", async ({ page }) => {
  await page.goto("/login");
  await page.evaluate(() => window.localStorage.setItem("paynudge.token", "expired.or.forged"));

  await page.goto("/dashboard");

  await expect(page).toHaveURL(/\/login$/);
  expect(await page.evaluate(() => window.localStorage.getItem("paynudge.token"))).toBeNull();
});

test("the theme can be switched to light and the choice survives a reload", async ({ page }) => {
  await page.goto("/login");
  await expect(page.locator("html")).toHaveClass(/dark/);

  await page.getByRole("button", { name: "Toggle light and dark theme" }).click();
  await expect(page.locator("html")).not.toHaveClass(/dark/);

  await page.reload();
  await expect(page.locator("html")).not.toHaveClass(/dark/);
  await page.screenshot({ path: test.info().outputPath("login-light.png"), fullPage: true });
});
