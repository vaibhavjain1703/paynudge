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
