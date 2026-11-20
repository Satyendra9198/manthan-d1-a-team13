import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'
import { inspectionApi } from '@/api/inspection'
import { getMockLedger } from '@/api/mocks/inspection.mock'
import { ScanInspectPage } from './scan-inspect-page'

type User = ReturnType<typeof userEvent.setup>

const radio = (g: string, n: number) =>
  screen.getByRole('radio', { name: new RegExp(`${g}\\s*·\\s*key ${n}`) })
const button = (name: RegExp | string) => screen.getByRole('button', { name })

async function setup() {
  const user = userEvent.setup()
  render(<ScanInspectPage initial={{}} />)
  await screen.findByText(/Today: \d+ inspected/)
  return user
}

async function scan(user: User, code: string) {
  const input = screen.getByLabelText('Scan or enter QR code')
  await user.clear(input)
  await user.type(input, code)
  await user.click(button('Find return'))
}

async function open(user: User, code: string, heading: RegExp) {
  await scan(user, code)
  await screen.findByRole('heading', { name: heading })
}

const LINEN = ['rr-qr-7f3a9c41', /RMA-2026-00731 · Linen shirt/] as const
const SHORTS = ['rr-qr-a1d0e702', /RMA-2026-00702 · Denim shorts/] as const
const SHOES = ['rr-qr-c4f5e744', /RMA-2026-00744 · Running shoes/] as const
const HEADPHONES = ['rr-qr-1b2c3d4e', /Wireless headphones/] as const

afterEach(() => vi.restoreAllMocks())

