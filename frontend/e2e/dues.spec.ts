import { type APIRequestContext, expect, test } from "@playwright/test";

import { E2E_API_URL } from "../playwright.config";
import { signInAsNewOwner } from "./session";

/** A customer with a due far enough ahead that no reminder goes out during the test. */
async function dueFor(request: APIRequestContext, token: string, name: string) {
  const auth = { Authorization: `Bearer ${token}` };
  const customer = await request.post(`${E2E_API_URL}/api/customers`, {
    headers: auth,
    data: { name, email: `${name.toLowerCase()}-${Date.now()}@example.com` },
  });
  const policies = await request.get(`${E2E_API_URL}/api/policies`, { headers: auth });
  const due = await request.post(`${E2E_API_URL}/api/dues`, {
    headers: auth,
    data: {
      customerId: ((await customer.json()) as { id: number }).id,
      amountPaise: 250000,
      dueDate: "2099-06-15",
      policyId: ((await policies.json()) as { id: number }[])[0].id,
    },
  });
  expect(due.status()).toBe(201);
}

test("the owner marks one due paid and stops another's reminders", async ({ page, request }) => {
  const { token } = await signInAsNewOwner(page, request);
  await dueFor(request, token, "Aarav");
  await dueFor(request, token, "Diya");

  await page.goto("/dues");
  const aarav = page.getByRole("row", { name: /Aarav/ });
  const diya = page.getByRole("row", { name: /Diya/ });
  await expect(aarav).toContainText("Scheduled");
  await page.screenshot({ path: test.info().outputPath("dues-open.png"), fullPage: true });

  await page.getByRole("button", { name: "Mark Aarav's due as paid" }).click();
  const dialog = page.getByRole("alertdialog");
  await expect(dialog).toContainText("Aarav · ₹2,500");
  await dialog.getByRole("button", { name: "Mark as paid" }).click();
  await expect(dialog).toBeHidden();
  await expect(aarav).toContainText("Paid");
  await expect(aarav).toContainText("—");
  await expect(page.getByRole("button", { name: "Mark Aarav's due as paid" })).toBeHidden();

  // Changing one's mind keeps the reminders going.
  await page.getByRole("button", { name: "Stop reminders for Diya" }).click();
  await dialog.getByRole("button", { name: "Keep reminding" }).click();
  await expect(diya).toContainText("Scheduled");

  await page.getByRole("button", { name: "Stop reminders for Diya" }).click();
  await dialog.getByRole("button", { name: "Stop reminders" }).click();
  await expect(diya).toContainText("Cancelled");
  await page.screenshot({ path: test.info().outputPath("dues-actions.png"), fullPage: true });
});

test("the owner filters, searches and stops many dues at once", async ({ page, request }) => {
  const { token } = await signInAsNewOwner(page, request);
  for (const name of ["Aarav", "Diya", "Kabir"]) {
    await dueFor(request, token, name);
  }

  await page.goto("/dues");
  const chips = page.getByRole("group", { name: "Show" });
  await expect(chips.getByRole("button", { name: /^All/ })).toContainText("3");
  await chips.getByRole("button", { name: /^Paid/ }).click();
  await expect(page.getByText("No paid dues.")).toBeVisible();
  await chips.getByRole("button", { name: /^All/ }).click();

  await page.getByLabel("Search dues").fill("diy");
  await expect(page.getByRole("row")).toHaveCount(2); // header + Diya
  await expect(page.getByRole("row", { name: /Diya/ })).toBeVisible();
  await page.getByLabel("Search dues").fill("");
  await expect(page.getByRole("row")).toHaveCount(4);

  await page.getByLabel("Select Aarav's due").check();
  await page.getByLabel("Select Kabir's due").check();
  const selection = page.getByRole("region", { name: "Selected dues" });
  await expect(selection).toContainText("2 selected");
  await selection.getByRole("button", { name: "Mark paid" }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Mark as paid" }).click();

  await expect(selection).toBeHidden();
  await expect(page.getByRole("row", { name: /Aarav/ })).toContainText("Paid");
  await expect(page.getByRole("row", { name: /Kabir/ })).toContainText("Paid");
  await expect(page.getByRole("row", { name: /Diya/ })).toContainText("Scheduled");
  await expect(chips.getByRole("button", { name: /^Paid/ })).toContainText("2");
  await page.screenshot({ path: test.info().outputPath("dues-bulk.png"), fullPage: true });
});
