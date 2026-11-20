import type { CustomerProfile, CustomerReturnStatus } from '@/api/types'
import type { BadgeProps } from '@/components/ui/badge'
import { formatDate, formatPaise } from '@/lib/format'

export const RETURN_STATUS_BADGE: Record<CustomerReturnStatus, { label: string; variant: BadgeProps['variant'] }> = {
  in_progress: { label: 'In progress', variant: 'info' },
  finance_review: { label: 'Finance review', variant: 'warning' },
  refunded: { label: 'Refunded', variant: 'success' },
  rejected: { label: 'Rejected', variant: 'danger' },
}

const monthYear = new Intl.DateTimeFormat('en-IN', { month: 'long', year: 'numeric', timeZone: 'Asia/Kolkata' })

export const customerLine = (p: CustomerProfile) =>
  `${p.email} · customer since ${monthYear.format(new Date(p.customerSince))}`

export function flagBadge(p: CustomerProfile): { label: string; variant: BadgeProps['variant'] } {
  if (p.flag.active) return { label: `Flagged · ${p.flag.returns30d} returns in 30 days`, variant: 'warning' }
  if (p.flag.lastReview?.action === 'clear') return { label: 'Flag cleared', variant: 'success' }
  return { label: 'Not flagged', variant: 'default' }
}

export const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? '' : 's'}`

export function statTiles(p: CustomerProfile) {
  return [
    { label: 'Returns in 30 days', value: String(p.flag.returns30d), caption: 'flag rule: more than 3' },
    { label: 'Refunded', value: formatPaise(p.stats.refundedPaise), caption: plural(p.stats.refundedCount, 'return') },
    {
      label: 'Pending finance',
      value: formatPaise(p.stats.pendingFinancePaise),
      caption: pendingCaption(p.stats.pendingFinanceRmas),
    },
    {
      label: 'Flag raised',
      value: p.flag.raisedAt ? formatDate(p.flag.raisedAt) : '—',
      caption: p.flag.raisedAt ? `by ${p.flag.raisedBy}` : 'not over the limit',
    },
  ]
}

/** "RMA-2026-00716", "RMA-2026-00716 +1 more", or "nothing waiting". */
export function pendingCaption(rmas: string[]): string {
  if (!rmas.length) return 'nothing waiting'
  return rmas.length === 1 ? rmas[0] : `${rmas[0]} +${rmas.length - 1} more`
}
