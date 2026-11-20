import { expect, test } from "@playwright/test";
import { loginStaff } from "./helpers";

test.describe("finance journey", () => {
  test("queue shows the three tabs", async ({ page }) => {
    await loginStaff(page, "finance1");
    await expect(page).toHaveURL(/\/finance/);
    await expect(page.getByTestId("tab-PENDING_FINANCE")).toBeVisible();
    await expect(page.getByTestId("tab-REFUND_FAILED")).toBeVisible();
    await expect(page.getByTestId("tab-REFUND_PENDING")).toBeVisible();
  });

  test("reject requires a note", async ({ page }) => {
    await loginStaff(page, "finance1");
    await expect(page.getByTestId("finance-queue").or(page.getByText("Queue is empty"))).toBeVisible();
    const row = page.getByTestId("queue-row").first();
    test.skip((await row.count()) === 0, "no PENDING_FINANCE returns");
    await row.locator("a").first().click();
    await page.getByTestId("finance-reject").click();
    await expect(page.getByTestId("confirm-reject")).toBeDisabled();
    await page.getByTestId("reject-note").fill("Suspicious pattern");
    await expect(page.getByTestId("confirm-reject")).toBeEnabled();
    await page.keyboard.press("Escape");
  });

  test("two reviewers approve the same return at once: exactly one wins (F-04)", async ({ browser }) => {
    const ctxA = await browser.newContext();
    const ctxB = await browser.newContext();
    const a = await ctxA.newPage();
    const b = await ctxB.newPage();
    try {
      await loginStaff(a, "finance1");
      await loginStaff(b, "finance2");

      await expect(a.getByTestId("finance-queue").or(a.getByText("Queue is empty"))).toBeVisible();
      const row = a.getByTestId("queue-row").first();
      test.skip((await row.count()) === 0, "no PENDING_FINANCE returns to race on");
      await row.locator("a").first().click();
      await expect(a).toHaveURL(/\/finance\/returns\//);
      await b.goto(a.url());

      // Both reviewers open the confirm dialog on the same version.
      await a.getByTestId("finance-approve").click();
      await b.getByTestId("finance-approve").click();
      await expect(a.getByTestId("confirm-approve")).toBeVisible();
      await expect(b.getByTestId("confirm-approve")).toBeVisible();

      const isApprove = (url: string, method: string) => url.includes("/approve") && method === "POST";
      const [resA, resB] = await Promise.all([
        a.waitForResponse((r) => isApprove(r.url(), r.request().method())),
        b.waitForResponse((r) => isApprove(r.url(), r.request().method())),
        a.getByTestId("confirm-approve").click(),
        b.getByTestId("confirm-approve").click(),
      ]);

      const statuses = [resA.status(), resB.status()];
      expect(statuses.filter((s) => s === 200 || s === 202)).toHaveLength(1);
      expect(statuses.filter((s) => s === 409)).toHaveLength(1);

      const loserRes = resA.status() === 409 ? resA : resB;
      const loser = resA.status() === 409 ? a : b;
      const body = (await loserRes.json()) as { error: { code: string } };
      expect(["STALE_VERSION", "ILLEGAL_TRANSITION"]).toContain(body.error.code);
      // The loser is told and the page reloads the new state: approve is no longer offered.
      await expect(loser.getByTestId("finance-approve")).toHaveCount(0);
    } finally {
      await ctxA.close();
      await ctxB.close();
    }
  });
});
