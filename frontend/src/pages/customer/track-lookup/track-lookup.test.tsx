import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/api/client'
import type { ReturnTracking } from '@/api/types'

/** The real backend needs the order email; this replaces the tracking API with one that does too. */
const getTracking = vi.fn<(rma: string, email?: string) => Promise<ReturnTracking>>()
vi.mock('@/api/tracking', () => ({
  trackingApi: { requiresEmail: true, getTracking: (rma: string, email?: string) => getTracking(rma, email) },
}))

const { routes } = await import('@/router')

const inTransit: ReturnTracking = {
  rmaNumber: 'RMA-DEMO-00011',
  qrCode: 'RMA-DEMO-00011',
  item: { name: 'Ceramic vase', pricePaise: 219_900 },
  refundMethod: 'store_credit',
  status: 'in_transit',
  history: [
    { status: 'requested', at: '2026-11-17T10:00:00Z' },
    { status: 'approved', at: '2026-11-17T10:00:00Z' },
    { status: 'in_transit', at: '2026-11-19T10:00:00Z' },
  ],
  requiresFinanceApproval: null,
  inspection: null,
  rejection: null,
  refund: null,
}

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  render(<RouterProvider router={router} />)
  return router
}

beforeEach(() => {
  getTracking.mockReset()
  sessionStorage.clear()
})

describe('Tracking with the order email', () => {
  it('/track asks for the return number and email, then shows the return', async () => {
    getTracking.mockResolvedValue(inTransit)
    const user = userEvent.setup()
    const router = renderAt('/track')

    await user.type(screen.getByLabelText('Return number'), 'rma-demo-00011')
    await user.type(screen.getByLabelText('Email'), 'farah.khan@example.com')
    await user.click(screen.getByRole('button', { name: 'Track return' }))

    expect(await screen.findByRole('heading', { name: 'RMA-DEMO-00011' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/track/RMA-DEMO-00011')
    expect(getTracking).toHaveBeenCalledWith('RMA-DEMO-00011', 'farah.khan@example.com')
  })

  it('a status link without a remembered email asks for it first', async () => {
    getTracking.mockResolvedValue(inTransit)
    const user = userEvent.setup()
    renderAt('/track/RMA-DEMO-00011')

    expect(screen.getByLabelText('Return number')).toHaveValue('RMA-DEMO-00011')
    expect(getTracking).not.toHaveBeenCalled()

    await user.type(screen.getByLabelText('Email'), 'farah.khan@example.com')
    await user.click(screen.getByRole('button', { name: 'Track return' }))

    expect(await screen.findByTestId('status-badge')).toHaveTextContent('In transit')
  })

  it('a wrong email shows the error and lets the customer try again', async () => {
    getTracking
      .mockRejectedValueOnce(new ApiError(404, "We couldn't find that return.", 'RETURN_NOT_FOUND'))
      .mockResolvedValueOnce(inTransit)
    const user = userEvent.setup()
    renderAt('/track/RMA-DEMO-00011')

    await user.type(screen.getByLabelText('Email'), 'wrong@example.com')
    await user.click(screen.getByRole('button', { name: 'Track return' }))
    expect(await screen.findByRole('alert')).toHaveTextContent("We couldn't find that return.")

    await user.type(screen.getByLabelText('Email'), 'farah.khan@example.com')
    await user.click(screen.getByRole('button', { name: 'Track return' }))
    expect(await screen.findByRole('heading', { name: 'RMA-DEMO-00011' })).toBeInTheDocument()
  })

  it('both fields are required', async () => {
    renderAt('/track')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Track return' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Enter the return number and the email.')
    expect(getTracking).not.toHaveBeenCalled()
  })
})
