import { expect, test } from "@playwright/test";

import { E2E_API_URL } from "../playwright.config";
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

test("a due's timeline shows the reminder sent and the ones still scheduled", async ({ page, request }) => {
  const { token } = await signInAsNewOwner(page, request, "Sunrise PG");
  // Quiet hours away from now, so the catch-up reminder can go out whenever this runs.
  const hour = kolkataHour(new Date());
  const at = (h: number) => `${String(h % 24).padStart(2, "0")}:00`;
  const settings = await request.put(`${E2E_API_URL}/api/business`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { name: "Sunrise PG", timezone: KOLKATA, quietStart: at(hour + 2), quietEnd: at(hour + 3), dailySendCap: 500 },
  });
  expect(settings.status()).toBe(200);

  // Rent due yesterday: the reminder for the due date goes out now; the one before it was missed.
  const tenant = uniqueEmail("tenant");
  const yesterday = kolkataDate(new Date(Date.now() - 24 * 60 * 60 * 1000));
  await page.goto("/import");
  await page.getByLabel("Choose a CSV file").setInputFiles({
    name: "rent.csv",
    mimeType: "text/csv",
    buffer: Buffer.from(`Name,Email,Amount,Due Date\nRohan Gupta,${tenant},"8,500",${yesterday}\n`),
  });
  await page.getByRole("button", { name: "Import 1 due" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Imported 1 due" })).toBeVisible();
  const reminder = await latestMessage(tenant, { timeoutMs: 30_000 });

  await page.getByRole("link", { name: "View dues" }).click();
  await page.getByRole("link", { name: "Rohan Gupta" }).click();
  await expect(page).toHaveURL(/\/dues\/\d+$/);
  await expect(page.getByRole("heading", { name: "Rohan Gupta" })).toBeVisible();

  const timeline = page.getByRole("list", { name: "Reminder timeline" });
  const step = (key: string) => timeline.locator(`[data-step="${key}"]`);
  await expect(step("remind_1")).toHaveAttribute("data-status", "SKIPPED");
  await expect(step("remind_2")).toHaveAttribute("data-status", "DONE");
  await expect(step("remind_2")).toContainText("Reminder · on the due date");
  await expect(step("remind_2")).toContainText(`“${reminder.Subject}”`);
  await expect(step("remind_2")).toContainText("Sent");
  await expect(step("remind_3")).toHaveAttribute("data-status", "PENDING");
  await expect(step("remind_3")).toContainText("Firm reminder · 3 days after");
  await expect(step("remind_3")).toContainText("Scheduled for");
  await expect(step("final")).toContainText("Final notice · 10 days after");
  await expect(timeline.getByText("Now", { exact: true })).toBeVisible();
  await page.screenshot({ path: test.info().outputPath("timeline.png"), fullPage: true });

  // Paid in cash: the rest of the timeline cancels itself.
  await page.getByRole("button", { name: "Mark as paid" }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: "Mark as paid" }).click();
  await expect(timeline.getByText("Marked paid")).toBeVisible();
  await expect(step("remind_3")).toHaveAttribute("data-status", "CANCELLED");
  await expect(step("final")).toHaveAttribute("data-status", "CANCELLED");
  await expect(step("remind_2")).toHaveAttribute("data-status", "DONE");
  await expect(page.getByRole("button", { name: "Mark as paid" })).toBeHidden();
  await page.screenshot({ path: test.info().outputPath("timeline-paid.png"), fullPage: true });

  await page.getByRole("navigation", { name: "Main" }).getByRole("link", { name: "Dashboard", exact: true }).click();
  await expect(page.getByRole("link", { name: /Collected this month/ })).toContainText("₹8,500");
  await expect(page.getByRole("link", { name: /Outstanding/ })).toContainText("₹0");
  await expect(page.getByText("Nothing scheduled.")).toBeVisible();
  await page.screenshot({ path: test.info().outputPath("dashboard.png"), fullPage: true });
});

test("an unknown due says so", async ({ page, request }) => {
  await signInAsNewOwner(page, request);
  await page.goto("/dues/999999999");
  await expect(page.getByText("This due isn't here")).toBeVisible();
});
