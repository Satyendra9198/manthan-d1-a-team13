/**
 * Manager dashboard metrics. Pure functions over orders / returns, so the same code can
 * run in the backend, the mock and the tests.
 */
import { countRecentReturns, FLAG_MAX_RETURNS } from './flagging'
import { daysBetween } from './dates'
import { RETURN_WINDOW_DAYS, type ProductCategory, PRODUCT_CATEGORIES } from './eligibility'

export const RETURN_REASONS = [
  'wrong_size',
  'damaged_in_transit',
  'not_as_described',
  'changed_mind',
  'defective',
] as const

export type ReturnReasonCode = (typeof RETURN_REASONS)[number]

export const RETURN_REASON_LABELS: Record<ReturnReasonCode, string> = {
  wrong_size: 'Wrong size',
  damaged_in_transit: 'Damaged in transit',
  not_as_described: 'Not as described',
  changed_mind: 'Changed mind',
  defective: 'Defective',
}

export type AnalyticsOrder = {
  id: string
  customerId: string
  category: ProductCategory
  orderedAt: string
}

export type AnalyticsReturn = {
  id: string
  orderId: string
  customerId: string
  category: ProductCategory
  reason: ReturnReasonCode
  requestedAt: string
  /** When it reached refunded or rejected; null while open. */
  closedAt: string | null
  outcome: 'refunded' | 'rejected' | 'open'
  refundPaise: number
  /** Each gateway attempt: true = succeeded. Empty for store credit / rejected. */
  gatewayAttempts: boolean[]
}

const inWindow = (iso: string, from: number, to: number) => {
  const t = new Date(iso).getTime()
  return t > from && t <= to
}

export type Period = { from: number; to: number }

export function periodEndingAt(now: Date, days: number): Period {
  return { from: now.getTime() - days * 86_400_000, to: now.getTime() }
}

const ratio = (n: number, d: number) => (d === 0 ? null : n / d)

/** Share of orders placed in the period that have a return. */
export function returnRate(orders: AnalyticsOrder[], returns: AnalyticsReturn[], p: Period) {
  const inPeriod = orders.filter((o) => inWindow(o.orderedAt, p.from, p.to))
  const returned = new Set(returns.map((r) => r.orderId))
  return { orders: inPeriod.length, rate: ratio(inPeriod.filter((o) => returned.has(o.id)).length, inPeriod.length) }
}

export type CategoryRate = { category: ProductCategory; orders: number; returns: number; rate: number | null }

/** Per category; non-returnable categories have rate null ("n/a"), not 0. */
export function returnRateByCategory(
  orders: AnalyticsOrder[],
  returns: AnalyticsReturn[],
  p: Period,
): CategoryRate[] {
  const returned = new Set(returns.map((r) => r.orderId))
  return PRODUCT_CATEGORIES.map((category) => {
    const inCat = orders.filter((o) => o.category === category && inWindow(o.orderedAt, p.from, p.to))
    const n = inCat.filter((o) => returned.has(o.id)).length
    return {
      category,
      orders: inCat.length,
      returns: n,
      rate: RETURN_WINDOW_DAYS[category] === null ? null : (ratio(n, inCat.length) ?? 0),
    }
  })
}

export type ReasonShare = { reason: ReturnReasonCode; returns: number; share: number }

/** Reasons for returns requested in the period, largest first. */
export function topReasons(returns: AnalyticsReturn[], p: Period, limit = 5): ReasonShare[] {
  const inPeriod = returns.filter((r) => inWindow(r.requestedAt, p.from, p.to))
  if (!inPeriod.length) return []
  return RETURN_REASONS.map((reason) => {
    const n = inPeriod.filter((r) => r.reason === reason).length
    return { reason, returns: n, share: n / inPeriod.length }
  })
    .filter((r) => r.returns > 0)
    .sort((a, b) => b.returns - a.returns)
    .slice(0, limit)
}

/** Mean days from request to refund or reject, for returns closed in the period. */
export function avgCycleDays(returns: AnalyticsReturn[], p: Period): number | null {
  const closed = returns.filter((r) => r.closedAt && inWindow(r.closedAt, p.from, p.to))
  if (!closed.length) return null
  const total = closed.reduce(
    (s, r) => s + (new Date(r.closedAt!).getTime() - new Date(r.requestedAt).getTime()) / 86_400_000,
    0,
  )
  return total / closed.length
}

/** Successful gateway attempts / all gateway attempts, for returns closed in the period. */
export function refundSuccessRate(returns: AnalyticsReturn[], p: Period): number | null {
  const attempts = returns
    .filter((r) => r.closedAt && inWindow(r.closedAt, p.from, p.to))
    .flatMap((r) => r.gatewayAttempts)
  return ratio(attempts.filter(Boolean).length, attempts.length)
}

export type FlaggedCustomer = {
  customerId: string
  returns30d: number
  refundedPaise: number
  lastReturnAt: string
}

/** Customers with more than 3 returns requested in the 30 days before `now` (fixed rule, not the filter). */
export function flaggedCustomers(returns: AnalyticsReturn[], now: Date): FlaggedCustomer[] {
  const today = now.toISOString().slice(0, 10)
  const byCustomer = new Map<string, AnalyticsReturn[]>()
  for (const r of returns) {
    const list = byCustomer.get(r.customerId) ?? []
    list.push(r)
    byCustomer.set(r.customerId, list)
  }
  const out: FlaggedCustomer[] = []
  for (const [customerId, list] of byCustomer) {
    const count = countRecentReturns(list.map((r) => r.requestedAt.slice(0, 10)), today)
    if (count <= FLAG_MAX_RETURNS) continue
    const recent = list.filter((r) => {
      const ago = daysBetween(r.requestedAt.slice(0, 10), today)
      return ago >= 0 && ago < 30
    })
    out.push({
      customerId,
      returns30d: count,
      refundedPaise: recent.filter((r) => r.outcome === 'refunded').reduce((s, r) => s + r.refundPaise, 0),
      lastReturnAt: recent.map((r) => r.requestedAt).sort().at(-1)!,
    })
  }
  return out.sort((a, b) => b.returns30d - a.returns30d || b.refundedPaise - a.refundedPaise)
}
