import { expect, test } from '@playwright/test'

test.beforeEach(async ({ page }) => {
  await page.goto('/')
})

test('adds and completes tasks', async ({ page }) => {
  const input = page.getByLabel('New task')
  await input.fill('Buy groceries')
  await input.press('Enter')
  await input.fill('Walk the dog')
  await page.getByRole('button', { name: 'Add' }).click()

  await expect(page.getByTestId('task-item')).toHaveCount(2)
  await expect(page.getByTestId('active-count')).toHaveText('2 active')

  await page.getByLabel('Buy groceries', { exact: true }).check()
  await expect(page.getByTestId('active-count')).toHaveText('1 active')
})

test('filters tasks by status', async ({ page }) => {
  const input = page.getByLabel('New task')
  for (const title of ['One', 'Two']) {
    await input.fill(title)
    await input.press('Enter')
  }
  await page.getByLabel('One', { exact: true }).check()

  await page.getByRole('tab', { name: 'Active' }).click()
  await expect(page.getByTestId('task-item')).toHaveText(['Two'])

  await page.getByRole('tab', { name: 'Completed' }).click()
  await expect(page.getByTestId('task-item')).toHaveText(['One'])

  await page.getByRole('button', { name: 'Clear completed' }).click()
  await expect(page.getByTestId('task-item')).toHaveCount(0)
})

test('persists tasks across reloads', async ({ page }) => {
  const input = page.getByLabel('New task')
  await input.fill('Survive reload')
  await input.press('Enter')
  await page.reload()
  await expect(page.getByText('Survive reload')).toBeVisible()
})
