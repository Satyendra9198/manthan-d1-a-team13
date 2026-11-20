import { expect, test, type Page } from '@playwright/test'

/**
 * The UI against the real Spring Boot backend and its dev seed (RMA-DEMO-*). Run in order: each
 * test changes demo data, so reset the seed before running again.
 */
test.describe.configure({ mode: 'serial' })

async function signIn(page: Page, email: string) {
  await page.getByLabel('Work email').fill(email)
  await page.getByLabel('Password').fill('Password@123')
  await page.getByRole('button', { name: 'Sign in' }).click()
}

test('a staff screen asks a signed-out visitor to sign in', async ({ page }) => {
  await page.goto('/scan')
  await expect(page).toHaveURL(/\/login\?next=%2Fscan$/)
  await expect(page.getByRole('heading', { name: 'Staff sign in' })).toBeVisible()

  await page.getByLabel('Work email').fill('riya.warehouse@returnright.example')
  await page.getByLabel('Password').fill('wrong')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByRole('alert')).toHaveText('Email or password is incorrect.')
})

test('warehouse agent scans a received parcel, grades it C and the refund is paid', async ({ page }) => {
  await page.goto('/scan')
  await signIn(page, 'riya.warehouse@returnright.example')
  await expect(page.getByRole('heading', { name: 'Scan and inspect' })).toBeVisible()
  await expect(page.getByText('Riya Kapoor')).toBeVisible()
  await expect(page.getByText(/Today: \d+ inspected/)).toBeVisible()

  await page.getByLabel('Scan or enter QR code').fill('RMA-DEMO-00006')
  await page.getByRole('button', { name: 'Find return' }).click()
  await expect(page.getByText('RMA-DEMO-00006 · Wool sweater + 1 more')).toBeVisible()
  await expect(page.getByText('Karan Kapoor')).toBeVisible()

  await page.getByRole('radio', { name: /^C/ }).click()
  await page.getByRole('button', { name: 'Accept', exact: true }).click()
  await expect(page.getByText(/RMA-DEMO-00006 saved\. Refund of .+ paid\./)).toBeVisible()
})

test('scanning a parcel that is still in transit receives it first', async ({ page }) => {
  await page.goto('/login')
  await signIn(page, 'riya.warehouse@returnright.example')

  await page.getByLabel('Scan or enter QR code').fill('rma-demo-00011')
  await page.getByRole('button', { name: 'Find return' }).click()
  await expect(page.getByText('RMA-DEMO-00011 · Ceramic vase')).toBeVisible()
  await expect(page.getByTestId('return-status')).toHaveText(/^In transit\s*Received$/)
})

test('an agent cannot open the finance screens', async ({ page }) => {
  await page.goto('/login')
  await signIn(page, 'riya.warehouse@returnright.example')
  await expect(page).toHaveURL(/\/scan$/)
  await page.goto('/finance')
  await expect(page).toHaveURL(/\/scan$/)
  await expect(page.getByRole('link', { name: 'Finance queue' })).toHaveCount(0)
})

test('finance reviewer approves the laptop refund and it is paid', async ({ page }) => {
  await page.goto('/login')
  await signIn(page, 'kavya.finance@returnright.example')
  await expect(page).toHaveURL(/\/finance$/)

  await page.getByRole('button', { name: 'Review RMA-DEMO-00009' }).click()
  await expect(page.getByText('Riya Kapoor').first()).toBeVisible()
  await page.getByRole('button', { name: 'Approve and pay' }).click()
  await expect(page.getByText(/RMA-DEMO-00009: refund of .+ paid\. Reference MOCK-TXN-\d+\./)).toBeVisible()
  await expect(page.getByRole('button', { name: 'Review RMA-DEMO-00009' })).toHaveCount(0)
})

test('customer tracks the grade C refund with their order email', async ({ page }) => {
  await page.goto('/track')
  await page.getByLabel('Return number').fill('RMA-DEMO-00007')
  await page.getByLabel('Email').fill('neha.gupta@example.com')
  await page.getByRole('button', { name: 'Track return' }).click()

  await expect(page.getByRole('heading', { name: 'RMA-DEMO-00007' })).toBeVisible()
  await expect(page.getByText('₹949.50 refunded')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Why ₹949.50 and not ₹1,899?' })).toBeVisible()
  await expect(page.getByText(/Reference: MOCK-TXN-DEMO-1/)).toBeVisible()
})

test('customer with the wrong email is told the return was not found', async ({ page }) => {
  await page.goto('/track/RMA-DEMO-00007')
  await page.getByLabel('Email').fill('someone.else@example.com')
  await page.getByRole('button', { name: 'Track return' }).click()
  await expect(page.getByRole('alert')).toContainText("couldn't find that return")
})
