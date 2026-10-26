import path from "node:path";

import { expect, test } from "@playwright/test";

import { signInAsNewOwner, uniqueEmail } from "./session";

const MESSY_CSV = path.resolve("e2e/fixtures/messy-dues.csv");

test("a messy spreadsheet imports with clear row errors", async ({ page, request }) => {
  await signInAsNewOwner(page, request);
  await page.goto("/dashboard");
  await page.getByRole("link", { name: "Import dues" }).click();
  await expect(page).toHaveURL(/\/import$/);

  await page.getByLabel("Choose a CSV file").setInputFiles(MESSY_CSV);

  // Columns are matched from the headers.
  await expect(page.getByLabel(/^Amount/)).toHaveValue("Amount (₹)");
  await expect(page.getByLabel(/^Due date/)).toHaveValue("Due Date");

  // Every bad row says what is wrong, in the cell that is wrong.
  await expect(page.getByRole("button", { name: "Import 4 dues" })).toBeEnabled();
  await expect(page.getByText("7 with problems will be skipped")).toBeVisible();
  await expect(page.getByRole("row").filter({ hasText: "Sana Khan" })).toContainText(
    "Use a number like 1500 or 1,500.50",
  );
  await expect(page.getByRole("row").filter({ hasText: "Ananya Bose" })).toContainText(
    "Use a date like 05/11/2026 (day/month/year)",
  );
  await expect(page.getByRole("row").filter({ hasText: "Aarav Shah (sibling)" })).toContainText(
    "Same customer as row 2",
  );
  await expect(page.getByRole("row").filter({ hasText: "Diya Rao" })).toContainText("₹1,500.50");

  // The reminder policy every imported due will follow.
  await expect(page.getByLabel("Reminder policy")).toHaveValue(/\d+/);
  await expect(page.getByText("4 emails, from 2 days before to 10 days after")).toBeVisible();
  await page.screenshot({ path: test.info().outputPath("import-preview.png"), fullPage: true });

  await page.getByRole("button", { name: /Only problems/ }).click();
  await expect(page.getByRole("row").filter({ hasText: "Diya Rao" })).toBeHidden();
  await expect(page.getByRole("row").filter({ hasText: "Sana Khan" })).toBeVisible();

  await page.getByRole("button", { name: "Import 4 dues" }).click();
  const result = page.getByRole("status").filter({ hasText: "Imported 4 dues" });
  await expect(result).toContainText("3 new customers · 0 already in your list · 0 imported before · 7 with problems");
  // The file's dates are fixed, so when the first reminder goes out depends on today; that it is scheduled does not.
  await expect(result).toContainText("First reminder goes out");

  await page.getByRole("link", { name: "View dues" }).click();
  await expect(page).toHaveURL(/\/dues$/);
  await expect(page.getByRole("row")).toHaveCount(5); // header + 4 dues
  for (const name of ["Aarav Shah", "Diya Rao", "Kabir Mehta"]) {
    await expect(page.getByRole("row", { name: new RegExp(name) }).first()).toBeVisible();
  }
  await page.screenshot({ path: test.info().outputPath("dues.png"), fullPage: true });

  // The same file again adds nothing: every valid row was imported before.
  await page.goto("/import");
  await page.getByLabel("Choose a CSV file").setInputFiles(MESSY_CSV);
  await expect(page.getByText("Every valid row was imported before.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Import 0 dues" })).toBeDisabled();
});

