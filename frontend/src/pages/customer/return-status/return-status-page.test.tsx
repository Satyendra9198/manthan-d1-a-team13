import { render, screen, within } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { inspectionMock } from '@/api/mocks/inspection.mock'
import { routes } from '@/router'

function renderTrack(rma: string) {
  const router = createMemoryRouter(routes, { initialEntries: [`/track/${rma}`] })
  render(<RouterProvider router={router} />)
}

const steps = () =>
  screen.getAllByTestId('timeline-step').map((li) => `${li.querySelector('p')!.firstChild!.textContent}:${li.dataset.state}`)

describe('Customer return status', () => {
  it('in transit: timeline and QR return label, no staff sidebar', async () => {
    renderTrack('RMA-2026-00742')
    expect(await screen.findByRole('heading', { name: 'RMA-2026-00742' })).toBeInTheDocument()
    expect(screen.getByTestId('status-badge')).toHaveTextContent('In transit')
    expect(screen.getByText('Wireless headphones · refund to store credit')).toBeInTheDocument()
    expect(steps()).toEqual([
      'Requested:done',
      'Approved:done',
      'In transit:current',
      'Received:upcoming',
      'Inspected:upcoming',
      'Refunded:upcoming',
    ])
    const label = screen.getByRole('region', { name: 'Your return label' })
    expect(within(label).getByTitle('QR code rr-qr-1b2c3d4e')).toBeInTheDocument()
    expect(within(label).getByRole('button', { name: 'Download label' })).toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Main' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Need help?' })).toHaveAttribute('href', expect.stringMatching(/^mailto:/))
  })

  it('refunded at grade C after the agent accepts: amount, reason, reference', async () => {
    await inspectionMock.submitDecision(
      'ret_00731',
      { decision: 'accept', grade: 'C', notes: 'A torn seam', requiresFinanceApproval: false },
      'k1',
    )
    renderTrack('RMA-2026-00731')
    expect(await screen.findByText('₹949.50 refunded')).toBeInTheDocument()
    expect(screen.getByTestId('status-badge')).toHaveTextContent('Refunded')
    expect(screen.getByText(/^To UPI · ananya@okbank · \d+ \w+ 2026$/)).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Why ₹949.50 and not ₹1,899?' })).toBeInTheDocument()
    expect(screen.getByText(/we found a torn seam \(condition grade C\)/)).toBeInTheDocument()
    expect(screen.getByText(/Reference: GW-\d{4}-2026/)).toBeInTheDocument()
    expect(steps().every((s) => s.endsWith(':done'))).toBe(true)
    expect(screen.queryByRole('region', { name: 'Your return label' })).not.toBeInTheDocument()
  })

  it('requiresFinanceApproval = true: "Under review" with a Refund review step', async () => {
    renderTrack('RMA-2026-00720')
    expect(await screen.findByText('Your refund is being reviewed')).toBeInTheDocument()
    expect(screen.getByTestId('status-badge')).toHaveTextContent('Under review')
    expect(steps()).toContain('Refund review:current')
    expect(screen.queryByText(/flag/i)).not.toBeInTheDocument()
  })

  it('a flagged customer sent to finance by the agent sees the review step', async () => {
    await inspectionMock.scanReturn({ qrCode: 'rr-qr-1b2c3d4e' })
    const res = await inspectionMock.submitDecision(
      'ret_00742',
      { decision: 'partial_accept', grade: 'C', refundAmountPaise: 50_000, reason: 'Scratches', notes: '', requiresFinanceApproval: true },
      'k1',
    )
    expect(res.requiresFinanceApproval).toBe(true)
    renderTrack('RMA-2026-00742')
    expect(await screen.findByText(/approved ₹500/)).toBeInTheDocument()
    expect(steps()).toContain('Refund review:current')
    expect(document.body).not.toHaveTextContent(/flagged/i)
  })

  it('rejected: reason, no refund, contact support, red final step', async () => {
    renderTrack('RMA-2026-00655')
    expect(await screen.findByText("We couldn't accept this return")).toBeInTheDocument()
    expect(screen.getByText('Reason: used or worn. Visible wear on the hem; tags removed.')).toBeInTheDocument()
    expect(screen.getByText('No refund has been paid.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Contact support' })).toHaveAttribute(
      'href',
      expect.stringContaining('RMA-2026-00655'),
    )
    expect(steps()).toEqual(['Requested:done', 'Approved:done', 'In transit:done', 'Received:done', 'Rejected:failed'])
  })

  it('shows a friendly error for an unknown RMA', async () => {
    renderTrack('RMA-0000')
    expect(await screen.findByRole('alert')).toHaveTextContent("We couldn't find return RMA-0000.")
  })
})
