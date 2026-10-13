import { expect, test } from "@playwright/test";

import { E2E_API_URL } from "../playwright.config";
import { latestMessage } from "./mailpit";
import { signInAsNewOwner, uniqueEmail } from "./session";

const KOLKATA = "Asia/Kolkata";

function kolkataHour(at: Date) {
  return Number(new Intl.DateTimeFormat("en-GB", { hour: "2-digit", hourCycle: "h23", timeZone: KOLKATA }).format(at));
}

/** dd/mm/yyyy in Kolkata, the way an Indian spreadsheet writes it. */
function kolkataDate(at: Date) {
  return new Intl.DateTimeFormat("en-GB", { day: "2-digit", month: "2-digit", year: "numeric", timeZone: KOLKATA }).format(
    at,
  );
}

test("an imported due schedules and its first reminder reaches the customer", async ({ page, request }) => {
  const { token } = await signInAsNewOwner(page, request, "Sunrise PG");

  // Quiet hours are moved away from the current time, so the reminder can go out whenever this test runs.
  const hour = kolkataHour(new Date());
  const at = (h: number) => `${String(h % 24).padStart(2, "0")}:00`;
  const settings = await request.put(`${E2E_API_URL}/api/business`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { name: "Sunrise PG", timezone: KOLKATA, quietStart: at(hour + 2), quietEnd: at(hour + 3), dailySendCap: 500 },
  });
  expect(settings.status()).toBe(200);

  // Rent that was due yesterday: the latest missed reminder goes out now instead of never.
  const tenant = uniqueEmail("tenant");
  const yesterday = kolkataDate(new Date(Date.now() - 24 * 60 * 60 * 1000));
  await page.goto("/import");
  await page.getByLabel("Choose a CSV file").setInputFiles({
    name: "rent.csv",
    mimeType: "text/csv",
    buffer: Buffer.from(`Name,Email,Amount,Due Date\nRohan Gupta,${tenant},"8,500",${yesterday}\n`),
  });
  await page.getByRole("button", { name: "Import 1 due" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Imported 1 due" })).toContainText(
    "First reminder goes out",
  );

  const reminder = await latestMessage(tenant, { timeoutMs: 30_000 });
  expect(reminder.Subject).toMatch(/^Payment of ₹8,500 due on \d{1,2} \w{3,4} \d{4}$/);
  expect(reminder.Text).toContain("Hi Rohan Gupta,");
  expect(reminder.Text).toMatch(/http:\/\/localhost:3100\/pay\/[\w-]{43}/);
  expect(reminder.Text).toMatch(/Stop these reminders: http:\/\/localhost:3100\/unsubscribe\/[\w-]{43}\s*$/);

  await page.goto("/dues");
  const row = page.getByRole("row", { name: /Rohan Gupta/ });
  await expect(row).toContainText("₹8,500");
  await expect(row).toContainText("Reminding");
});
