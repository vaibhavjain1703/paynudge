import { createHmac } from "node:crypto";

import { expect, test } from "@playwright/test";

import { E2E_API_URL, E2E_WEBHOOK_SECRET } from "../playwright.config";
import { latestMessage } from "./mailpit";
import { signInAsNewOwner, uniqueEmail } from "./session";

const KOLKATA = "Asia/Kolkata";

function kolkataHour(at: Date) {
  return Number(new Intl.DateTimeFormat("en-GB", { hour: "2-digit", hourCycle: "h23", timeZone: KOLKATA }).format(at));
}

function kolkataDate(at: Date) {
  return new Intl.DateTimeFormat("en-GB", { day: "2-digit", month: "2-digit", year: "numeric", timeZone: KOLKATA }).format(
    at,
  );
}

/** Razorpay's payment_link.paid event for the link carrying this pay token, as Razorpay sends it. */
function paymentLinkPaid(payToken: string, paymentId: string, amountPaise: number) {
  return JSON.stringify({
    entity: "event",
    event: "payment_link.paid",
    payload: {
      payment_link: { entity: { id: `plink_${paymentId}`, amount: amountPaise, notes: { pay_token: payToken } } },
      payment: {
        entity: {
          id: paymentId,
          amount: amountPaise,
          currency: "INR",
          status: "captured",
          method: "upi",
          created_at: Math.floor(Date.now() / 1000),
        },
      },
    },
  });
}

test("a test payment flips the due to PAID live, and the customer sees it paid", async ({
  page,
  browser,
  request,
}) => {
  const { token } = await signInAsNewOwner(page, request, "Sunrise PG");
  const hour = kolkataHour(new Date());
  const at = (h: number) => `${String(h % 24).padStart(2, "0")}:00`;
  await request.put(`${E2E_API_URL}/api/business`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { name: "Sunrise PG", timezone: KOLKATA, quietStart: at(hour + 2), quietEnd: at(hour + 3), dailySendCap: 500 },
  });

  // Rent due yesterday: the reminder goes out now, with the pay link in it.
  const tenant = uniqueEmail("tenant");
  await page.goto("/import");
  await page.getByLabel("Choose a CSV file").setInputFiles({
    name: "rent.csv",
    mimeType: "text/csv",
    buffer: Buffer.from(
      `Name,Email,Amount,Due Date\nRohan Gupta,${tenant},"8,500",${kolkataDate(new Date(Date.now() - 864e5))}\n`,
    ),
  });
  await page.getByRole("button", { name: "Import 1 due" }).click();
  const reminder = await latestMessage(tenant, { timeoutMs: 30_000 });
  const payLink = reminder.Text.match(/http:\/\/localhost:3100\/pay\/([\w-]{43})/)!;

  // The owner watches the due's page.
  await page.getByRole("link", { name: "View dues" }).click();
  await page.getByRole("link", { name: "Rohan Gupta" }).click();
  const timeline = page.getByRole("list", { name: "Reminder timeline" });
  await expect(timeline.locator('[data-step="remind_2"]')).toHaveAttribute("data-status", "DONE");

  // The customer opens the link from the email, on their own device.
  const customer = await (await browser.newContext()).newPage();
  await customer.goto(payLink[0]);
  await expect(customer.getByRole("heading", { name: "Sunrise PG" })).toBeVisible();
  await expect(customer.getByText("Hi Rohan,")).toBeVisible();
  await expect(customer.getByRole("button", { name: "Pay ₹8,500" })).toBeEnabled();
  await customer.screenshot({ path: test.info().outputPath("pay-page.png") });

  // They pay; Razorpay tells PayNudge (a signed webhook, as Razorpay would send it).
  const event = paymentLinkPaid(payLink[1], `pay_e2e${Date.now()}`, 850000);
  const delivered = await request.post(`${E2E_API_URL}/webhooks/razorpay`, {
    headers: {
      "Content-Type": "application/json",
      "X-Razorpay-Signature": createHmac("sha256", E2E_WEBHOOK_SECRET).update(event).digest("hex"),
    },
    data: event,
  });
  expect(await delivered.json()).toEqual({ status: "recorded" });

  // The owner's open page turns paid by itself: no reload, no polling.
  await expect(timeline.getByText("Payment received")).toBeVisible({ timeout: 5_000 });
  await expect(timeline.getByText(/₹8,500 via UPI/)).toBeVisible();
  await expect(timeline.locator('[data-step="remind_3"]')).toHaveAttribute("data-status", "CANCELLED");
  await expect(page.getByRole("button", { name: "Mark as paid" })).toBeHidden();
  await page.screenshot({ path: test.info().outputPath("paid-live.png"), fullPage: true });

  await customer.reload();
  await expect(customer.getByRole("status")).toContainText("Paid. Thank you!");
  await expect(customer.getByRole("status")).toContainText("₹8,500 to Sunrise PG");
  await customer.screenshot({ path: test.info().outputPath("pay-page-paid.png") });
});

test("a broken pay link says so", async ({ page }) => {
  await page.goto("/pay/not-a-real-link");
  await expect(page.getByText("This link doesn't work")).toBeVisible();
});
