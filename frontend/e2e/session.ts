import { type APIRequestContext, expect, type Page } from "@playwright/test";

import { E2E_API_URL } from "../playwright.config";

export function uniqueEmail(prefix = "owner") {
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;
}

/** Creates an owner through the API and makes every page load in this test start signed in as them. */
export async function signInAsNewOwner(page: Page, request: APIRequestContext, businessName = "Bright Tuition") {
  const email = uniqueEmail();
  const response = await request.post(`${E2E_API_URL}/api/auth/signup`, {
    data: { email, password: "correct-horse-battery", businessName },
  });
  expect(response.status()).toBe(201);
  const { token } = (await response.json()) as { token: string };
  await page.addInitScript((value) => window.localStorage.setItem("paynudge.token", value), token);
  return { email, token };
}
