import { describe, expect, it } from 'vitest'
import type { ReturnTracking } from '@/api/types'
import {
  buildTimeline,
  refundExplanation,
  rejectionText,
  showReturnLabel,
  statusBadge,
  subtitle,
} from './tracking'

const IST = (s: string) => new Date(`${s}:00+05:30`).toISOString()

const base: ReturnTracking = {
  rmaNumber: 'RMA-2026-00731',
  qrCode: 'rr-qr-7f3a9c41',
  item: { name: 'Linen shirt', pricePaise: 189_900 },
  refundMethod: 'original_payment',
  status: 'in_transit',
  history: [
    { status: 'requested', at: IST('2026-11-17T10:12') },
    { status: 'approved', at: IST('2026-11-17T10:12') },
    { status: 'in_transit', at: IST('2026-11-19T16:40') },
  ],
  requiresFinanceApproval: null,
  inspection: null,
  rejection: null,
  refund: null,
}

const reachedThroughInspection = [
  ...base.history,
  { status: 'received', at: IST('2026-11-20T14:58') },
  { status: 'inspected', at: IST('2026-11-20T15:31') },
] as ReturnTracking['history']

const summary = (t: ReturnTracking) => buildTimeline(t).map((s) => `${s.label}:${s.state}`)

describe('buildTimeline', () => {
  it('in transit: matches the design', () => {
    const steps = buildTimeline(base)
    expect(steps.map((s) => [s.label, s.state, s.caption])).toEqual([
      ['Requested', 'done', '17 Nov 2026, 10:12'],
      ['Approved', 'done', '17 Nov 2026, 10:12 · label ready'],
      ['In transit', 'current', '19 Nov 2026, 16:40 · picked up by courier'],
      ['Received', 'upcoming', "We'll update you when it reaches our warehouse"],
      ['Inspected', 'upcoming', 'Usually within 2 days of arrival'],
      ['Refunded', 'upcoming', '₹1,899 to your original payment method'],
    ])
  })

  it('refunded: every step done, with grade and amount', () => {
    const t: ReturnTracking = {
      ...base,
      status: 'refunded',
      history: [...reachedThroughInspection, { status: 'refunded', at: IST('2026-11-20T15:32') }],
      requiresFinanceApproval: false,
      inspection: { decision: 'accept', grade: 'C', notes: 'Torn seam', partialReason: null, refundAmountPaise: 94_950 },
      refund: { amountPaise: 94_950, paidAt: IST('2026-11-20T15:32'), destination: 'UPI · ananya@okbank', reference: 'GW-7781-2026' },
    }
    const steps = buildTimeline(t)
    expect(steps.every((s) => s.state === 'done')).toBe(true)
    expect(steps.find((s) => s.key === 'inspected')!.caption).toBe('20 Nov 2026, 15:31 · grade C')
    expect(steps.at(-1)!.caption).toBe('20 Nov 2026, 15:32 · ₹949.50')
    expect(steps.some((s) => s.key === 'finance_review')).toBe(false)
  })

  describe('requiresFinanceApproval flag', () => {
    const inspected: ReturnTracking = {
      ...base,
      status: 'inspected',
      history: reachedThroughInspection,
      inspection: { decision: 'accept', grade: 'A', notes: '', partialReason: null, refundAmountPaise: 1_899_900 },
    }

    it('true → adds a current "Refund review" step and an "Under review" badge', () => {
      const t = { ...inspected, requiresFinanceApproval: true }
      expect(summary(t)).toEqual([
        'Requested:done',
        'Approved:done',
        'In transit:done',
        'Received:done',
        'Inspected:done',
        'Refund review:current',
        'Refunded:upcoming',
      ])
      expect(statusBadge(t)).toEqual({ label: 'Under review', variant: 'warning' })
      expect(buildTimeline(t).at(-1)!.caption).toBe('₹18,999 to your original payment method')
    })

    it('true and later refunded → review step shown as done', () => {
      const t: ReturnTracking = {
        ...inspected,
        requiresFinanceApproval: true,
        status: 'refunded',
        history: [...reachedThroughInspection, { status: 'refunded', at: IST('2026-11-21T11:00') }],
        refund: { amountPaise: 1_899_900, paidAt: IST('2026-11-21T11:00'), destination: 'Card', reference: 'GW-1' },
      }
      expect(buildTimeline(t).find((s) => s.key === 'finance_review')).toMatchObject({
        state: 'done',
        caption: 'Approved by our team',
      })
    })

    it('false → no review step; Inspected is current while the refund is paid', () => {
      const t = { ...inspected, requiresFinanceApproval: false }
      expect(summary(t)).toEqual([
        'Requested:done',
        'Approved:done',
        'In transit:done',
        'Received:done',
        'Inspected:current',
        'Refunded:upcoming',
      ])
      expect(statusBadge(t).label).toBe('Inspected')
    })

    it('never tells the customer why the review happens', () => {
      const t = { ...inspected, requiresFinanceApproval: true }
      const text = buildTimeline(t).map((s) => s.caption).join(' ')
      expect(text).not.toMatch(/flag|10,000|suspicious/i)
    })
  })

  it('rejected: ends in red and skips Inspected (matches the design)', () => {
    const t: ReturnTracking = {
      ...base,
      status: 'rejected',
      history: [...reachedThroughInspection, { status: 'rejected', at: IST('2026-11-20T15:31') }],
      rejection: { reason: 'used_or_worn', notes: 'Visible wear on the hem; tags removed.', at: IST('2026-11-20T15:31') },
    }
    expect(summary(t)).toEqual([
      'Requested:done',
      'Approved:done',
      'In transit:done',
      'Received:done',
      'Rejected:failed',
    ])
    expect(buildTimeline(t).at(-1)!.caption).toBe('20 Nov 2026, 15:31 · used or worn')
    expect(rejectionText(t)).toBe('Reason: used or worn. Visible wear on the hem; tags removed.')
    expect(subtitle(t)).toBe('Linen shirt · ₹1,899')
    expect(statusBadge(t).variant).toBe('danger')
  })

  it('a request rejected by eligibility only shows Requested → Rejected', () => {
    const t: ReturnTracking = {
      ...base,
      status: 'rejected',
      history: [base.history[0], { status: 'rejected', at: IST('2026-11-17T10:12') }],
      rejection: { reason: 'outside_return_window', notes: '', at: IST('2026-11-17T10:12') },
    }
    expect(summary(t)).toEqual(['Requested:done', 'Rejected:failed'])
    expect(rejectionText(t)).toBe('Reason: outside return window.')
  })
})

