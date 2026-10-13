import path from "node:path";

import { expect, test } from "@playwright/test";

import { signInAsNewOwner } from "./session";

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
  await expect(result).toContainText("First reminder goes out 3 Nov 2026, 10:00");

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
