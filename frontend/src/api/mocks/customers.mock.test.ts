import { describe, expect, it } from 'vitest'
import { buildCustomerProfile, customersMock, dashboardMock, getMockFlagAudit } from './dashboard.mock'
import { generateDataset } from './seed/synthetic'

const RAHUL = 'cus_0001'

describe('customer profile', () => {
  it('describes a flagged customer', async () => {
    const p = await customersMock.getProfile(RAHUL)
    expect(p).toMatchObject({ name: 'Rahul Verma', email: 'rahul.verma@example.com' })
    expect(p.flag.active).toBe(true)
    expect(p.flag.returns30d).toBeGreaterThan(3)
    expect(p.flag.raisedAt).not.toBeNull()
    expect(p.returns.length).toBeGreaterThanOrEqual(4)
    // newest first
    expect([...p.returns].map((r) => r.requestedAt)).toEqual([...p.returns].map((r) => r.requestedAt).sort().reverse())
    // while flagged, open returns go to finance whatever the amount
    expect(p.returns.filter((r) => r.status === 'in_progress')).toEqual([])
    expect(p.stats.refundedCount).toBe(p.returns.filter((r) => r.status === 'refunded').length)
  })

  it('404s for an unknown customer', async () => {
    await expect(customersMock.getProfile('nope')).rejects.toMatchObject({ code: 'CUSTOMER_NOT_FOUND' })
  })
})

describe('flag review', () => {
  it('requires a note', async () => {
    await expect(customersMock.reviewFlag(RAHUL, { action: 'clear', note: ' ' })).rejects.toMatchObject({
      code: 'NOTES_REQUIRED',
    })
    expect(getMockFlagAudit()).toEqual([])
  })

  it('keep → FLAG_REVIEWED, stays flagged', async () => {
    const p = await customersMock.reviewFlag(RAHUL, { action: 'keep', note: 'Pattern looks like abuse.' })
    expect(p.flag.active).toBe(true)
    expect(p.flag.lastReview).toMatchObject({ action: 'keep', note: 'Pattern looks like abuse.' })
    expect(getMockFlagAudit()).toEqual([
      expect.objectContaining({ type: 'FLAG_REVIEWED', customerId: RAHUL, to: 'flagged' }),
    ])
  })

  it('clear → FLAG_CHANGED, unflagged, and off the dashboard list', async () => {
    const p = await customersMock.reviewFlag(RAHUL, { action: 'clear', note: 'Size issues and a real defect.' })
    expect(p.flag.active).toBe(false)
    expect(getMockFlagAudit()).toEqual([
      expect.objectContaining({ type: 'FLAG_CHANGED', from: 'flagged', to: 'cleared', actor: 'Operations manager' }),
    ])
    const dash = await dashboardMock.getDashboard(30)
    expect(dash.flaggedCustomers.some((f) => f.customerId === RAHUL)).toBe(false)
    // cleared customers' open returns no longer forced to finance
    expect(p.returns.every((r) => r.status !== 'finance_review' || r.refundPaise > 1_000_000)).toBe(true)
  })

  it('cannot review a customer who is not flagged', async () => {
    await customersMock.reviewFlag(RAHUL, { action: 'clear', note: 'ok' })
    await expect(customersMock.reviewFlag(RAHUL, { action: 'keep', note: 'again' })).rejects.toMatchObject({
      code: 'NOT_FLAGGED',
    })
  })

  it('a new return after clearing raises the flag again', async () => {
    await customersMock.reviewFlag(RAHUL, { action: 'clear', note: 'ok' })
    const later = new Date(Date.now() + 60_000)
    const data = generateDataset({ now: new Date() })
    const template = data.returns.find((r) => r.customerId === RAHUL)!
    data.returns.push({ ...template, id: 'ret_new', rmaNumber: 'RMA-2026-29999', requestedAt: later.toISOString(), closedAt: null, outcome: 'open', grade: null })
    expect(buildCustomerProfile(data, RAHUL, later).flag.active).toBe(true)
  })
})