describe('refundExplanation', () => {
  it('explains a grade C refund', () => {
    expect(
      refundExplanation({
        ...base,
        inspection: { decision: 'accept', grade: 'C', notes: 'Torn seam', partialReason: null, refundAmountPaise: 94_950 },
      }),
    ).toEqual({
      title: 'Why ₹949.50 and not ₹1,899?',
      body: 'At inspection we found torn seam (condition grade C), so the item was refunded at 50% of its price.',
    })
  })

  it('explains a partial refund', () => {
    const r = refundExplanation({
      ...base,
      item: { name: 'Running shoes', pricePaise: 429_900 },
      inspection: {
        decision: 'partial_accept',
        grade: 'B',
        notes: 'Original box and spare laces missing.',
        partialReason: 'Missing parts or accessories',
        refundAmountPaise: 300_000,
      },
    })
    expect(r).toEqual({
      title: 'Why ₹3,000 and not ₹4,299?',
      body: 'At inspection we found: missing parts or accessories; original box and spare laces missing. So we refunded 70% of the price.',
    })
  })

  it('is null for a full refund', () => {
    expect(
      refundExplanation({
        ...base,
        inspection: { decision: 'accept', grade: 'A', notes: '', partialReason: null, refundAmountPaise: 189_900 },
      }),
    ).toBeNull()
  })
})

it('shows the return label only before the parcel arrives', () => {
  expect(showReturnLabel({ ...base, status: 'approved' })).toBe(true)
  expect(showReturnLabel(base)).toBe(true)
  expect(showReturnLabel({ ...base, status: 'received' })).toBe(false)
})

it('subtitle mentions the refund method', () => {
  expect(subtitle(base)).toBe('Linen shirt · refund to original payment')
  expect(subtitle({ ...base, refundMethod: 'store_credit' })).toBe('Linen shirt · refund to store credit')
})
