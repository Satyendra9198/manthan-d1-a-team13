import { expect, test } from '@playwright/test'

test('sidebar navigation opens each screen', async ({ page }) => {
  await page.goto('/')
  await expect(page).toHaveURL(/\/scan$/)
  await expect(page.getByRole('heading', { name: 'Scan and inspect' })).toBeVisible()

  await page.getByRole('link', { name: 'Refunds & ledger' }).click()
  await expect(page).toHaveURL(/\/refunds$/)
  await expect(page.getByRole('heading', { name: 'Refunds & ledger' })).toBeVisible()
})

test('mobile menu opens and navigates', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 800 })
  await page.goto('/scan')
  await page.getByRole('button', { name: 'Open menu' }).click()
  await page.getByRole('dialog').getByRole('link', { name: 'Dashboard' }).click()
  await expect(page).toHaveURL(/\/dashboard$/)
  await expect(page.getByRole('dialog')).toBeHidden()
})
