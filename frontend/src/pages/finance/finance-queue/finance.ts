import type { FinanceQueueItem } from '@/api/types'
import type { BadgeProps } from '@/components/ui/badge'
import { GRADE_REFUND_PERCENT } from '@/domain/returns'
import { formatPaise } from '@/lib/format'

/** Time since the refund entered the queue: "35 min", "2 h", "1 d". */
export function formatWaiting(sinceIso: string, now: number = Date.now()): string {
  const minutes = Math.max(0, Math.floor((now - new Date(sinceIso).getTime()) / 60_000))
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} h`
  return `${Math.floor(hours / 24)} d`
}

export type QueueFlag = { label: string; variant: BadgeProps['variant'] }

/** Why a reviewer should look closer. Empty → "—". */
export function queueFlags(item: FinanceQueueItem): QueueFlag[] {
  const flags: QueueFlag[] = []
  if (item.decision === 'partial_accept') {
    const pct = Math.round((item.refundAmountPaise / item.item.pricePaise) * 100)
    flags.push({ label: `Partial · ${pct}%`, variant: 'info' })
  } else if (item.grade && GRADE_REFUND_PERCENT[item.grade] < 100) {
    flags.push({ label: `Grade ${item.grade} · ${GRADE_REFUND_PERCENT[item.grade]}% applied`, variant: 'info' })
  }
  if (item.customer.flaggedForReview) {
    flags.push({ label: `Flagged · ${item.customer.returnsLast30Days} returns / 30 d`, variant: 'warning' })
  }
  return flags
}

export function queueSummary(items: FinanceQueueItem[]): string {
  const total = items.reduce((sum, i) => sum + i.refundAmountPaise, 0)
  return `${items.length} waiting · ${formatPaise(total)}`
}

export function payoutLine(item: FinanceQueueItem): string {
  const amount = formatPaise(item.refundAmountPaise)
  return item.refundMethod === 'store_credit'
    ? `${amount} as store credit`
    : `${amount} to original payment (${item.payoutMethod})`
}

export function inspectedLine(item: FinanceQueueItem): string {
  const parts = [`${item.inspectedBy} (agent)`]
  if (item.grade) parts.push(`Grade ${item.grade}`)
  if (item.decision === 'partial_accept') parts.push('partial accept')
  return parts.join(' · ')
}
