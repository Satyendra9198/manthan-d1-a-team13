import { describe, expect, it } from 'vitest'
import type { AuditLogEntry } from '@/api/types'
import { actionBadge, actorLabel, auditCsv, changeLabel } from './audit'

const e: AuditLogEntry = {
  id: 'a1',
  at: '2026-11-20T10:02:00.000Z',
  requestId: 'req 51ab',
  action: 'GRADED',
  actor: 'warehouse_agent',
  actorName: 'Vikram',
  rmaNumber: 'RMA-2026-00731',
  customerId: 'cus_4412',
  customerName: 'Ananya Rao',
  from: 'received',
  to: 'inspected',
  details: 'Grade C · accept · ₹949.50 · seam torn',
}

describe('audit display', () => {
  it('matches the design', () => {
    expect(changeLabel(e)).toBe('RECEIVED → INSPECTED')
    expect(changeLabel({ ...e, from: null, to: 'requested' })).toBe('— → REQUESTED')
    expect(changeLabel({ ...e, from: 'refund_pending', to: 'refunded' })).toBe('REFUND PENDING → REFUNDED')
    expect(actorLabel(e)).toBe('Vikram · Agent')
    expect(actorLabel({ ...e, actor: 'system', actorName: null })).toBe('System')
  })

  it('colours actions by meaning', () => {
    expect(actionBadge(e)).toBe('info')
    expect(actionBadge({ ...e, action: 'REFUND_ATTEMPTED', to: 'refunded' })).toBe('success')
    expect(actionBadge({ ...e, action: 'REFUND_ATTEMPTED', to: 'refund_pending' })).toBe('danger')
    expect(actionBadge({ ...e, action: 'STATUS_CHANGED' })).toBe('default')
  })

  it('exports CSV safely', () => {
    const csv = auditCsv([{ ...e, details: '=cmd|calc' }])
    expect(csv.split('\n')[0]).toBe('time,request_id,action,rma,customer,from,to,actor,details')
    expect(csv).toContain("'=cmd|calc")
  })
})
