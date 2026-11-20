import { describe, expect, it } from 'vitest'
import type { FinanceQueueItem } from '@/api/types'
import { formatWaiting, inspectedLine, payoutLine, queueFlags, queueSummary } from './finance'

const now = Date.parse('2026-11-20T12:00:00Z')
const ago = (min: number) => new Date(now - min * 60_000).toISOString()

const item: FinanceQueueItem = {
  returnId: 'ret_00712',
  rmaNumber: 'RMA-2026-00712',
  customer: { name: 'Priya Sharma', flaggedForReview: false, returnsLast30Days: 1 },
  item: { name: '55" 4K TV', pricePaise: 4_299_000 },
  decision: 'accept',
  grade: 'A',
  refundAmountPaise: 4_299_000,
  refundMethod: 'original_payment',
  payoutMethod: 'UPI',
  inspectedBy: 'Vikram',
  inspectedAt: ago(120),
  refundKey: 'rr-refund-88214',
}

describe('formatWaiting', () => {
  it.each([
    [0, '0 min'],
    [59, '59 min'],
    [60, '1 h'],
    [23 * 60 + 59, '23 h'],
    [24 * 60, '1 d'],
    [3 * 24 * 60, '3 d'],
  ])('%i minutes → %s', (min, expected) => {
    expect(formatWaiting(ago(min), now)).toBe(expected)
  })
})

describe('queueFlags', () => {
  it('none for a full grade A refund', () => {
    expect(queueFlags(item)).toEqual([])
  })
  it('grade C reduction', () => {
    expect(queueFlags({ ...item, grade: 'C' })).toEqual([{ label: 'Grade C · 50% applied', variant: 'info' }])
  })
  it('partial accept percentage', () => {
    expect(queueFlags({ ...item, decision: 'partial_accept', refundAmountPaise: 2_149_500 })[0].label).toBe(
      'Partial · 50%',
    )
  })
  it('flagged customer', () => {
    expect(
      queueFlags({ ...item, customer: { ...item.customer, flaggedForReview: true, returnsLast30Days: 4 } }),
    ).toEqual([{ label: 'Flagged · 4 returns / 30 d', variant: 'warning' }])
  })
})

it('summarises count and total in Indian grouping', () => {
  const amounts = [4_299_000, 3_299_900, 1_225_000, 1_849_900, 1_120_000]
  expect(queueSummary(amounts.map((a) => ({ ...item, refundAmountPaise: a })))).toBe('5 waiting · ₹1,17,938')
})

it('describes payout and inspection', () => {
  expect(payoutLine(item)).toBe('₹42,990 to original payment (UPI)')
  expect(payoutLine({ ...item, refundMethod: 'store_credit' })).toBe('₹42,990 as store credit')
  expect(inspectedLine(item)).toBe('Vikram (agent) · Grade A')
})
