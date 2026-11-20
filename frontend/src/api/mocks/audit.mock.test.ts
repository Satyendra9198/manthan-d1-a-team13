import { afterEach, describe, expect, it } from 'vitest'
import { customersMock } from './dashboard.mock'
import { setMockGatewayMode } from './gateway.mock'
import { auditMock, financeMock, getMockAuditLog, inspectionMock } from './inspection.mock'

afterEach(() => setMockGatewayMode('ok'))

const rows = (rma: string) => auditMock.getEntries({ rma, days: undefined })

describe('audit mock', () => {
  it('seeds each return history, newest first, request + approval sharing one request', async () => {
    const r = await rows('RMA-2026-00731')
    expect(r.map((e) => `${e.action} ${e.from ?? '—'}→${e.to}`)).toEqual([
      'STATUS_CHANGED in_transit→received',
      'STATUS_CHANGED approved→in_transit',
      'STATUS_CHANGED requested→approved',
      'STATUS_CHANGED —→requested',
    ])
    expect(r[3]).toMatchObject({ actor: 'customer', details: 'Reason: Too large · 2 photos' })
    expect(r[2]).toMatchObject({ actor: 'system', details: 'Eligible (Apparel) · label created' })
    expect(r[2].requestId).toBe(r[3].requestId)
    expect(r[0]).toMatchObject({ actor: 'warehouse_agent', actorName: 'Vikram' })
    expect(new Set(r.map((e) => e.requestId)).size).toBe(3)
  })

  it('an agent decision writes GRADED, REFUND_ROUTED, REFUND_ATTEMPTED and STATUS_CHANGED under one request', async () => {
    await inspectionMock.submitDecision(
      'ret_00731',
      { decision: 'accept', grade: 'C', notes: 'seam torn', requiresFinanceApproval: false },
      'k',
    )
    const fresh = getMockAuditLog()
    expect(fresh.map((e) => e.action)).toEqual(['GRADED', 'REFUND_ROUTED', 'REFUND_ATTEMPTED', 'STATUS_CHANGED'])
    expect(new Set(fresh.map((e) => e.requestId)).size).toBe(1)
    expect(fresh[0].details).toBe('Grade C · accept · ₹949.50 · seam torn')
    expect(fresh[1]).toMatchObject({ from: 'inspected', to: 'refund_pending', details: '₹949.50 ≤ ₹10,000, customer not flagged' })
    expect(fresh[2].details).toMatch(/^Key rr-refund-\d+ · success · \d+ ms$/)
  })

  it('replaying a retried decision writes nothing new', async () => {
    const body = { decision: 'accept', grade: 'C', notes: '', requiresFinanceApproval: false } as const
    await inspectionMock.submitDecision('ret_00731', body, 'same')
    const n = getMockAuditLog().length
    await inspectionMock.submitDecision('ret_00731', body, 'same')
    expect(getMockAuditLog()).toHaveLength(n)
  })

  it('records every gateway attempt when it is down', async () => {
    setMockGatewayMode('timeout')
    await financeMock.approveRefund('ret_00712', 'rr-refund-88214')
    const fresh = getMockAuditLog()
    expect(fresh.map((e) => `${e.action}:${e.to}`)).toEqual([
      'REFUND_APPROVED:refund_pending',
      'REFUND_ATTEMPTED:refund_pending',
      'REFUND_ATTEMPTED:refund_pending',
      'REFUND_ATTEMPTED:refund_pending',
      'REFUND_ATTEMPTED:refund_failed',
    ])
    expect(fresh[4].details).toBe('Key rr-refund-88214 · timeout after 5000 ms · attempt 4 of 4')
    expect(fresh[0]).toMatchObject({ actor: 'finance_reviewer', actorName: 'Asha' })
  })

  it('includes flag reviews', async () => {
    await customersMock.reviewFlag('cus_0001', { action: 'clear', note: 'No abuse pattern.' })
    const [row] = await auditMock.getEntries({ customer: 'cus_0001', action: 'FLAG_CHANGED' })
    expect(row).toMatchObject({
      actor: 'operations_manager',
      customerName: 'Rahul Verma',
      from: 'flagged',
      to: 'cleared',
      details: 'No abuse pattern.',
      rmaNumber: null,
    })
  })

  it('filters by actor, action, RMA (partial, any case) and date', async () => {
    const byActor = await auditMock.getEntries({ actor: 'customer' })
    expect(byActor.length).toBeGreaterThan(0)
    expect(byActor.every((e) => e.actor === 'customer')).toBe(true)
    expect((await auditMock.getEntries({ action: 'GRADED' })).every((e) => e.action === 'GRADED')).toBe(true)
    expect((await auditMock.getEntries({ rma: 'rma-2026-0073' })).every((e) => e.rmaNumber?.includes('RMA-2026-0073'))).toBe(true)
    const all = await auditMock.getEntries({})
    const lastDay = await auditMock.getEntries({ days: 1 })
    expect(lastDay.length).toBeLessThan(all.length)
    expect(lastDay.every((e) => Date.now() - new Date(e.at).getTime() <= 86_400_000)).toBe(true)
  })

  it('never exposes internal fields', async () => {
    const [row] = await auditMock.getEntries({})
    expect(row).not.toHaveProperty('returnId')
    expect(row).not.toHaveProperty('note')
  })
})
