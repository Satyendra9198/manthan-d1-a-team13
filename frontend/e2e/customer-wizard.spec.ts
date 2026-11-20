import { expect, test, type Page } from '@playwright/test'

async function findOrder(page: Page, orderNumber: string, email: string) {
  await page.getByLabel('Order number').fill(orderNumber)
  await page.getByLabel('Email').fill(email)
  await page.getByRole('button', { name: 'Find my order' }).click()
}

test.beforeEach(async ({ page }) => {
  await page.goto('/')
})

test('shows an error for an unknown order', async ({ page }) => {
  await findOrder(page, 'RR-99999', 'ananya.rao@example.com')

  await expect(page.getByRole('alert')).toContainText('We couldn’t find that order')
  await expect(page.getByText('Step 1 of 5 · Find your order')).toBeVisible()
})

// 1×1 transparent PNG.
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==',
  'base64',
)

test('gives each item a reason and photo, then reaches step 4', async ({ page }) => {
  await findOrder(page, 'RR-10482', 'ananya.rao@example.com')
  await page.getByText('Linen shirt').click()
  await page.getByText('Ceramic mug set').click()
  await page.getByRole('button', { name: 'Continue' }).click()
  await expect(page.getByText('Step 3 of 5 · Reason and photos')).toBeVisible()

  for (const [reason, file] of [['Too large', 'front.png'], ['Damaged on arrival', 'chip.png']]) {
    await page.getByRole('radio', { name: reason }).click()
    await page
      .getByLabel(/take a photo or upload/i)
      .setInputFiles({ name: file, mimeType: 'image/png', buffer: PNG })
    await expect(page.getByText(file)).toBeVisible()
    await page.getByRole('button', { name: 'Continue' }).click()
  }

  await expect(page.getByText('Step 4 of 5')).toBeVisible()
})

test('finds an order, picks items, and continues to step 3', async ({ page }) => {
  await findOrder(page, 'RR-10482', 'ananya.rao@example.com')
  await expect(page.getByText('Step 2 of 5 · Choose items')).toBeVisible()

  const continueButton = page.getByRole('button', { name: 'Continue' })
  await expect(continueButton).toBeDisabled()
  await expect(page.getByRole('checkbox', { name: 'Wireless earbuds Pro' })).toBeDisabled()

  await page.getByText('Linen shirt').click()
  await page.getByText('Ceramic mug set').click()
  await expect(page.getByText('2 items selected')).toBeVisible()
  await expect(page.getByText('Est. refund ₹3,149')).toBeVisible()

  await continueButton.click()
  await expect(page.getByText('Step 3 of 5')).toBeVisible()
})
