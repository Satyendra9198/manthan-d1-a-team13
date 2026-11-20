import { describe, expect, it } from 'vitest'
import { ApiError } from '../client'
import { getMockAuditLog, getNewPayments, inspectionMock as api } from './inspection.mock'

const LINEN = { id: 'ret_00731', qr: 'rr-qr-7f3a9c41' }
const HEADPHONES = { id: 'ret_00742', qr: 'rr-qr-1b2c3d4e' }
const accept = { grade: 'C', decision: 'accept', notes: '', requiresFinanceApproval: false } as const

async function code(p: Promise<unknown>) {
  try {
    await p
  } catch (e) {
    return (e as ApiError).code
  }
  return 'no error'
}

describe('mock backend: duplicate-refund prevention', () => {
  it('retrying with the same idempotency key pays once and replays the answer', async () => {
    const first = await api.submitDecision(LINEN.id, accept, 'key-1')
    const retry = await api.submitDecision(LINEN.id, accept, 'key-1')
    expect(retry).toEqual(first)
    expect(getNewPayments()).toHaveLength(1)
    expect(getNewPayments()[0]).toMatchObject({ returnId: LINEN.id, amountPaise: 94_950 })
  })

  it('two parallel clicks with the same key still pay once', async () => {
    await Promise.all([
      api.submitDecision(LINEN.id, accept, 'key-1'),
      api.submitDecision(LINEN.id, accept, 'key-1'),
    ]).catch(() => {})
    expect(getNewPayments()).toHaveLength(1)
  })

  it('a second decision with a new key is refused', async () => {
    await api.submitDecision(LINEN.id, accept, 'key-1')
    expect(await code(api.submitDecision(LINEN.id, accept, 'key-2'))).toBe('RETURN_NOT_INSPECTABLE')
    expect(getNewPayments()).toHaveLength(1)
  })

  it('reusing a key for a different decision is refused', async () => {
    await api.submitDecision(LINEN.id, accept, 'key-1')
    expect(await code(api.submitDecision(LINEN.id, { ...accept, grade: 'A' }, 'key-1'))).toBe(
      'IDEMPOTENCY_KEY_REUSED',
    )
  })

  it('requires an idempotency key', async () => {
    expect(await code(api.submitDecision(LINEN.id, accept, ''))).toBe('IDEMPOTENCY_KEY_REQUIRED')
  })
})

