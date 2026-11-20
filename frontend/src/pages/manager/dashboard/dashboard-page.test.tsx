import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { dashboardApi } from '@/api/dashboard'
import { routes } from '@/router'

async function setup() {
  const user = userEvent.setup()
  const router = createMemoryRouter(routes, { initialEntries: ['/dashboard'] })
  render(<RouterProvider router={router} />)
  await screen.findAllByTestId('stat-value')
  return { user, router }
}

afterEach(() => vi.restoreAllMocks())

describe('Dashboard page', () => {
  it('shows the four stats, both charts and flagged customers', async () => {
    await setup()
    expect(screen.getByRole('heading', { level: 1, name: 'Returns overview' })).toBeInTheDocument()
    expect(screen.getByText('Last 30 days · sample data from the seed script')).toBeInTheDocument()
    expect(screen.getByText('Operations manager')).toBeInTheDocument()

    const tile = (name: string) => screen.getByRole('group', { name })
    expect(within(tile('Return rate')).getByTestId('stat-value')).toHaveTextContent(/^\d+\.\d%$/)
    expect(within(tile('Return rate')).getByText('of 2,000 orders')).toBeInTheDocument()
    expect(within(tile('Avg cycle time')).getByTestId('stat-value')).toHaveTextContent(/^\d\.\d days$/)
    expect(within(tile('Refund success rate')).getByTestId('stat-value')).toHaveTextContent(/^9\d\.\d%$/)

    const bars = screen.getAllByTestId('category-bar')
    expect(bars).toHaveLength(6)
    expect(bars.at(-1)).toHaveAccessibleName(/not returnable/)
    expect(screen.getByText('Innerwear and perishables are not returnable.')).toBeInTheDocument()

    expect(screen.getAllByTestId('reason-row')).toHaveLength(5)
    expect(screen.getAllByTestId('reason-row')[0]).toHaveTextContent('Wrong size')

    const flagged = screen.getAllByTestId('flagged-row')
    expect(within(tile('Flagged customers')).getByTestId('stat-value')).toHaveTextContent(String(flagged.length))
    expect(screen.getByRole('region', { name: 'Flagged customers' })).toBeInTheDocument()
    expect(flagged.some((r) => r.textContent!.includes('Rahul Verma'))).toBe(true)
  })

  it('shows a tooltip on bar focus (keyboard) and hover', async () => {
    const { user } = await setup()
    const first = screen.getAllByTestId('category-bar')[0]
    act(() => first.focus())
    expect(within(first).getByRole('tooltip')).toHaveTextContent(/\d+\.\d%.* of [\d,]+ orders/)
    await user.hover(screen.getAllByTestId('category-bar')[1])
    expect(screen.getAllByRole('tooltip')).toHaveLength(1)
  })

  it('has a table view for each chart', async () => {
    await setup()
    expect(screen.getByRole('table', { name: 'Return rate by category' })).toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'Top return reasons' })).toBeInTheDocument()
  })

  it('changing the period refetches and keeps the frame', async () => {
    const spy = vi.spyOn(dashboardApi, 'getDashboard')
    const { user } = await setup()
    await user.selectOptions(screen.getByLabelText('Period'), '7')
    expect(spy).toHaveBeenLastCalledWith(7)
    expect(await screen.findByText('Last 7 days · sample data from the seed script')).toBeInTheDocument()
    expect(await screen.findByText('mock gateway, 7 days')).toBeInTheDocument()
  })

  it('exports a CSV file', async () => {
    const createObjectURL = vi.fn(() => 'blob:x')
    const revoke = vi.fn()
    Object.assign(URL, { createObjectURL, revokeObjectURL: revoke })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const { user } = await setup()
    await user.click(screen.getByRole('button', { name: 'Export CSV' }))
    expect(createObjectURL).toHaveBeenCalledWith(expect.any(Blob))
    expect(click).toHaveBeenCalled()
  })

  it('View history opens the customer page', async () => {
    const { user, router } = await setup()
    await user.click(screen.getByRole('link', { name: 'View history for Rahul Verma' }))
    expect(router.state.location.pathname).toBe('/dashboard/customers/cus_0001')
    expect(await screen.findByRole('heading', { level: 1, name: 'Rahul Verma' })).toBeInTheDocument()
  })
})
