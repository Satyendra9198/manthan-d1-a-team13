import { describe, expect, it } from 'vitest'
import { PRODUCT_CATEGORIES, RETURN_WINDOW_DAYS } from '@/domain/returns'
import { generateDataset } from './synthetic'

const now = new Date('2026-11-20T12:00:00Z')
const data = generateDataset({ now })

describe('seed script', () => {
  it('matches the brief: 2,000 orders, 40 products in 6 categories, 500 customers', () => {
    expect(data.orders).toHaveLength(2000)
    expect(data.products).toHaveLength(40)
    expect(new Set(data.products.map((p) => p.category))).toEqual(new Set(PRODUCT_CATEGORIES))
    expect(data.customers).toHaveLength(500)
    expect(new Set(data.customers.map((c) => c.id)).size).toBe(500)
  })

  it('is deterministic for the same seed and date', () => {
    expect(generateDataset({ now })).toEqual(data)
    expect(generateDataset({ now, seed: 7 }).returns).not.toEqual(data.returns)
  })

  it('never returns non-returnable categories or requests returns in the future', () => {
    for (const r of data.returns) {
      expect(RETURN_WINDOW_DAYS[r.category]).not.toBeNull()
      expect(new Date(r.requestedAt).getTime()).toBeLessThanOrEqual(now.getTime())
      if (r.closedAt) expect(r.closedAt >= r.requestedAt).toBe(true)
    }
  })

  it('every return points to a real order of the same customer', () => {
    const orders = new Map(data.orders.map((o) => [o.id, o]))
    for (const r of data.returns) expect(orders.get(r.orderId)?.customerId).toBe(r.customerId)
  })

  it('prices are positive whole rupees and refunds never exceed the price', () => {
    const orders = new Map(data.orders.map((o) => [o.id, o]))
    for (const p of data.products) expect(p.pricePaise % 100).toBe(0)
    for (const r of data.returns) expect(r.refundPaise).toBeLessThanOrEqual(orders.get(r.orderId)!.pricePaise)
  })
})