describe('mock backend: rules', () => {
  it('pays 100% for grade B and 50% for grade C', async () => {
    const b = await api.submitDecision(LINEN.id, { ...accept, grade: 'B' }, 'k')
    expect(b.refundAmountPaise).toBe(189_900)
  })

  it('sends refunds above ₹10,000 to finance without paying', async () => {
    await api.scanReturn({ qrCode: HEADPHONES.qr })
    const res = await api.submitDecision(HEADPHONES.id, { ...accept, grade: 'A' }, 'k')
    expect(res).toMatchObject({
      outcome: 'sent_to_finance',
      status: 'inspected',
      refundAmountPaise: 2_499_900,
      requiresFinanceApproval: true,
    })
    expect(getNewPayments()).toHaveLength(0)
  })

  it('sends a flagged customer to finance even under ₹10,000', async () => {
    await api.scanReturn({ qrCode: HEADPHONES.qr }) // Rohan: 4 returns in 30 days
    const res = await api.submitDecision(
      HEADPHONES.id,
      { decision: 'partial_accept', grade: 'C', refundAmountPaise: 50_000, reason: 'Scratches', notes: '', requiresFinanceApproval: true },
      'k',
    )
    expect(res).toMatchObject({ outcome: 'sent_to_finance', requiresFinanceApproval: true })
    expect(getNewPayments()).toHaveLength(0)
  })

  it("trusts its own routing, not the client's flag", async () => {
    await api.scanReturn({ qrCode: HEADPHONES.qr })
    const res = await api.submitDecision(HEADPHONES.id, { ...accept, grade: 'A', requiresFinanceApproval: false }, 'k')
    expect(res.requiresFinanceApproval).toBe(true)
    expect(getNewPayments()).toHaveLength(0)
  })

  it('requires a grade to accept', async () => {
    // @ts-expect-error: testing what the server does with a bad payload
    expect(await code(api.submitDecision(LINEN.id, { ...accept, grade: null }, 'k1'))).toBe('GRADE_REQUIRED')
  })

  it('reject needs a valid reason, and a note when the reason is "other"', async () => {
    const reject = { decision: 'reject', grade: null, notes: '' } as const
    // @ts-expect-error: invalid reason
    expect(await code(api.submitDecision(LINEN.id, { ...reject, reason: 'nope' }, 'k1'))).toBe('REASON_REQUIRED')
    expect(await code(api.submitDecision(LINEN.id, { ...reject, reason: 'other' }, 'k2'))).toBe('NOTES_REQUIRED')
    const ok = await api.submitDecision(LINEN.id, { ...reject, reason: 'used_or_worn' }, 'k3')
    expect(ok).toMatchObject({ outcome: 'rejected', refundAmountPaise: 0, requiresFinanceApproval: false })
    expect(getMockAuditLog().at(-1)?.note).toBe('Used or worn')
  })

  describe('partial accept', () => {
    const partial = {
      decision: 'partial_accept',
      grade: 'B',
      reason: 'Missing parts or accessories',
      notes: 'Original box missing.',
      requiresFinanceApproval: false,
    } as const

    it('pays the agreed amount', async () => {
      const res = await api.submitDecision(LINEN.id, { ...partial, refundAmountPaise: 100_000 }, 'k')
      expect(res).toMatchObject({ outcome: 'refund_initiated', refundAmountPaise: 100_000 })
      expect(getNewPayments()[0].amountPaise).toBe(100_000)
    })

    it.each([0, -1, 189_901])('rejects amount %i', async (amount) => {
      expect(await code(api.submitDecision(LINEN.id, { ...partial, refundAmountPaise: amount }, 'k'))).toBe(
        'INVALID_REFUND_AMOUNT',
      )
    })

    it('caps grade C at 50% of the price', async () => {
      const c = { ...partial, grade: 'C' } as const
      expect(await code(api.submitDecision(LINEN.id, { ...c, refundAmountPaise: 94_951 }, 'k1'))).toBe(
        'INVALID_REFUND_AMOUNT',
      )
      expect((await api.submitDecision(LINEN.id, { ...c, refundAmountPaise: 94_950 }, 'k2')).refundAmountPaise).toBe(
        94_950,
      )
    })

    it('needs a reason', async () => {
      expect(
        await code(api.submitDecision(LINEN.id, { ...partial, refundAmountPaise: 100, reason: ' ' }, 'k')),
      ).toBe('REASON_REQUIRED')
    })
  })

  it("can't inspect a parcel that hasn't been scanned in", async () => {
    expect(await code(api.submitDecision(HEADPHONES.id, accept, 'k'))).toBe('RETURN_NOT_INSPECTABLE')
  })

  it('refuses to scan a return that is already refunded', async () => {
    expect(await code(api.scanReturn({ qrCode: 'rr-qr-9e8d7c6b' }))).toBe('RETURN_NOT_INSPECTABLE')
  })

  it('flags a customer with more than 3 returns in 30 days', async () => {
    const r = await api.scanReturn({ qrCode: HEADPHONES.qr })
    expect(r.customer).toMatchObject({ returnsLast30Days: 4, flaggedForReview: true })
    const l = await api.scanReturn({ qrCode: LINEN.qr })
    expect(l.customer.flaggedForReview).toBe(false)
  })
})

describe('mock backend: audit log', () => {
  it('writes every state change', async () => {
    await api.scanReturn({ qrCode: HEADPHONES.qr })
    await api.submitDecision(HEADPHONES.id, { grade: 'C', decision: 'reject', reason: 'other', notes: 'Water damage' }, 'k1')
    await api.submitDecision(LINEN.id, accept, 'k2')

    const steps = getMockAuditLog()
      .filter((e) => e.action === 'STATUS_CHANGED' || e.action === 'GRADED')
      .map((e) => `${e.returnId}:${e.from}->${e.to}`)
    expect(steps).toEqual([
      'ret_00742:in_transit->received',
      'ret_00742:received->inspected',
      'ret_00742:inspected->rejected',
      'ret_00731:received->inspected',
      'ret_00731:inspected->refunded',
    ])
  })

  it('writes nothing when a retry is replayed', async () => {
    await api.submitDecision(LINEN.id, accept, 'k')
    const before = getMockAuditLog().length
    await api.submitDecision(LINEN.id, accept, 'k')
    expect(getMockAuditLog()).toHaveLength(before)
  })
})
