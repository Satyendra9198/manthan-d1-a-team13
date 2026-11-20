import { expect, test } from "@playwright/test";
import { EDGE_ORDER, loginCustomer } from "./helpers";

test.describe("customer journey", () => {
  test("rejects an unknown order with a generic message", async ({ page }) => {
    await page.goto("/");
    await page.getByTestId("lookup-order").fill("RR-DOES-NOT-EXIST");
    await page.getByTestId("lookup-email").fill("nobody@example.com");
    await page.getByTestId("lookup-submit").click();
    await expect(page.getByTestId("customer-lookup-form").getByRole("alert")).toBeVisible();
    await expect(page).not.toHaveURL(/\/portal/);
  });

  test("lookup, start a return for the day-30 item and see the label", async ({ page }) => {
    await loginCustomer(page);

    // RR-EDGE-001: apparel delivered exactly 30 days ago, still eligible [A-02].
    const card = page.locator(`[data-testid="order-card"][data-order="${EDGE_ORDER}"]`);
    await expect(card).toBeVisible();
    await card.click();
    await expect(page.getByTestId("order-units")).toBeVisible();

    // Eligibility microcopy is rendered from the backend message.
    const eligibleUnit = page.locator('[data-testid="order-unit"][data-eligible="true"]').first();
    await expect(eligibleUnit.getByTestId("eligibility-message")).not.toBeEmpty();

    await page.getByTestId("start-return").click();

    // Step 1: select the first eligible unit.
    await page.locator('[data-testid="wizard-unit-checkbox"]:not([disabled])').first().check();
    await page.getByTestId("wizard-next").click();

    // Step 2: a reason for every selected unit.
    for (const select of await page.getByTestId("wizard-reason").all()) {
      await select.selectOption("SIZE_FIT");
    }
    await page.getByTestId("wizard-next").click();

    // Step 3: refund method.
    await page.getByTestId("method-ORIGINAL_PAYMENT").check({ force: true });
    await page.getByTestId("wizard-next").click();

    // Step 4: review and submit (one Idempotency-Key for this submission).
    await expect(page.getByTestId("wizard-review")).toBeVisible();
    await expect(page.getByTestId("wizard-estimate")).toContainText("₹");
    await page.getByTestId("wizard-submit").click();

    await expect(page.getByTestId("return-success")).toBeVisible();
    const rma = (await page.getByTestId("success-rma").textContent())?.trim() ?? "";
    expect(rma.length).toBeGreaterThan(0);

    // Label page renders RMA and QR.
    await page.getByTestId("success-label").click();
    await expect(page.getByTestId("shipping-label")).toBeVisible();
    await expect(page.getByTestId("label-rma")).toHaveText(rma);
    await expect(page.getByTestId("label-qr")).toBeVisible();
    await expect(page.getByTestId("print-label")).toBeEnabled();
  });

  test("return detail shows the timeline", async ({ page }) => {
    await loginCustomer(page);
    await page.goto("/portal/returns");
    await expect(page.getByTestId("returns-list").or(page.getByText("No returns yet"))).toBeVisible();
    const first = page.getByTestId("return-card").first();
    test.skip((await first.count()) === 0, "no returns for this customer");
    await first.click();
    await expect(page.getByTestId("timeline")).toBeVisible();
    await expect(page.getByTestId("status-label")).not.toBeEmpty();
  });
});
