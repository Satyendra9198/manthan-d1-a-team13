import { describe, expect, it } from 'vitest'
import { ApiError } from '../client'
import { financeMock as finance, getMockAuditLog, getNewPayments, inspectionMock, trackingMock } from './inspection.mock'

const TV = { id: 'ret_00712', key: 'rr-refund-88214', rma: 'RMA-2026-00712' }

async function code(p: Promise<unknown>) {
  try {
    await p
  } catch (e) {
    return (e as ApiError).code
  }
  return 'no error'
}

describe('finance mock', () => {
  it('lists the design queue, oldest first, totalling ₹1,17,938', async () => {
    const q = await finance.getQueue()
    expect(q.map((i) => i.rmaNumber)).toEqual([
      'RMA-2026-00698', // 1 d
      'RMA-2026-00705', // 5 h
      'RMA-2026-00716', // 3 h
      'RMA-2026-00712', // 2 h
      'RMA-2026-00720', // 1 h
    ])
    expect(q.reduce((s, i) => s + i.refundAmountPaise, 0)).toBe(11_793_800)
    expect(q.find((i) => i.rmaNumber === 'RMA-2026-00705')).toMatchObject({ grade: 'C', refundAmountPaise: 1_225_000 })
    expect(q.find((i) => i.rmaNumber === 'RMA-2026-00716')!.customer).toMatchObject({
      flaggedForReview: true,
      returnsLast30Days: 4,
    })
  })

  it('approve pays once; retries and double clicks with the same key replay', async () => {
    const [a, b] = await Promise.all([finance.approveRefund(TV.id, TV.key), finance.approveRefund(TV.id, TV.key)])
    const c = await finance.approveRefund(TV.id, TV.key)
    expect(a).toEqual(b)
    expect(c).toEqual(a)
    expect(getNewPayments()).toHaveLength(1)
    expect(getNewPayments()[0]).toMatchObject({ returnId: TV.id, amountPaise: 4_299_000, idempotencyKey: TV.key })
    expect((await finance.getQueue()).some((i) => i.returnId === TV.id)).toBe(false)
  })

  it('records the finance step in the audit log', async () => {
    await finance.approveRefund(TV.id, TV.key)
    expect(getMockAuditLog().at(-1)).toMatchObject({
      returnId: TV.id,
      actor: 'finance_reviewer',
      from: 'inspected',
      to: 'refunded',
    })
  })

  it('refuses a key that belongs to another refund', async () => {
    expect(await code(finance.approveRefund(TV.id, 'rr-refund-88231'))).toBe('IDEMPOTENCY_KEY_MISMATCH')
    expect(await code(finance.approveRefund(TV.id, ''))).toBe('IDEMPOTENCY_KEY_REQUIRED')
  })

  it('cannot decline after approving (or approve after declining)', async () => {
    await finance.approveRefund(TV.id, TV.key)
    expect(await code(finance.declineRefund(TV.id, { note: 'x' }, TV.key))).toBe('IDEMPOTENCY_KEY_REUSED')
    expect(await code(finance.declineRefund('ret_00720', { note: 'x' }, 'rr-refund-88231'))).toBe('no error')
    expect(await code(finance.approveRefund('ret_00720', 'rr-refund-88231'))).toBe('IDEMPOTENCY_KEY_REUSED')
  })

  it('refuses returns that are not waiting for approval', async () => {
    expect(await code(finance.approveRefund('ret_00731', 'k'))).toBe('NOT_AWAITING_APPROVAL')
  })

  it('decline needs a reason and closes the return as rejected', async () => {
    expect(await code(finance.declineRefund(TV.id, { note: ' ' }, TV.key))).toBe('NOTES_REQUIRED')
    const res = await finance.declineRefund(TV.id, { note: "Serial number doesn't match." }, TV.key)
    expect(res).toMatchObject({ outcome: 'declined', status: 'rejected', refund: null })
    expect(getNewPayments()).toHaveLength(0)

    const t = await trackingMock.getTracking(TV.rma)
    expect(t).toMatchObject({
      status: 'rejected',
      requiresFinanceApproval: null,
      inspection: null,
      rejection: { reason: 'declined_after_review', notes: "Serial number doesn't match." },
    })
  })

  it('the customer tracker shows the refund once finance pays', async () => {
    expect((await trackingMock.getTracking(TV.rma)).requiresFinanceApproval).toBe(true)
    await finance.approveRefund(TV.id, TV.key)
    const t = await trackingMock.getTracking(TV.rma)
    expect(t.status).toBe('refunded')
    expect(t.refund).toMatchObject({ amountPaise: 4_299_000, destination: 'UPI · priya@okhdfc' })
  })

  it('an agent decision over ₹10,000 lands in the queue with its own refund key', async () => {
    await inspectionMock.scanReturn({ qrCode: 'rr-qr-1b2c3d4e' })
    await inspectionMock.submitDecision(
      'ret_00742',
      { decision: 'accept', grade: 'A', notes: '', requiresFinanceApproval: true },
      'agent-key',
    )
    const item = (await finance.getQueue()).find((i) => i.returnId === 'ret_00742')!
    expect(item).toMatchObject({ refundAmountPaise: 2_499_900, payoutMethod: 'Store credit', inspectedBy: 'Vikram' })
    expect(item.refundKey).toMatch(/^rr-refund-\d+$/)
    expect(item.refundKey).not.toBe('agent-key')
  })

  it('agents cannot use the finance-only decline reason', async () => {
    expect(
      await code(
        inspectionMock.submitDecision(
          'ret_00731',
          // @ts-expect-error: finance-only reason
          { decision: 'reject', grade: null, reason: 'declined_after_review', notes: '' },
          'k',
        ),
      ),
    ).toBe('REASON_REQUIRED')
  })
})
