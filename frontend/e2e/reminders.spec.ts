import { expect, test } from "@playwright/test";

import { E2E_API_URL } from "../playwright.config";
import { headersOf, latestMessage } from "./mailpit";
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

test("an imported due's first reminder reaches the customer, who can stop the rest", async ({ page, request }) => {
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
  // One Message-ID per step, so a resend after a crash is recognisably the same email; and the mail client's own
  // unsubscribe button works in one click.
  expect(reminder.MessageID).toMatch(/^due-\d+\.remind_\d+@paynudge\.local$/);
  const headers = await headersOf(reminder);
  expect(headers["List-Unsubscribe"]).toEqual([
    expect.stringMatching(/^<http:\/\/localhost:8180\/api\/public\/unsubscribe\/[\w-]{43}>$/),
  ]);
  expect(headers["List-Unsubscribe-Post"]).toEqual(["List-Unsubscribe=One-Click"]);

  await page.goto("/dues");
  const row = page.getByRole("row", { name: /Rohan Gupta/ });
  await expect(row).toContainText("₹8,500");
  await expect(row).toContainText("Reminding");

  // The tenant follows the link in the email. Opening it changes nothing; the button does.
  await page.goto(reminder.Text.match(/Stop these reminders: (\S+)/)![1]);
  await expect(page.getByText("Stop payment reminders from Sunrise PG?")).toBeVisible();
  await page.screenshot({ path: test.info().outputPath("unsubscribe.png") });
  await page.getByRole("button", { name: "Stop reminders" }).click();
  await expect(page.getByRole("status")).toContainText("You're unsubscribed");
  await expect(page.getByRole("status")).toContainText("Sunrise PG won't send you any more payment reminders");
  await page.screenshot({ path: test.info().outputPath("unsubscribed.png") });

  await page.goto("/dues");
  await expect(row).toContainText("Unsubscribed");
  await expect(row).toContainText("—"); // no next reminder
});

test("a broken unsubscribe link says so", async ({ page }) => {
  await page.goto("/unsubscribe/not-a-real-link");
  await expect(page.getByText("This link doesn't work")).toBeVisible();
});
