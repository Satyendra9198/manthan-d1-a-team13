import { expect } from "@playwright/test";
import type { Page } from "@playwright/test";
import { existsSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

/** Secret from the environment, else from .env at the repo root (never hard-coded; see .env.example). */
function secret(name: string): string {
  const fromEnv = process.env[name];
  if (fromEnv) return fromEnv;
  for (const file of [resolve(process.cwd(), "../.env"), resolve(process.cwd(), ".env")]) {
    if (!existsSync(file)) continue;
    const line = readFileSync(file, "utf8").split(/\r?\n/).find((l) => l.startsWith(`${name}=`));
    if (line) return line.slice(name.length + 1).trim();
  }
  throw new Error(`${name} is not set (export it or create .env from .env.example)`);
}

export const STAFF_PASSWORD = process.env.E2E_STAFF_PASSWORD ?? secret("RR_SEED_STAFF_PASSWORD");
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
