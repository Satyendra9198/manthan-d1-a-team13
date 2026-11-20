import { describe, expect, it } from 'vitest'
import type { InspectionDecisionResponse } from '@/api/types'
import { formatPaise } from '@/lib/format'
import { decisionMessage, routingHint } from './inspection'

describe('formatPaise', () => {
  it('drops decimals for whole rupees and keeps two otherwise', () => {
    expect(formatPaise(189_900)).toBe('₹1,899')
    expect(formatPaise(94_950)).toBe('₹949.50')
    expect(formatPaise(1_000_000)).toBe('₹10,000')
  })
})

describe('routingHint (finance approves refunds above ₹10,000 or for flagged customers)', () => {
  it.each([
    [94_950, false, 'panel', 'Under ₹10,000, so this goes straight to refund after you accept.'],
    [1_000_000, false, 'panel', 'Exactly ₹10,000, so this goes straight to refund after you accept.'],
    [1_000_001, false, 'panel', 'Over ₹10,000, so finance must approve this refund after you accept.'],
    [94_950, true, 'panel', 'Customer is flagged for review, so finance must approve this refund after you accept.'],
    [1_000_001, true, 'dialog', 'Over ₹10,000 and customer flagged, so finance must approve this refund.'],
    [300_000, false, 'dialog', 'Under ₹10,000 and customer not flagged, so this goes straight to refund.'],
  ] as const)('%i paise, flagged=%s, %s', (amount, flagged, ctx, expected) => {
    expect(routingHint(amount, 1_000_000, flagged, ctx)).toBe(expected)
  })
})

describe('decisionMessage', () => {
  const base: InspectionDecisionResponse = {
    inspectionId: 'i', returnId: 'r', decision: 'accept', grade: 'C', status: 'refunded', requiresFinanceApproval: false,
    refundAmountPaise: 94_950, outcome: 'refund_initiated', decidedAt: '',
  }
  it('describes each outcome', () => {
    expect(decisionMessage('RMA-1', base)).toBe('RMA-1 saved. Refund of ₹949.50 paid.')
    expect(decisionMessage('RMA-1', { ...base, outcome: 'sent_to_finance' })).toMatch(/finance queue/)
    expect(decisionMessage('RMA-1', { ...base, outcome: 'rejected' })).toMatch(/^RMA-1 rejected/)
    expect(decisionMessage('RMA-1', { ...base, decision: 'partial_accept' })).toBe('RMA-1 saved. Partial refund of ₹949.50 paid.')
  })
})
