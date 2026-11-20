import { expect, test } from "@playwright/test";
import { loginStaff } from "./helpers";

test.describe("warehouse journey", () => {
  test("scan station reports an unknown code as an error", async ({ page }) => {
    await loginStaff(page, "warehouse1");
    await expect(page).toHaveURL(/\/warehouse/);
    const input = page.getByTestId("scan-input");
    await expect(input).toBeFocused();
    await input.fill("RMA-DOES-NOT-EXIST");
    await input.press("Enter");
    const result = page.getByTestId("scan-result");
    await expect(result).toBeVisible();
    await expect(result).toHaveAttribute("data-kind", "error");
  });

  test("receive an approved return by RMA, inspect and complete", async ({ page }) => {
    await loginStaff(page, "warehouse1");
    await page.goto("/warehouse/queue?tab=APPROVED");
    await expect(page.getByTestId("warehouse-queue").or(page.getByText("All clear"))).toBeVisible();
    const row = page.getByTestId("queue-row").first();
    test.skip((await row.count()) === 0, "no APPROVED returns in seed data");
    const rma = (await row.getAttribute("data-rma")) ?? "";

    await page.goto("/warehouse");
    await page.getByTestId("scan-input").fill(rma);
    await page.getByTestId("scan-input").press("Enter");
    await expect(page.getByTestId("scan-result")).toHaveAttribute("data-kind", "success");
    await expect(page.getByTestId("scan-rma")).toHaveText(rma);

    // Scanning the same parcel again is safe: already_received (F-06).
    await page.getByTestId("scan-input").fill(rma);
    await page.getByTestId("scan-input").press("Enter");
    await expect(page.getByTestId("scan-result")).toHaveAttribute("data-kind", "warning");

    await page.getByTestId("start-inspection").click();
    const units = page.getByTestId("inspection-unit");
    await expect(units.first()).toBeVisible();
    await expect(page.getByTestId("complete-inspection")).toBeDisabled();

    for (const unit of await units.all()) {
      await unit.getByTestId("decision-ACCEPT").check({ force: true });
      await unit.getByTestId("grade-B").check({ force: true });
    }
    await expect(page.getByTestId("complete-inspection")).toBeEnabled();
    await page.getByTestId("complete-inspection").click();
    await page.getByTestId("confirm-complete").click();
    await expect(page).toHaveURL(/\/warehouse\/queue/);
  });
});