test("columns the guess missed can be chosen by hand", async ({ page, request }) => {
  await signInAsNewOwner(page, request);
  await page.goto("/import");

  await page.getByLabel("Choose a CSV file").setInputFiles({
    name: "odd-headers.csv",
    mimeType: "text/csv",
    buffer: Buffer.from("Who,How to reach,How much,When\nAarav,aarav@example.com,1500,05/11/2026\n"),
  });
  await expect(page.getByText("Choose a column for: name, email or phone, amount, due date.")).toBeVisible();
  await expect(page.getByRole("button", { name: /^Import/ })).toBeDisabled();

  await page.getByLabel(/^Name/).selectOption("Who");
  await page.getByLabel(/^Email/).selectOption("How to reach");
  await page.getByLabel(/^Amount/).selectOption("How much");
  await page.getByLabel(/^Due date/).selectOption("When");

  await page.getByRole("button", { name: "Import 1 due" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Imported 1 due" })).toBeVisible();
});

test("an Excel workbook gets told how to save it as CSV", async ({ page, request }) => {
  await signInAsNewOwner(page, request);
  await page.goto("/import");

  await page.getByLabel("Choose a CSV file").setInputFiles({
    name: "dues.xlsx",
    mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    buffer: Buffer.from([0x50, 0x4b, 0x03, 0x04, 0x14, 0x00]),
  });

  await expect(page.getByRole("alert").filter({ hasText: "This looks like an Excel workbook" })).toBeVisible();
  await expect(page.getByLabel("Choose a CSV file")).toBeAttached();
});

test("a file being checked waits while the owner looks at other pages", async ({ page, request }) => {
  await signInAsNewOwner(page, request);
  const menu = page.getByRole("navigation", { name: "Main" });
  await page.goto("/import");
  await page.getByLabel("Choose a CSV file").setInputFiles(MESSY_CSV);
  await expect(page.getByRole("button", { name: "Import 4 dues" })).toBeEnabled();
  await page.getByLabel("Reminder policy").selectOption({ label: "Gentle" });

  // Off to read the policies and back through the menu: the file, its row checks and the chosen policy are still there.
  await page.getByRole("link", { name: "See the policies" }).click();
  await expect(page).toHaveURL(/\/policies$/);
  await menu.getByRole("link", { name: "Import", exact: true }).click();
  await expect(page.getByText("messy-dues.csv")).toBeVisible();
  await expect(page.getByRole("row").filter({ hasText: "Sana Khan" })).toContainText(
    "Use a number like 1500 or 1,500.50",
  );
  await expect(page.getByLabel("Reminder policy").locator("option:checked")).toHaveText("Gentle");

  await page.getByRole("button", { name: "Import 4 dues" }).click();
  await expect(page.getByRole("status").filter({ hasText: "Imported 4 dues" })).toBeVisible();

  // An imported file is let go: the next visit starts with an empty picker.
  await menu.getByRole("link", { name: "Dues", exact: true }).click();
  await expect(page).toHaveURL(/\/dues$/);
  await menu.getByRole("link", { name: "Import", exact: true }).click();
  await expect(page.getByText("Drop a CSV file here, or click to choose one")).toBeVisible();
});

test("logging out forgets a file that was being checked", async ({ page, request }) => {
  const { email } = await signInAsNewOwner(page, request);
  await page.goto("/import");
  await page.getByLabel("Choose a CSV file").setInputFiles(MESSY_CSV);
  await expect(page.getByText("messy-dues.csv")).toBeVisible();

  await page.getByRole("button", { name: "Log out" }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.getByRole("button", { name: "Use a password instead" }).click();
  await expect(page.getByLabel("Password")).toBeVisible();
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill("correct-horse-battery");
  await page.getByRole("button", { name: "Log in" }).click();
  await expect(page).toHaveURL(/\/dashboard$/);

  await page.getByRole("navigation", { name: "Main" }).getByRole("link", { name: "Import", exact: true }).click();
  await expect(page.getByText("Drop a CSV file here, or click to choose one")).toBeVisible();
  await expect(page.getByText("messy-dues.csv")).toBeHidden();
});

/** yyyy-mm-dd, a month ahead: far enough that no reminder goes out during the test. */
function aMonthAhead() {
  return new Date(Date.now() + 30 * 864e5).toISOString().slice(0, 10);
}

test("a due added by hand shows up in dues, and the same due is never added twice", async ({ page, request }) => {
  await signInAsNewOwner(page, request);
  const menu = page.getByRole("navigation", { name: "Main" });
  const email = uniqueEmail("rohan");
  const dueDate = aMonthAhead();
  await page.goto("/import");
  await page.getByRole("tab", { name: "Add manually" }).click();

  await page.getByLabel("Name", { exact: true }).fill("Rohan Gupta");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Phone (optional)").fill("98765 43210");
  await page.getByLabel("Amount (₹)").fill("8,500");
  await page.getByLabel("Due date").fill(dueDate);
  await expect(page.getByText("4 emails, from 2 days before to 10 days after")).toBeVisible();
  await page.screenshot({ path: test.info().outputPath("manual-entry.png"), fullPage: true });
  await page.getByRole("button", { name: "Add due" }).click();

  const result = page.getByRole("status").filter({ hasText: "Added ₹8,500 for Rohan Gupta" });
  await expect(result).toContainText("New customer");
  await expect(result).toContainText("First reminder goes out");
  await page.screenshot({ path: test.info().outputPath("manual-entry-added.png"), fullPage: true });

  // It is in the dues list, and the customer in the customer list.
  await page.getByRole("link", { name: "View dues" }).click();
  await expect(page).toHaveURL(/\/dues$/);
  await expect(page.getByRole("row", { name: /Rohan Gupta/ })).toContainText("₹8,500");
  await menu.getByRole("link", { name: "Customers", exact: true }).click();
  await expect(page.getByRole("row", { name: /Rohan Gupta/ })).toContainText(email);

  // The same due again (typed a little differently) is refused, with a way to the one already there.
  await menu.getByRole("link", { name: "Import", exact: true }).click();
  await expect(page.getByRole("tab", { name: "Add manually" })).toHaveAttribute("aria-selected", "true");
  await page.getByLabel("Name", { exact: true }).fill("rohan gupta");
  await page.getByLabel("Email", { exact: true }).fill(email.toUpperCase());
  await page.getByLabel("Phone (optional)").fill("+91 98765-43210");
  await page.getByLabel("Amount (₹)").fill("8500");
  await page.getByLabel("Due date").fill(dueDate);
  await page.getByRole("button", { name: "Add due" }).click();
  await expect(page.getByText("This due is already in your list, so it was not added again.")).toBeVisible();
  await page.getByRole("link", { name: "Open it" }).click();
  await expect(page).toHaveURL(/\/dues\/\d+$/);
  await expect(page.getByText("Rohan Gupta").first()).toBeVisible();
});

test("a due being typed in waits while the owner looks at other pages", async ({ page, request }) => {
  await signInAsNewOwner(page, request);
  const menu = page.getByRole("navigation", { name: "Main" });
  await page.goto("/import");
  await page.getByRole("tab", { name: "Add manually" }).click();

  // Nothing filled in: every field says what it needs.
  await page.getByRole("button", { name: "Add due" }).click();
  await expect(page.getByText("Enter a name")).toBeVisible();
  await expect(page.getByText("Add an email or a phone number")).toBeVisible();
  await expect(page.getByText("Enter the amount")).toBeVisible();
  await expect(page.getByText("Enter the due date")).toBeVisible();

  await page.getByLabel("Name", { exact: true }).fill("Diya Rao");
  await page.getByLabel("Amount (₹)").fill("1,500.50");
  await page.getByLabel("Reminder policy").selectOption({ label: "Gentle" });

  await menu.getByRole("link", { name: "Dues", exact: true }).click();
  await expect(page).toHaveURL(/\/dues$/);
  await menu.getByRole("link", { name: "Import", exact: true }).click();
  await expect(page.getByRole("tab", { name: "Add manually" })).toHaveAttribute("aria-selected", "true");
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("Diya Rao");
  await expect(page.getByLabel("Amount (₹)")).toHaveValue("1,500.50");
  await expect(page.getByLabel("Reminder policy").locator("option:checked")).toHaveText("Gentle");

  // The CSV tab is one click away, and back again keeps what was typed.
  await page.getByRole("tab", { name: "Upload a CSV" }).click();
  await expect(page.getByText("Drop a CSV file here, or click to choose one")).toBeVisible();
  await page.getByRole("tab", { name: "Add manually" }).click();
  await expect(page.getByLabel("Name", { exact: true })).toHaveValue("Diya Rao");
});