describe('Scan & inspect page', () => {
  it('opens with the static design data by default', () => {
    render(<ScanInspectPage />)
    expect(screen.getByText('Today: 37 inspected')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: LINEN[1] })).toBeInTheDocument()
    expect(radio('C', 3)).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByTestId('refund-preview')).toHaveTextContent('₹949.50')
    expect(screen.getByText(/Under ₹10,000, so this goes straight to refund after you accept/)).toBeInTheDocument()
  })

  it('shows the empty state and focuses the field when Find is pressed empty', async () => {
    const user = await setup()
    expect(screen.getByText(/enter its QR code to start/i)).toBeInTheDocument()
    await user.click(button('Find return'))
    expect(screen.getByLabelText('Scan or enter QR code')).toHaveFocus()
  })

  it('loads return details from the API', async () => {
    const user = await setup()
    await open(user, ...LINEN)
    expect(screen.getByTestId('return-status')).toHaveTextContent('In transitReceived')
    for (const text of ['Ananya Rao', 'Too large', 'Original payment', '₹1,899', 'Customer photo · 1 of 2']) {
      expect(screen.getByText(text)).toBeInTheDocument()
    }
  })

  it('explains unknown and already-processed codes', async () => {
    const user = await setup()
    await scan(user, 'nope')
    expect(await screen.findByRole('alert')).toHaveTextContent('No return found for "nope"')
    await scan(user, 'rr-qr-9e8d7c6b')
    expect(await screen.findByRole('alert')).toHaveTextContent('already been refunded')
  })

  it('previews 100% for grade B and 50% for grade C', async () => {
    const user = await setup()
    await open(user, ...LINEN)
    await user.click(radio('B', 2))
    expect(screen.getByTestId('refund-preview')).toHaveTextContent('Grade B refund (100% of ₹1,899)₹1,899')
    await user.click(radio('C', 3))
    expect(screen.getByTestId('refund-preview')).toHaveTextContent('Grade C refund (50% of ₹1,899)₹949.50')
  })

  describe('Accept (A): no popup, sends requiresFinanceApproval', () => {
    it('sends false for a small refund from an unflagged customer and pays', async () => {
      const spy = vi.spyOn(inspectionApi, 'submitDecision')
      const user = await setup()
      await open(user, ...LINEN)
      await user.click(radio('C', 3))
      await user.type(screen.getByLabelText('Inspection notes'), 'Seam torn')
      await user.click(button(/^Accept/))

      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      expect(spy).toHaveBeenCalledWith(
        'ret_00731',
        { decision: 'accept', grade: 'C', notes: 'Seam torn', requiresFinanceApproval: false },
        expect.stringMatching(/^[0-9a-f-]{36}$/),
      )
      expect(await screen.findByRole('status')).toHaveTextContent('Refund of ₹949.50 paid.')
      expect(getMockLedger()).toHaveLength(1)
    })

    it('sends true above ₹10,000 / flagged customer and routes to finance', async () => {
      const spy = vi.spyOn(inspectionApi, 'submitDecision')
      const user = await setup()
      await open(user, ...HEADPHONES)
      expect(screen.getByText('Flagged · 4 returns in 30 days')).toBeInTheDocument()
      await user.click(radio('A', 1))
      expect(screen.getByText(/Over ₹10,000 and customer flagged, so finance must approve/)).toBeInTheDocument()
      await user.keyboard('a')

      expect(spy.mock.calls[0][1]).toMatchObject({ decision: 'accept', requiresFinanceApproval: true })
      expect(await screen.findByRole('status')).toHaveTextContent('sent to the finance queue')
      expect(getMockLedger()).toHaveLength(0)
    })

    it('sends true for a flagged customer even under ₹10,000', async () => {
      const spy = vi.spyOn(inspectionApi, 'submitDecision')
      const user = await setup()
      await open(user, ...HEADPHONES)
      await user.click(radio('C', 3)) // still ₹12,499.50; use partial for a small amount below
      await user.click(button(/^Partially accept/))
      const dialog = screen.getByRole('dialog', { name: 'Partially accept' })
      await user.type(within(dialog).getByLabelText(/Refund amount/), '500')
      await user.type(within(dialog).getByLabelText('Reason'), 'Scratches')
      expect(within(dialog).getByText(/Customer is flagged for review, so finance must approve/)).toBeInTheDocument()
      await user.click(within(dialog).getByRole('button', { name: 'Confirm partial accept' }))
      expect(spy.mock.calls[0][1]).toMatchObject({ refundAmountPaise: 50_000, requiresFinanceApproval: true })
    })
  })

  it('ignores a rapid second key press while saving', async () => {
    const spy = vi.spyOn(inspectionApi, 'submitDecision')
    const user = await setup()
    await open(user, ...LINEN)
    await user.keyboard('3')
    await user.keyboard('aa')
    await screen.findByRole('status')
    expect(spy).toHaveBeenCalledTimes(1)
    expect(getMockLedger()).toHaveLength(1)
  })

  it('reuses the same idempotency key when retrying after a timeout', async () => {
    const real = inspectionApi.submitDecision
    const spy = vi
      .spyOn(inspectionApi, 'submitDecision')
      .mockImplementationOnce(() => Promise.reject(new TypeError('Failed to fetch')))
      .mockImplementation(real)
    const user = await setup()
    await open(user, ...LINEN)
    await user.click(radio('C', 3))
    await user.click(button(/^Accept/))
    expect(await screen.findByRole('status')).toHaveTextContent(/Connection problem/)
    await user.click(button(/^Accept/))
    expect(await screen.findByText(/Refund of ₹949.50 paid/)).toBeInTheDocument()
    expect(spy.mock.calls[0][2]).toBe(spy.mock.calls[1][2])
    expect(getMockLedger()).toHaveLength(1)
  })

  it('uses a new key after the server definitively refuses', async () => {
    const spy = vi
      .spyOn(inspectionApi, 'submitDecision')
      .mockRejectedValueOnce(new ApiError(422, 'Pick a condition grade first.', 'GRADE_REQUIRED'))
    const user = await setup()
    await open(user, ...LINEN)
    await user.click(radio('A', 1))
    await user.click(button(/^Accept/))
    await screen.findByRole('status')
    await user.click(button(/^Accept/))
    await screen.findByText(/paid/)
    expect(spy.mock.calls[0][2]).not.toBe(spy.mock.calls[1][2])
  })

  describe('Reject (R) popup', () => {
    it('opens with the return summary and needs a reason', async () => {
      const spy = vi.spyOn(inspectionApi, 'submitDecision')
      const user = await setup()
      await open(user, ...SHORTS)
      await user.keyboard('r')

      const dialog = screen.getByRole('dialog', { name: 'Reject return' })
      expect(within(dialog).getByText('RMA-2026-00702 · Denim shorts · ₹1,499')).toBeInTheDocument()
      expect(within(dialog).getAllByRole('radio').map((r) => r.closest('div')!.textContent)).toEqual([
        'Not as described',
        'Used or worn',
        'Missing parts or tags',
        'Outside return window',
        'Other',
      ])
      expect(within(dialog).getByText(/No refund will be paid/)).toBeInTheDocument()

      await user.click(within(dialog).getByRole('button', { name: 'Reject return' }))
      expect(within(dialog).getByText(/Pick a reason/)).toBeInTheDocument()
      expect(spy).not.toHaveBeenCalled()
    })

    it('typing in the popup does not trigger shortcuts', async () => {
      const spy = vi.spyOn(inspectionApi, 'submitDecision')
      const user = await setup()
      await open(user, ...SHORTS)
      await user.click(button(/^Reject/))
      const dialog = screen.getByRole('dialog')
      await user.click(within(dialog).getByLabelText('Used or worn'))
      await user.keyboard('a') // focus is on a radio, not a text field
      expect(spy).not.toHaveBeenCalled()
      expect(screen.getByRole('dialog')).toBeInTheDocument()
    })

    it('requires a note for "Other", then rejects with reason + note', async () => {
      const spy = vi.spyOn(inspectionApi, 'submitDecision')
      const user = await setup()
      await open(user, ...SHORTS)
      await user.type(screen.getByLabelText('Inspection notes'), 'Visible wear on the hem')
      await user.click(button(/^Reject/))
      const dialog = screen.getByRole('dialog')
      // note is pre-filled from the inspection notes
      expect(within(dialog).getByLabelText(/^Note/)).toHaveValue('Visible wear on the hem')

      await user.click(within(dialog).getByLabelText('Other'))
      await user.clear(within(dialog).getByLabelText(/^Note/))
      await user.click(within(dialog).getByRole('button', { name: 'Reject return' }))
      expect(within(dialog).getByText('Add a note explaining the reason.')).toBeInTheDocument()

      await user.click(within(dialog).getByLabelText('Used or worn'))
      await user.type(within(dialog).getByLabelText(/^Note/), 'Visible wear on the hem; tags removed.')
      await user.click(within(dialog).getByRole('button', { name: 'Reject return' }))

      expect(spy).toHaveBeenCalledWith(
        'ret_00702',
        { decision: 'reject', grade: null, reason: 'used_or_worn', notes: 'Visible wear on the hem; tags removed.' },
        expect.any(String),
      )
      expect(await screen.findByRole('status')).toHaveTextContent('RMA-2026-00702 rejected.')
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      expect(getMockLedger()).toHaveLength(0)
    })

    it('Cancel closes without sending', async () => {
      const spy = vi.spyOn(inspectionApi, 'submitDecision')
      const user = await setup()
      await open(user, ...SHORTS)
      await user.click(button(/^Reject/))
      await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel' }))
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      expect(spy).not.toHaveBeenCalled()
    })
  })

  describe('Partially accept (P) popup', () => {
    it('needs a grade first', async () => {
      const user = await setup()
      await open(user, ...SHOES)
      expect(button(/^Partially accept/)).toBeDisabled()
      await user.keyboard('p')
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })

    it('matches the design and sends amount, reason, note and the flag', async () => {
      const spy = vi.spyOn(inspectionApi, 'submitDecision')
      const user = await setup()
      await open(user, ...SHOES)
      await user.keyboard('2')
      await user.keyboard('p')

      const dialog = screen.getByRole('dialog', { name: 'Partially accept' })
      expect(within(dialog).getByText('RMA-2026-00744 · Running shoes · Grade B · ₹4,299')).toBeInTheDocument()
      expect(within(dialog).getByLabelText('Refund amount (max ₹4,299)')).toHaveFocus()

      await user.type(within(dialog).getByLabelText(/Refund amount/), '3,000')
      expect(within(dialog).getByTestId('partial-preview')).toHaveTextContent('Customer gets 70% of the price₹3,000')
      expect(
        within(dialog).getByText('Under ₹10,000 and customer not flagged, so this goes straight to refund.'),
      ).toBeInTheDocument()

      await user.type(within(dialog).getByLabelText('Reason'), 'Missing parts or accessories')
      await user.type(within(dialog).getByLabelText('Note'), 'Original box and spare laces missing.')
      await user.click(within(dialog).getByRole('button', { name: 'Confirm partial accept' }))

      expect(spy).toHaveBeenCalledWith(
        'ret_00744',
        {
          decision: 'partial_accept',
          grade: 'B',
          refundAmountPaise: 300_000,
          reason: 'Missing parts or accessories',
          notes: 'Original box and spare laces missing.',
          requiresFinanceApproval: false,
        },
        expect.any(String),
      )
      expect(await screen.findByRole('status')).toHaveTextContent('Partial refund of ₹3,000 paid.')
      expect(getMockLedger()[0].amountPaise).toBe(300_000)
    })

    it('validates the amount and reason', async () => {
      const spy = vi.spyOn(inspectionApi, 'submitDecision')
      const user = await setup()
      await open(user, ...SHOES)
      await user.click(radio('C', 3)) // grade C → max 50% = ₹2,149.50
      await user.click(button(/^Partially accept/))
      const dialog = screen.getByRole('dialog')
      expect(within(dialog).getByLabelText('Refund amount (max ₹2,149.50)')).toBeInTheDocument()

      const confirm = within(dialog).getByRole('button', { name: 'Confirm partial accept' })
      await user.click(confirm)
      expect(within(dialog).getByText('Enter the refund amount.')).toBeInTheDocument()
      expect(within(dialog).getByText('Add a reason. The customer sees it.')).toBeInTheDocument()

      const amount = within(dialog).getByLabelText(/Refund amount/)
      await user.type(amount, '2150')
      await user.click(confirm)
      expect(within(dialog).getByText("Can't be more than ₹2,149.50 for grade C.")).toBeInTheDocument()

      await user.clear(amount)
      await user.type(amount, '0')
      await user.click(confirm)
      expect(within(dialog).getByText(/Enter an amount above ₹0/)).toBeInTheDocument()
      expect(spy).not.toHaveBeenCalled()
    })

    it('shows server errors inside the popup', async () => {
      vi.spyOn(inspectionApi, 'submitDecision').mockRejectedValueOnce(
        new ApiError(422, 'Enter a refund amount between ₹0.01 and the maximum.', 'INVALID_REFUND_AMOUNT'),
      )
      const user = await setup()
      await open(user, ...SHOES)
      await user.click(radio('B', 2))
      await user.click(button(/^Partially accept/))
      const dialog = screen.getByRole('dialog')
      await user.type(within(dialog).getByLabelText(/Refund amount/), '100')
      await user.type(within(dialog).getByLabelText('Reason'), 'Scuffed')
      await user.click(within(dialog).getByRole('button', { name: 'Confirm partial accept' }))
      expect(await within(dialog).findByRole('alert')).toHaveTextContent('Enter a refund amount between')
    })
  })

  it('refuses to inspect the same return twice', async () => {
    const user = await setup()
    await open(user, ...LINEN)
    await user.click(radio('C', 3))
    await user.click(button(/^Accept/))
    await screen.findByRole('status')
    await scan(user, LINEN[0])
    expect(await screen.findByRole('alert')).toHaveTextContent('already been refunded')
  })
})
