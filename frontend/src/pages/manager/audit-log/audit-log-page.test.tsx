import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { auditApi } from '@/api/audit'
import { customersMock } from '@/api/mocks/dashboard.mock'
import { routes } from '@/router'

async function setup(url = '/audit') {
  const user = userEvent.setup()
  const router = createMemoryRouter(routes, { initialEntries: [url] })
  render(<RouterProvider router={router} />)
  await screen.findByTestId('audit-count')
  return { user, router }
}

const rows = () => screen.queryAllByTestId('audit-row')

afterEach(() => vi.restoreAllMocks())

describe('Audit log page', () => {
  it('lists rows newest first with the design columns', async () => {
    await setup()
    expect(screen.getByRole('heading', { level: 1, name: 'Audit log' })).toBeInTheDocument()
    expect(screen.getByText('Every state change, written in the same transaction. Read-only.')).toBeInTheDocument()
    for (const h of ['Time', 'Action', 'Change', 'Actor', 'Details', 'Request']) {
      expect(screen.getByRole('columnheader', { name: h })).toBeInTheDocument()
    }
    expect(rows().length).toBeGreaterThan(10)
    expect(screen.getByLabelText('Date')).toHaveValue('30')
    expect(screen.getByText('Signed in as').parentElement).toHaveTextContent('Signed in asOperations manager')
  })

  it('filters by RMA and keeps it in the URL', async () => {
    const { user, router } = await setup()
    await user.type(screen.getByLabelText('RMA'), 'RMA-2026-00731')
    await vi.waitFor(() => expect(rows()).toHaveLength(4))
    expect(rows()[3]).toHaveTextContent('— → REQUESTED')
    expect(rows()[3]).toHaveTextContent('Reason: Too large · 2 photos')
    expect(router.state.location.search).toBe('?rma=RMA-2026-00731')
  })

  it('opens pre-filtered from a link (e.g. View audit trail on the ledger)', async () => {
    await setup('/audit?rma=RMA-2026-00701')
    await vi.waitFor(() => expect(rows().length).toBeGreaterThan(0))
    expect(screen.getByLabelText('RMA')).toHaveValue('RMA-2026-00701')
    expect(rows().every((r) => r.textContent!.includes('RMA-2026-00701'))).toBe(true)
    expect(rows()[0]).toHaveTextContent('REFUND PENDING → REFUND FAILED')
  })

  it('filters by actor and action', async () => {
    const { user } = await setup()
    await user.selectOptions(screen.getByLabelText('Actor'), 'warehouse_agent')
    await user.selectOptions(screen.getByLabelText('Action'), 'GRADED')
    await vi.waitFor(() => expect(rows().length).toBeGreaterThan(0))
    expect(rows().every((r) => r.textContent!.includes('GRADED') && r.textContent!.includes('Vikram · Agent'))).toBe(true)
  })

  it('shows a removable customer filter with flag reviews', async () => {
    await customersMock.reviewFlag('cus_0001', { action: 'keep', note: 'Watching this one.' })
    const { user, router } = await setup('/audit?customer=cus_0001&days=all')
    await vi.waitFor(() => expect(rows()).toHaveLength(1))
    expect(rows()[0]).toHaveTextContent('FLAG_REVIEWED')
    expect(rows()[0]).toHaveTextContent('Watching this one.')
    await user.click(screen.getByRole('button', { name: /Customer: Rahul Verma/ }))
    expect(router.state.location.search).toBe('?days=all')
  })

  it('shows an empty state', async () => {
    const { user } = await setup()
    await user.type(screen.getByLabelText('RMA'), 'nothing-matches')
    expect(await screen.findByText('No audit entries match these filters.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Export CSV' })).toBeDisabled()
  })

  it('exports the filtered rows as CSV', async () => {
    const createObjectURL = vi.fn(() => 'blob:x')
    Object.assign(URL, { createObjectURL, revokeObjectURL: vi.fn() })
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const spy = vi.spyOn(auditApi, 'getEntries')
    const { user } = await setup()
    await user.click(screen.getByRole('button', { name: 'Export CSV' }))
    expect(createObjectURL).toHaveBeenCalledWith(expect.any(Blob))
    expect(spy).toHaveBeenCalled()
  })
})
