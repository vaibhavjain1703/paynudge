import { expect, type Page, test } from "@playwright/test";

import { signInAsNewOwner } from "./session";

async function addCustomer(page: Page, fields: { name: string; email?: string; phone?: string }) {
  await page.getByRole("button", { name: "Add customer" }).first().click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("Name").fill(fields.name);
  if (fields.email) await dialog.getByLabel("Email").fill(fields.email);
  if (fields.phone) await dialog.getByLabel("Phone (optional)").fill(fields.phone);
  await dialog.getByRole("button", { name: "Add customer" }).click();
}

test("an owner adds, edits, finds and deletes customers", async ({ page, request }) => {
  await signInAsNewOwner(page, request);
  await page.goto("/dashboard");
  await page.getByRole("link", { name: "Customers" }).click();
  await expect(page.getByText("No customers yet")).toBeVisible();

  await addCustomer(page, { name: "  Aarav   Shah ", email: "Aarav@Example.com", phone: "98765 43210" });
  await expect(page.getByRole("dialog")).toBeHidden();
  const aarav = page.getByRole("row", { name: /Aarav Shah/ });
  await expect(aarav).toContainText("aarav@example.com");

  await addCustomer(page, { name: "Diya Rao", phone: "9876500000" });
  await expect(page.getByRole("row", { name: /Diya Rao/ })).toBeVisible();

  // One email is one customer.
  await addCustomer(page, { name: "Someone Else", email: "AARAV@example.com" });
  await expect(page.getByRole("dialog").getByText("Another customer already uses this email")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toBeHidden();

  await page.getByRole("button", { name: "Edit Aarav Shah" }).click();
  await page.getByRole("dialog").getByLabel("Name").fill("Aarav S.");
  await page.getByRole("dialog").getByRole("button", { name: "Save changes" }).click();
  await expect(page.getByRole("row", { name: /Aarav S\./ })).toBeVisible();
  await page.screenshot({ path: test.info().outputPath("customers.png"), fullPage: true });

  await page.getByLabel("Search customers").fill("diya");
  await expect(page.getByRole("row", { name: /Aarav S\./ })).toBeHidden();
  await expect(page.getByRole("row", { name: /Diya Rao/ })).toBeVisible();
  await page.getByLabel("Search customers").fill("nobody-like-this");
  await expect(page.getByText("No matches")).toBeVisible();
  await page.getByLabel("Search customers").fill("");

  await page.getByRole("button", { name: "Delete Diya Rao" }).click();
  await expect(page.getByRole("alertdialog")).toContainText("This also deletes their dues");
  await page.getByRole("button", { name: "Delete customer" }).click();
  await expect(page.getByRole("row", { name: /Diya Rao/ })).toBeHidden();
  await expect(page.getByRole("row", { name: /Aarav S\./ })).toBeVisible();
});

test("the customer form says what is missing", async ({ page, request }) => {
  await signInAsNewOwner(page, request);
  await page.goto("/customers");

  await page.getByRole("button", { name: "Add customer" }).first().click();
  await page.getByRole("dialog").getByRole("button", { name: "Add customer" }).click();

  await expect(page.getByText("Enter a name")).toBeVisible();
  await expect(page.getByText("Add an email or a phone number")).toBeVisible();
});
