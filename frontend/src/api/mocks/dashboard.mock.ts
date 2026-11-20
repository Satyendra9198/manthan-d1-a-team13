import {
  avgCycleDays,
  CATEGORY_LABELS,
  countRecentReturns,
  flaggedCustomers,
  flagRaisedAt,
  isFlagActive,
  periodEndingAt,
  refundSuccessRate,
  RETURN_REASON_LABELS,
  returnRate,
  returnRateByCategory,
  topReasons,
} from '@/domain/returns'
import { ApiError } from '../client'
import type {
  CustomerProfile,
  CustomerReturnStatus,
  DashboardPeriodDays,
  DashboardResponse,
  FlagReview,
  FlagReviewRequest,
} from '../types'
import { recordAudit } from './inspection.mock'
import { generateDataset, type SyntheticDataset } from './seed/synthetic'

const LATENCY_MS = import.meta.env.MODE === 'test' ? 0 : 300
const wait = () => new Promise((r) => setTimeout(r, LATENCY_MS))

let cache: { day: string; data: SyntheticDataset } | null = null

/** Latest manager review per customer. */
let flagReviews = new Map<string, FlagReview>()

export type FlagAuditEntry = {
  type: 'FLAG_CHANGED' | 'FLAG_REVIEWED'
  customerId: string
  from: 'flagged'
  to: 'flagged' | 'cleared'
  note: string
  actor: string
  at: string
}
let flagAudit: FlagAuditEntry[] = []

/** The signed-in manager in mock mode. */
const MOCK_MANAGER = 'Operations manager'

export function resetDashboardMock() {
  flagReviews = new Map()
  flagAudit = []
}
export const getMockFlagAudit = () => [...flagAudit]

const clearedAt = (customerId: string) => {
  const r = flagReviews.get(customerId)
  return r?.action === 'clear' ? r.at : null
}

/** Seed data, regenerated once per day so "last 30 days" stays current. */
function dataset(now: Date) {
  const day = now.toISOString().slice(0, 10)
  if (cache?.day !== day) cache = { day, data: generateDataset({ now }) }
  return cache.data
}

export function buildDashboard(data: SyntheticDataset, days: DashboardPeriodDays, now: Date): DashboardResponse {
  const p = periodEndingAt(now, days)
  const names = new Map(data.customers.map((c) => [c.id, c.name]))
  const overall = returnRate(data.orders, data.returns, p)
  return {
    periodDays: days,
    generatedAt: now.toISOString(),
    orders: overall.orders,
    returnRate: overall.rate,
    avgCycleDays: avgCycleDays(data.returns, p),
    refundSuccessRate: refundSuccessRate(data.returns, p),
    byCategory: returnRateByCategory(data.orders, data.returns, p).map((c) => ({
      ...c,
      label: CATEGORY_LABELS[c.category],
    })),
    topReasons: topReasons(data.returns, p).map((r) => ({ ...r, label: RETURN_REASON_LABELS[r.reason] })),
    flaggedCustomers: flaggedCustomers(data.returns, now)
      .filter((f) =>
        isFlagActive(
          data.returns.filter((r) => r.customerId === f.customerId).map((r) => r.requestedAt),
          now,
          clearedAt(f.customerId),
        ),
      )
      .map((f) => ({ ...f, name: names.get(f.customerId) ?? f.customerId })),
  }
}

export const dashboardMock = {
  async getDashboard(days: DashboardPeriodDays): Promise<DashboardResponse> {
    await wait()
    const now = new Date()
    return buildDashboard(dataset(now), days, now)
  },
}

function statusOf(r: SyntheticDataset['returns'][number], flagged: boolean): CustomerReturnStatus {
  if (r.outcome === 'refunded') return 'refunded'
  if (r.outcome === 'rejected') return 'rejected'
  return flagged || r.refundPaise > 1_000_000 ? 'finance_review' : 'in_progress'
}

export function buildCustomerProfile(data: SyntheticDataset, customerId: string, now: Date): CustomerProfile {
  const customer = data.customers.find((c) => c.id === customerId)
  if (!customer) throw new ApiError(404, 'Customer not found.', 'CUSTOMER_NOT_FOUND')

  const all = data.returns.filter((r) => r.customerId === customerId)
  const requested = all.map((r) => r.requestedAt)
  const active = isFlagActive(requested, now, clearedAt(customerId))
  const from = now.getTime() - 30 * 86_400_000
  const recent = all
    .filter((r) => new Date(r.requestedAt).getTime() > from)
    .sort((a, b) => b.requestedAt.localeCompare(a.requestedAt))
  const rows = recent.map((r) => ({
    rmaNumber: r.rmaNumber,
    itemName: r.productName,
    reason: RETURN_REASON_LABELS[r.reason],
    grade: r.grade,
    status: statusOf(r, active),
    refundPaise: r.outcome === 'rejected' ? 0 : r.refundPaise,
    requestedAt: r.requestedAt,
  }))
  const refunded = rows.filter((r) => r.status === 'refunded')
  const pending = rows.filter((r) => r.status === 'finance_review')

  return {
    customerId,
    name: customer.name,
    email: customer.email,
    customerSince: customer.customerSince,
    flag: {
      active,
      returns30d: countRecentReturns(
        requested.map((iso) => iso.slice(0, 10)),
        now.toISOString().slice(0, 10),
      ),
      raisedAt: flagRaisedAt(requested, now),
      raisedBy: 'system',
      lastReview: flagReviews.get(customerId) ?? null,
    },
    stats: {
      refundedPaise: refunded.reduce((s, r) => s + r.refundPaise, 0),
      refundedCount: refunded.length,
      pendingFinancePaise: pending.reduce((s, r) => s + r.refundPaise, 0),
      pendingFinanceRmas: pending.map((r) => r.rmaNumber),
    },
    returns: rows,
  }
}

export const customersMock = {
  async getProfile(customerId: string): Promise<CustomerProfile> {
    await wait()
    const now = new Date()
    return structuredClone(buildCustomerProfile(dataset(now), customerId, now))
  },

  async reviewFlag(customerId: string, body: FlagReviewRequest): Promise<CustomerProfile> {
    await wait()
    const now = new Date()
    const before = buildCustomerProfile(dataset(now), customerId, now)
    if (!body.note.trim()) throw new ApiError(422, 'Add a review note.', 'NOTES_REQUIRED')
    if (!before.flag.active) {
      throw new ApiError(409, 'This customer is not flagged right now.', 'NOT_FLAGGED')
    }
    const review: FlagReview = { action: body.action, note: body.note.trim(), at: now.toISOString(), by: MOCK_MANAGER }
    flagReviews.set(customerId, review)
    recordAudit({
      action: body.action === 'clear' ? 'FLAG_CHANGED' : 'FLAG_REVIEWED',
      actor: 'operations_manager',
      rmaNumber: null,
      customerId,
      customerName: before.name,
      from: 'flagged',
      to: body.action === 'clear' ? 'cleared' : 'flagged',
      details: review.note,
      note: review.note,
    })
    flagAudit.push({
      type: body.action === 'clear' ? 'FLAG_CHANGED' : 'FLAG_REVIEWED',
      customerId,
      from: 'flagged',
      to: body.action === 'clear' ? 'cleared' : 'flagged',
      note: review.note,
      actor: MOCK_MANAGER,
      at: review.at,
    })
    return structuredClone(buildCustomerProfile(dataset(now), customerId, now))
  },
}
