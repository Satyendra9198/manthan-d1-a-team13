import type { DashboardResponse } from '@/api/types'
import { csvCell } from '@/lib/csv'
import { formatDate, formatPaise } from '@/lib/format'

export const formatPercent = (rate: number | null, digits = 1) =>
  rate === null ? '—' : `${(rate * 100).toFixed(digits)}%`

export const formatDays = (days: number | null) => (days === null ? '—' : `${days.toFixed(1)} days`)

/** Returnable categories by rate (highest first), then the "n/a" ones. */
export function sortedCategories(d: DashboardResponse) {
  return [...d.byCategory].sort((a, b) => {
    if (a.rate === null || b.rate === null) return a.rate === null ? (b.rate === null ? 0 : 1) : -1
    return b.rate - a.rate
  })
}

export { csvCell } from '@/lib/csv'

export function toCsv(d: DashboardResponse): string {
  const rows: (string | number)[][] = [
    ['section', 'name', 'value', 'detail'],
    ['summary', 'period_days', d.periodDays, ''],
    ['summary', 'orders', d.orders, ''],
    ['summary', 'return_rate', formatPercent(d.returnRate), ''],
    ['summary', 'avg_cycle_days', d.avgCycleDays?.toFixed(1) ?? '', 'request to refund or reject'],
    ['summary', 'refund_success_rate', formatPercent(d.refundSuccessRate), 'mock gateway'],
    ['summary', 'flagged_customers', d.flaggedCustomers.length, '> 3 returns in 30 days'],
    ...sortedCategories(d).map((c) => [
      'return_rate_by_category',
      c.label,
      c.rate === null ? 'n/a' : formatPercent(c.rate),
      `${c.returns} of ${c.orders} orders`,
    ]),
    ...d.topReasons.map((r) => ['top_reasons', r.label, formatPercent(r.share, 0), `${r.returns} returns`]),
    ...d.flaggedCustomers.map((f) => [
      'flagged_customers',
      f.name,
      f.returns30d,
      `refunded ${formatPaise(f.refundedPaise)}; last return ${formatDate(f.lastReturnAt)}`,
    ]),
  ]
  return rows.map((r) => r.map(csvCell).join(',')).join('\n')
}
