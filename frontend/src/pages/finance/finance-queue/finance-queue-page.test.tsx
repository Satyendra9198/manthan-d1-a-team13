import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'
import { financeApi } from '@/api/finance'
import { getNewPayments, trackingMock } from '@/api/mocks/inspection.mock'
import { FinanceQueuePage } from './finance-queue-page'

async function setup() {
  const user = userEvent.setup()
  render(<FinanceQueuePage />)
  await screen.findAllByTestId('queue-row')
  return user
}

const row = (rma: string) => screen.getAllByTestId('queue-row').find((r) => r.textContent!.includes(rma))!
const panel = () => screen.getByRole('region', { name: /refund · RMA-/ })

afterEach(() => vi.restoreAllMocks())

describe('Finance queue page', () => {
  it('shows the queue like the design', async () => {
    await setup()
    expect(screen.getByRole('heading', { level: 1, name: 'Refunds awaiting approval' })).toBeInTheDocument()
    expect(screen.getByText('5 waiting · ₹1,17,938')).toBeInTheDocument()
    expect(screen.getAllByTestId('queue-row')).toHaveLength(5)

    const tv = row('RMA-2026-00712')
    for (const text of ['Priya Sharma', '55" 4K TV', 'A', '₹42,990', '2 h']) {
      expect(within(tv).getByText(text)).toBeInTheDocument()
    }
    expect(within(tv).getByLabelText('No flags')).toBeInTheDocument()
    expect(within(row('RMA-2026-00705')).getByText('Grade C · 50% applied')).toBeInTheDocument()
    expect(within(row('RMA-2026-00705')).getByText('₹12,250')).toBeInTheDocument()
    expect(within(row('RMA-2026-00716')).getByText('Flagged · 4 returns / 30 d')).toBeInTheDocument()
    expect(within(row('RMA-2026-00698')).getByText('1 d')).toBeInTheDocument()
  })

  it('Review opens the panel below with amount, inspector and key', async () => {
    const user = await setup()
    await user.click(screen.getByRole('button', { name: 'Review RMA-2026-00712' }))
    const p = panel()
    expect(within(p).getByRole('heading', { name: 'Approve refund · RMA-2026-00712' })).toBeInTheDocument()
    expect(within(p).getByText('₹42,990 to original payment (UPI)')).toBeInTheDocument()
    expect(within(p).getByText('Vikram (agent) · Grade A')).toBeInTheDocument()
    expect(within(p).getByText('rr-refund-88214')).toBeInTheDocument()
    expect(row('RMA-2026-00712')).toHaveAttribute('aria-selected', 'true')
    expect(p).toHaveFocus()

    await user.click(within(p).getByRole('button', { name: 'Cancel' }))
    expect(screen.queryByRole('region', { name: /refund · RMA-/ })).not.toBeInTheDocument()
  })

  it('Approve and pay sends the refund key, pays once and removes the row', async () => {
    const spy = vi.spyOn(financeApi, 'approveRefund')
    const user = await setup()
    await user.click(screen.getByRole('button', { name: 'Review RMA-2026-00712' }))
    const approve = within(panel()).getByRole('button', { name: 'Approve and pay' })
    await user.dblClick(approve)

    expect(await screen.findByRole('status')).toHaveTextContent(
      /RMA-2026-00712: refund of ₹42,990 paid\. Reference GW-\d{4}-2026\./,
    )
    expect(spy).toHaveBeenCalledTimes(1)
    expect(spy).toHaveBeenCalledWith('ret_00712', 'rr-refund-88214')
    expect(getNewPayments()).toHaveLength(1)
    expect(screen.getAllByTestId('queue-row')).toHaveLength(4)
    expect(screen.getByText('4 waiting · ₹74,948')).toBeInTheDocument()
  })

  it('retrying after a timeout reuses the same key and still pays once', async () => {
    const real = financeApi.approveRefund
    const spy = vi
      .spyOn(financeApi, 'approveRefund')
      .mockImplementationOnce(async (id, key) => {
        await real(id, key) // server paid…
        throw new TypeError('Failed to fetch') // …but the response was lost
      })
      .mockImplementation(real)
    const user = await setup()
    await user.click(screen.getByRole('button', { name: 'Review RMA-2026-00712' }))
    await user.click(within(panel()).getByRole('button', { name: 'Approve and pay' }))
    expect(await within(panel()).findByRole('alert')).toHaveTextContent(/Nothing was paid twice/)

    await user.click(within(panel()).getByRole('button', { name: 'Approve and pay' }))
    expect(await screen.findByRole('status')).toHaveTextContent('refund of ₹42,990 paid')
    expect(spy.mock.calls.map((c) => c[1])).toEqual(['rr-refund-88214', 'rr-refund-88214'])
    expect(getNewPayments()).toHaveLength(1)
  })

  it('Decline needs a reason the customer will see', async () => {
    const spy = vi.spyOn(financeApi, 'declineRefund')
    const user = await setup()
    await user.click(screen.getByRole('button', { name: 'Review RMA-2026-00716' }))
    await user.click(within(panel()).getByRole('button', { name: 'Decline' }))

    expect(within(panel()).getByRole('heading', { name: 'Decline refund · RMA-2026-00716' })).toBeInTheDocument()
    await user.click(within(panel()).getByRole('button', { name: 'Confirm decline' }))
    expect(within(panel()).getByText(/Add a reason/)).toBeInTheDocument()
    expect(spy).not.toHaveBeenCalled()

    await user.type(within(panel()).getByLabelText('Reason shown to the customer'), 'Item is not the one we sent.')
    await user.click(within(panel()).getByRole('button', { name: 'Confirm decline' }))
    expect(await screen.findByRole('status')).toHaveTextContent('RMA-2026-00716: refund declined')
    expect(spy).toHaveBeenCalledWith('ret_00716', { note: 'Item is not the one we sent.' }, 'rr-refund-88220')
    expect(getNewPayments()).toHaveLength(0)

    const t = await trackingMock.getTracking('RMA-2026-00716')
    expect(t.status).toBe('rejected')
  })

  it('drops a row someone else already decided', async () => {
    vi.spyOn(financeApi, 'approveRefund').mockRejectedValueOnce(
      new ApiError(409, 'RMA-2026-00712 is no longer waiting for approval.', 'NOT_AWAITING_APPROVAL'),
    )
    const user = await setup()
    await user.click(screen.getByRole('button', { name: 'Review RMA-2026-00712' }))
    await user.click(within(panel()).getByRole('button', { name: 'Approve and pay' }))
    expect(await screen.findByRole('status')).toHaveTextContent('no longer waiting for approval')
    expect(screen.queryByRole('region', { name: /refund · RMA-/ })).not.toBeInTheDocument()
  })

  it('shows an empty state when nothing is waiting', async () => {
    vi.spyOn(financeApi, 'getQueue').mockResolvedValueOnce([])
    render(<FinanceQueuePage />)
    expect(await screen.findByText(/Nothing waiting for approval/)).toBeInTheDocument()
  })
})
