import { describe, expect, it } from 'vitest'
import {
  avgCycleDays,
  flaggedCustomers,
  periodEndingAt,
  refundSuccessRate,
  returnRate,
  returnRateByCategory,
  topReasons,
  type AnalyticsOrder,
  type AnalyticsReturn,
} from './analytics'

const now = new Date('2026-11-20T12:00:00Z')
const p30 = periodEndingAt(now, 30)
const daysAgo = (d: number) => new Date(now.getTime() - d * 86_400_000).toISOString()

const order = (id: string, category: AnalyticsOrder['category'], ago = 5, customerId = 'c1'): AnalyticsOrder => ({
  id,
  customerId,
  category,
  orderedAt: daysAgo(ago),
})
const ret = (o: Partial<AnalyticsReturn> & { orderId: string }): AnalyticsReturn => ({
  id: `r_${o.orderId}`,
  customerId: 'c1',
  category: 'apparel',
  reason: 'wrong_size',
  requestedAt: daysAgo(3),
  closedAt: daysAgo(1),
  outcome: 'refunded',
  refundPaise: 100_00,
  gatewayAttempts: [true],
  ...o,
})

describe('returnRate', () => {
  it('counts orders with a return, within the period only', () => {
    const orders = [order('o1', 'apparel'), order('o2', 'apparel'), order('o3', 'apparel', 31)]
    const r = returnRate(orders, [ret({ orderId: 'o1' }), ret({ orderId: 'o3' })], p30)
    expect(r).toEqual({ orders: 2, rate: 0.5 })
  })
  it('is null with no orders', () => {
    expect(returnRate([], [], p30).rate).toBeNull()
  })
})

describe('returnRateByCategory', () => {
  it('marks innerwear and perishables n/a (null), not 0%', () => {
    const rates = returnRateByCategory([order('o1', 'innerwear'), order('o2', 'home')], [], p30)
    expect(rates.find((c) => c.category === 'innerwear')!.rate).toBeNull()
    expect(rates.find((c) => c.category === 'perishables')!.rate).toBeNull()
    expect(rates.find((c) => c.category === 'home')!.rate).toBe(0)
    expect(rates).toHaveLength(6)
  })
})

describe('topReasons', () => {
  it('sorts by count and computes shares', () => {
    const r = topReasons(
      [
        ret({ orderId: 'a', reason: 'defective' }),
        ret({ orderId: 'b', reason: 'wrong_size' }),
        ret({ orderId: 'c', reason: 'wrong_size' }),
        ret({ orderId: 'd', reason: 'wrong_size', requestedAt: daysAgo(40) }),
      ],
      p30,
    )
    expect(r.map((x) => [x.reason, x.returns])).toEqual([
      ['wrong_size', 2],
      ['defective', 1],
    ])
    expect(r[0].share).toBeCloseTo(2 / 3)
  })
})

describe('avgCycleDays and refundSuccessRate', () => {
  const returns = [
    ret({ orderId: 'a', requestedAt: daysAgo(6), closedAt: daysAgo(2), gatewayAttempts: [false, true] }),
    ret({ orderId: 'b', requestedAt: daysAgo(3), closedAt: daysAgo(1), gatewayAttempts: [true] }),
    ret({ orderId: 'c', closedAt: null, outcome: 'open', gatewayAttempts: [] }),
  ]
  it('averages request → close for closed returns only', () => {
    expect(avgCycleDays(returns, p30)).toBeCloseTo(3)
    expect(avgCycleDays([], p30)).toBeNull()
  })
  it('is successful attempts over all attempts', () => {
    expect(refundSuccessRate(returns, p30)).toBeCloseTo(2 / 3)
  })
})

describe('flaggedCustomers (> 3 returns in 30 days)', () => {
  const many = (customerId: string, n: number, ago = 2) =>
    Array.from({ length: n }, (_, i) =>
      ret({ orderId: `${customerId}-${i}`, customerId, requestedAt: daysAgo(ago + i), refundPaise: 1_000_00 }),
    )
  it('3 is not flagged, 4 is; sums refunded value; latest return date', () => {
    const f = flaggedCustomers([...many('three', 3), ...many('four', 4)], now)
    expect(f.map((x) => x.customerId)).toEqual(['four'])
    expect(f[0]).toMatchObject({ returns30d: 4, refundedPaise: 4_000_00, lastReturnAt: daysAgo(2) })
  })
  it('ignores returns older than 30 days', () => {
    expect(flaggedCustomers(many('old', 4, 28), now)).toEqual([])
  })
  it('does not count rejected returns as refunded value', () => {
    const list = many('x', 4)
    list[0] = { ...list[0], outcome: 'rejected', refundPaise: 0 }
    expect(flaggedCustomers(list, now)[0].refundedPaise).toBe(3_000_00)
  })
})
