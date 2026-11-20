import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { setMockGatewayMode } from '@/api/mocks/gateway.mock'
import { getGatewayCharges } from '@/api/mocks/gateway.mock'
import { refundsApi } from '@/api/refunds'
import { routes } from '@/router'

async function setup() {
  const user = userEvent.setup()
  const router = createMemoryRouter(routes, { initialEntries: ['/refunds'] })
  render(<RouterProvider router={router} />)
  await screen.findAllByTestId('refund-row')
  return { user, router }
}

const banner = () => screen.getByRole('region', { name: 'Failed refund RMA-2026-00701' })
const refundRow = (rma: string) => screen.getAllByTestId('refund-row').find((r) => r.textContent!.includes(rma))!

afterEach(() => {
  vi.restoreAllMocks()
  setMockGatewayMode('ok')
})

describe('Refunds and ledger page', () => {
  it('shows the failed refund banner, refunds and a balanced ledger', async () => {
    await setup()
    expect(screen.getByRole('heading', { level: 1, name: 'Refunds and ledger' })).toBeInTheDocument()
    expect(screen.getByText('1 failed refund')).toBeInTheDocument()
    expect(screen.getByText('Finance reviewer')).toBeInTheDocument()

    expect(within(banner()).getByText('Refund failed · RMA-2026-00701 · Bluetooth speaker · ₹14,999')).toBeInTheDocument()
    expect(within(banner()).getByText(/on all 4 attempts \(1 \+ 3 automatic retries\)\. Key rr-refund-88175/)).toBeInTheDocument()

    const failed = refundRow('RMA-2026-00701')
    for (const t of ['₹14,999', 'Card', 'Failed', '4', 'rr-refund-88175']) expect(within(failed).getByText(t)).toBeInTheDocument()
    expect(within(refundRow('RMA-2026-00712')).getByText('Pending')).toBeInTheDocument()
    expect(within(refundRow('RMA-2026-00668')).getByText('Store credit')).toBeInTheDocument()

    expect(screen.getAllByTestId('ledger-row')).toHaveLength(4)
    expect(screen.getByTestId('ledger-balance')).toHaveTextContent('Balanced')
    expect(screen.getByTestId('ledger-totals')).toHaveTextContent('Debits ₹3,849Credits ₹3,849')
  })

  it('Retry with same key pays, posts ledger rows and clears the banner', async () => {
    const spy = vi.spyOn(refundsApi, 'retryRefund')
    const { user } = await setup()
    await user.dblClick(within(banner()).getByRole('button', { name: 'Retry with same key' }))

    expect(await screen.findByRole('status')).toHaveTextContent(
      /RMA-2026-00701: ₹14,999 paid on retry\. Reference GW-\d+-2026\. Ledger rows posted\./,
    )
    expect(spy).toHaveBeenCalledTimes(1)
    expect(spy).toHaveBeenCalledWith('rfd_ret_00701', 'rr-refund-88175')
    expect(getGatewayCharges()).toHaveLength(1)
    expect(screen.queryByRole('region', { name: /Failed refund/ })).not.toBeInTheDocument()
    expect(screen.queryByText(/failed refund/)).not.toBeInTheDocument()
    expect(within(refundRow('RMA-2026-00701')).getByText('Succeeded')).toBeInTheDocument()
    expect(screen.getAllByTestId('ledger-row')).toHaveLength(6)
    expect(screen.getByTestId('ledger-balance')).toHaveTextContent('Balanced')
  })

  it('still failing: explains nothing was charged and keeps the banner', async () => {
    setMockGatewayMode('timeout')
    const { user } = await setup()
    await user.click(within(banner()).getByRole('button', { name: 'Retry with same key' }))
    expect(await screen.findByRole('status')).toHaveTextContent('still failing after 8 attempts. Nothing was charged')
    expect(within(banner()).getByText(/on all 8 attempts/)).toBeInTheDocument()
    expect(screen.getAllByTestId('ledger-row')).toHaveLength(4)
  })

  it('View audit trail links to the audit log for that RMA', async () => {
    const { user, router } = await setup()
    await user.click(within(banner()).getByRole('link', { name: 'View audit trail' }))
    expect(router.state.location.pathname).toBe('/audit')
    expect(router.state.location.search).toBe('?rma=RMA-2026-00701')
  })
})
