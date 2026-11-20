import { afterEach, describe, expect, it } from 'vitest'
import { getGatewayCharges, setMockGatewayMode } from './gateway.mock'
import {
  financeMock,
  getMockJournal,
  getMockRefunds,
  getNewPayments,
  inspectionMock,
  refundsMock,
  trackingMock,
} from './inspection.mock'

const FAILED = { id: 'rfd_ret_00701', key: 'rr-refund-88175', rma: 'RMA-2026-00701' }
const balanced = () => {
  const j = getMockJournal()
  return j.reduce((s, e) => s + e.debitPaise, 0) === j.reduce((s, e) => s + e.creditPaise, 0)
}

afterEach(() => setMockGatewayMode('ok'))

describe('seed data', () => {
  it('has pending, succeeded (UPI + store credit) and one failed refund', async () => {
    const refunds = await refundsMock.getRefunds()
    const by = (rma: string) => refunds.find((r) => r.rmaNumber === rma)!
    expect(by('RMA-2026-00701')).toMatchObject({ status: 'failed', attempts: 4, method: 'Card', idempotencyKey: FAILED.key })
    expect(by('RMA-2026-00690')).toMatchObject({ status: 'succeeded', method: 'UPI' })
    expect(by('RMA-2026-00668')).toMatchObject({ status: 'succeeded', method: 'Store credit', amountPaise: 235_000 })
    expect(by('RMA-2026-00712')).toMatchObject({ status: 'pending', attempts: 0, idempotencyKey: 'rr-refund-88214' })
  })

  it('ledger only has rows for succeeded refunds, and balances', async () => {
    const ledger = await refundsMock.getLedger()
    expect(new Set(ledger.entries.map((e) => e.rmaNumber))).toEqual(new Set(['RMA-2026-00690', 'RMA-2026-00668']))
    expect(ledger.balanced).toBe(true)
    expect(ledger.entries.find((e) => e.rmaNumber === 'RMA-2026-00668' && e.creditPaise)).toMatchObject({
      account: 'store_credit_liability',
      type: 'STORE_CREDIT',
    })
  })
})

describe('gateway payments', () => {
  it('a paid refund posts one balanced journal: Dr returns_expense, Cr gateway_clearing', async () => {
    await inspectionMock.submitDecision(
      'ret_00731',
      { decision: 'accept', grade: 'C', notes: '', requiresFinanceApproval: false },
      'k',
    )
    const rows = getMockJournal().filter((e) => e.rmaNumber === 'RMA-2026-00731')
    expect(rows).toEqual([
      expect.objectContaining({ account: 'returns_expense', debitPaise: 94_950, creditPaise: 0, type: 'REFUND' }),
      expect.objectContaining({ account: 'gateway_clearing', debitPaise: 0, creditPaise: 94_950, type: 'REFUND' }),
    ])
    expect(rows[0].journalId).toBe(rows[1].journalId)
    expect(balanced()).toBe(true)
  })

  it('gateway down: 1 + 3 retries with the same key, then failed; no ledger rows, return not refunded', async () => {
    setMockGatewayMode('timeout')
    const res = await financeMock.approveRefund('ret_00712', 'rr-refund-88214')
    expect(res).toMatchObject({ outcome: 'payment_failed', status: 'inspected', refund: null })

    const refund = getMockRefunds().find((r) => r.returnId === 'ret_00712')!
    expect(refund).toMatchObject({ status: 'failed', attempts: 4, lastError: 'Gateway timed out' })
    expect(getMockJournal().some((e) => e.rmaNumber === 'RMA-2026-00712')).toBe(false)
    expect(getGatewayCharges()).toHaveLength(0)
    // It leaves the finance queue (already approved) and waits for a retry on the ledger page.
    expect((await financeMock.getQueue()).some((i) => i.returnId === 'ret_00712')).toBe(false)
  })

  it('retry with the same key pays once, posts the ledger and marks the return refunded', async () => {
    const r = await refundsMock.retryRefund(FAILED.id, FAILED.key)
    expect(r).toMatchObject({ status: 'succeeded', attempts: 5 })
    expect(r.reference).toMatch(/^GW-\d+-2026$/)

    // Second click / retry after timeout: same answer, no new charge or ledger rows.
    const again = await refundsMock.retryRefund(FAILED.id, FAILED.key)
    expect(again).toEqual(r)
    expect(getGatewayCharges()).toHaveLength(1)
    expect(getMockJournal().filter((e) => e.rmaNumber === FAILED.rma)).toHaveLength(2)
    expect(getNewPayments()).toHaveLength(1)
    expect(balanced()).toBe(true)
    expect((await trackingMock.getTracking(FAILED.rma)).status).toBe('refunded')
  })

  it('retry while the gateway is still down stays failed and adds 4 attempts', async () => {
    setMockGatewayMode('timeout')
    const r = await refundsMock.retryRefund(FAILED.id, FAILED.key)
    expect(r).toMatchObject({ status: 'failed', attempts: 8 })
    expect(getMockJournal().some((e) => e.rmaNumber === FAILED.rma)).toBe(false)
  })

  it('retry needs the refund key and a failed refund', async () => {
    await expect(refundsMock.retryRefund(FAILED.id, 'rr-refund-1')).rejects.toMatchObject({
      code: 'IDEMPOTENCY_KEY_MISMATCH',
    })
    await expect(refundsMock.retryRefund('rfd_ret_00712', 'rr-refund-88214')).rejects.toMatchObject({
      code: 'NOT_RETRYABLE',
    })
  })

  it('gateway is idempotent by key: a charge that went through is never repeated', async () => {
    // Gateway succeeds, response lost, client retries → same charge returned.
    await refundsMock.retryRefund(FAILED.id, FAILED.key)
    const charges = getGatewayCharges()
    await refundsMock.retryRefund(FAILED.id, FAILED.key)
    expect(getGatewayCharges()).toEqual(charges)
  })

  it('declined refunds are cancelled and never posted', async () => {
    await financeMock.declineRefund('ret_00720', { note: 'Wrong item' }, 'rr-refund-88231')
    expect(getMockRefunds().find((r) => r.returnId === 'ret_00720')!.status).toBe('cancelled')
    expect(getMockJournal().some((e) => e.rmaNumber === 'RMA-2026-00720')).toBe(false)
  })
})
