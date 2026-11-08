import { expect, test } from "@playwright/test";

test("a stranger opens the live demo, reads a reminder and follows its pay link", async ({ page, context }) => {
  await page.goto("/");
  await page.getByRole("button", { name: "Try the live demo" }).click();

  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole("note")).toContainText("You're exploring a demo business");
  await expect(page.getByRole("banner")).toContainText("Sunrise Tuition Centre");
  await page.screenshot({ path: test.info().outputPath("demo-dashboard.png"), fullPage: true });

  // Two months of history: paid fees, overdue ones, one that needs the owner.
  await page.getByRole("navigation", { name: "Main" }).getByRole("link", { name: "Dues", exact: true }).click();
  await expect(page.getByRole("button", { name: /^Needs you/ })).toContainText("1");
  await page.getByRole("button", { name: /^Needs you/ }).click();
  await expect(page.getByRole("link", { name: "Kabir Mehta" })).toHaveCount(1);
  await page.getByRole("link", { name: "Kabir Mehta" }).click();

  const timeline = page.getByRole("list", { name: "Reminder timeline" });
  await expect(timeline.locator('[data-step="notify_owner"]')).toHaveAttribute("data-status", "DONE");
  await timeline.locator('[data-step="remind_3"]').getByRole("button", { name: "View email" }).click();
  const email = page.getByRole("dialog");
  await expect(email).toContainText("Your payment of ₹4,500 is overdue");
  await expect(email).toContainText("Hi Kabir Mehta,");
  await expect(email).toContainText("To kabir.mehta@example.com");
  await page.screenshot({ path: test.info().outputPath("demo-email.png") });

  // The pay link in the email is the customer's real pay page.
  const [payPage] = await Promise.all([
    context.waitForEvent("page"),
    email.getByRole("link", { name: /\/pay\// }).click(),
  ]);
  await expect(payPage.getByRole("heading", { name: "Sunrise Tuition Centre" })).toBeVisible();
  await expect(payPage.getByText("Hi Kabir,")).toBeVisible();
  await payPage.close();

  // Leaving the demo leads to an account of one's own.
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "Create your own account" }).click();
  await expect(page).toHaveURL(/\/signup$/);
});

test("the privacy and terms pages are linked from the landing page", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("navigation", { name: "Footer" }).getByRole("link", { name: "Privacy" }).click();
  await expect(page.getByRole("heading", { name: "Privacy", level: 1 })).toBeVisible();
  await expect(page.getByText("PayNudge does not sell data")).toBeVisible();
  await page.goto("/terms");
  await expect(page.getByRole("heading", { name: "Terms", level: 1 })).toBeVisible();
});
