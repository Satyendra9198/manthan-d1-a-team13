import { expect, test } from "@playwright/test";
import { loginStaff } from "./helpers";

test.describe("manager journey", () => {
  test("dashboard renders KPIs and switches the window", async ({ page }) => {
    await loginStaff(page, "manager1");
    await expect(page).toHaveURL(/\/manager/);
    await expect(page.getByTestId("dashboard")).toBeVisible();
    await expect(page.getByTestId("kpi-returns")).toBeVisible();
    await expect(page.getByTestId("reconciliation-card")).toBeVisible();

    const overview = page.waitForResponse((r) => r.url().includes("/analytics/overview") && r.url().includes("days=7"));
    await page.getByTestId("window-7").check({ force: true });
    expect((await overview).ok()).toBeTruthy();
    await expect(page).toHaveURL(/days=7/);
  });

  test("reconciliation is green", async ({ page }) => {
    await loginStaff(page, "manager1");
    await page.goto("/manager/reconciliation");
    await expect(page.getByTestId("reconciliation-card")).toHaveAttribute("data-ok", "true");
  });

  test("audit log explorer loads", async ({ page }) => {
    await loginStaff(page, "manager1");
    await page.goto("/manager/audit");
    await expect(page.getByTestId("audit-search")).toBeVisible();
    await expect(page.getByTestId("audit-table").or(page.getByText("No audit entries"))).toBeVisible();
  });

  test("manager cannot open the finance area (separation of duties)", async ({ page }) => {
    await loginStaff(page, "manager1");
    await page.goto("/finance");
    await expect(page.getByTestId("forbidden")).toBeVisible();
  });

  test("clear flag requires a note", async ({ page }) => {
    await loginStaff(page, "manager1");
    await page.goto("/manager/flagged");
    await expect(page.getByTestId("flagged-table").or(page.getByText("No flagged customers"))).toBeVisible();
    const row = page.getByTestId("flagged-row").first();
    test.skip((await row.count()) === 0, "no flagged customers");
    await row.getByTestId("clear-flag").click();
    await expect(page.getByTestId("confirm-clear-flag")).toBeDisabled();
    await page.getByTestId("clear-flag-note").fill("Verified with customer");
    await expect(page.getByTestId("confirm-clear-flag")).toBeEnabled();
    await page.keyboard.press("Escape");
  });
});
