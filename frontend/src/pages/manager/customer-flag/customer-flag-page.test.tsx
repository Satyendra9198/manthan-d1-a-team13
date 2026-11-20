import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { getMockFlagAudit } from '@/api/mocks/dashboard.mock'
import { routes } from '@/router'

async function setup(id = 'cus_0001') {
  const user = userEvent.setup()
  const router = createMemoryRouter(routes, { initialEntries: [`/dashboard/customers/${id}`] })
  render(<RouterProvider router={router} />)
  await screen.findByRole('heading', { level: 1 })
  return { user, router }
}

const panel = () => screen.getByRole('region', { name: 'Review this flag' })

describe('Customer flag page', () => {
  it('matches the design', async () => {
    await setup()
    expect(screen.getByRole('heading', { level: 1, name: 'Rahul Verma' })).toBeInTheDocument()
    expect(screen.getByText(/^rahul\.verma@example\.com · customer since \w+ \d{4}$/)).toBeInTheDocument()
    expect(screen.getByTestId('flag-badge')).toHaveTextContent(/^Flagged · \d+ returns in 30 days$/)
    expect(screen.getByRole('link', { name: /Back to dashboard/ })).toHaveAttribute('href', '/dashboard')
    expect(screen.getByRole('link', { name: 'Dashboard' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByText('Operations manager')).toBeInTheDocument()

    for (const name of ['Returns in 30 days', 'Refunded', 'Pending finance', 'Flag raised']) {
      expect(screen.getByRole('group', { name })).toBeInTheDocument()
    }
    expect(within(screen.getByRole('group', { name: 'Flag raised' })).getByText('by system')).toBeInTheDocument()
    expect(screen.getAllByTestId('customer-return').length).toBeGreaterThanOrEqual(4)
    expect(within(panel()).getByText(/all of this customer's refunds go to finance/)).toBeInTheDocument()
  })

  it('needs a note before keeping or clearing', async () => {
    const { user } = await setup()
    await user.click(within(panel()).getByRole('button', { name: 'Clear flag' }))
    expect(within(panel()).getByText(/Write why/)).toBeInTheDocument()
    expect(within(panel()).getByLabelText('Review note (required)')).toHaveAttribute('aria-invalid', 'true')
    expect(getMockFlagAudit()).toEqual([])
  })

  it('Keep flag records the review and stays flagged', async () => {
    const { user } = await setup()
    await user.type(within(panel()).getByLabelText('Review note (required)'), 'Repeated wear on returned items.')
    await user.click(within(panel()).getByRole('button', { name: 'Keep flag' }))
    expect(await screen.findByRole('status')).toHaveTextContent('Flag kept.')
    expect(screen.getByTestId('flag-badge')).toHaveTextContent(/^Flagged/)
    expect(screen.getByTestId('last-review')).toHaveTextContent('Last review: kept by Operations manager')
    expect(getMockFlagAudit()[0].type).toBe('FLAG_REVIEWED')
  })

  it('Clear flag writes FLAG_CHANGED and removes the customer from the dashboard', async () => {
    const { user, router } = await setup()
    await user.type(
      within(panel()).getByLabelText('Review note (required)'),
      'Two returns are size issues and one is a defect confirmed at inspection. No abuse pattern.',
    )
    await user.dblClick(within(panel()).getByRole('button', { name: 'Clear flag' }))
    expect(await screen.findByRole('status')).toHaveTextContent('FLAG_CHANGED')
    expect(screen.getByTestId('flag-badge')).toHaveTextContent('Flag cleared')
    expect(within(panel()).queryByRole('button', { name: 'Clear flag' })).not.toBeInTheDocument()
    expect(getMockFlagAudit()).toHaveLength(1)

    await user.click(screen.getByRole('link', { name: /Back to dashboard/ }))
    expect(router.state.location.pathname).toBe('/dashboard')
    await screen.findAllByTestId('flagged-row')
    expect(screen.queryByText('Rahul Verma')).not.toBeInTheDocument()
  })

  it('shows an error for an unknown customer', async () => {
    render(<RouterProvider router={createMemoryRouter(routes, { initialEntries: ['/dashboard/customers/nope'] })} />)
    expect(await screen.findByRole('alert')).toHaveTextContent('Customer not found.')
  })
})
