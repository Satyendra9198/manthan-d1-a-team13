import { expect } from "@playwright/test";
import type { Page } from "@playwright/test";

export const STAFF_PASSWORD = process.env.E2E_STAFF_PASSWORD ?? "ReturnRight#2026";
export const EDGE_EMAIL = "edge.customer@example.com";
export const EDGE_ORDER = "RR-EDGE-001";

export async function loginCustomer(page: Page, email = EDGE_EMAIL, order = EDGE_ORDER): Promise<void> {
  await page.goto("/");
  await page.getByTestId("lookup-order").fill(order);
  await page.getByTestId("lookup-email").fill(email);
  await page.getByTestId("lookup-submit").click();
  await expect(page).toHaveURL(/\/portal/);
}

export async function loginStaff(page: Page, username: string, password = STAFF_PASSWORD): Promise<void> {
  await page.goto("/login");
  await page.getByTestId("staff-username").fill(username);
  await page.getByTestId("staff-password").fill(password);
  await page.getByTestId("staff-submit").click();
  await expect(page).not.toHaveURL(/\/login/);
}
