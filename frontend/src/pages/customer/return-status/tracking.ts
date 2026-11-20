import type { ReturnStatus, ReturnTracking } from '@/api/types'
import type { BadgeProps } from '@/components/ui/badge'
import { GRADE_REFUND_PERCENT, REJECT_REASON_LABELS, STATUS_LABELS } from '@/domain/returns'
import { formatDate, formatDateTime, formatPaise } from '@/lib/format'

export type StepState = 'done' | 'current' | 'upcoming' | 'failed'

export type TimelineStep = {
  key: ReturnStatus | 'finance_review'
  label: string
  state: StepState
  /** "17 Nov 2026, 10:12 · label ready" for reached steps, a short explanation otherwise. */
  caption: string
}

/** Waiting for the finance team: inspected and the routing flag is set. */
export const isUnderReview = (t: ReturnTracking) =>
  t.status === 'inspected' && t.requiresFinanceApproval === true

const refundDestination = (t: ReturnTracking) =>
  t.refundMethod === 'store_credit' ? 'as store credit' : 'to your original payment method'

function reachedDetail(t: ReturnTracking, status: ReturnStatus): string | null {
  switch (status) {
    case 'approved':
      return 'label ready'
    case 'in_transit':
      return 'picked up by courier'
    case 'inspected':
      return t.inspection?.grade ? `grade ${t.inspection.grade}` : null
    case 'refunded':
      return t.refund ? formatPaise(t.refund.amountPaise) : null
    case 'rejected':
      return t.rejection ? REJECT_REASON_LABELS[t.rejection.reason].toLowerCase() : null
    default:
      return null
  }
}

function upcomingCaption(t: ReturnTracking, key: TimelineStep['key']): string {
  const expected = t.inspection?.refundAmountPaise ?? t.item.pricePaise
  switch (key) {
    case 'approved':
      return "We're checking your request"
    case 'in_transit':
      return 'Drop the parcel off with your return label'
    case 'received':
      return "We'll update you when it reaches our warehouse"
    case 'inspected':
      return 'Usually within 2 days of arrival'
    case 'finance_review':
      return 'We review some refunds before paying them. Usually within 2 business days'
    case 'refunded':
      return `${formatPaise(expected)} ${refundDestination(t)}`
    default:
      return ''
  }
}

/**
 * The customer timeline. `requiresFinanceApproval` adds a "Refund review" step between
 * Inspected and Refunded. The step never says why (e.g. a flagged account); that stays internal.
 */
export function buildTimeline(t: ReturnTracking): TimelineStep[] {
  const reachedAt = new Map(t.history.map((h) => [h.status, h.at]))
  const caption = (status: ReturnStatus) => {
    const at = reachedAt.get(status)!
    const detail = reachedDetail(t, status)
    return detail ? `${formatDateTime(at)} · ${detail}` : formatDateTime(at)
  }

  // Rejected: show what actually happened, ending in red (Inspected is folded into Rejected).
  if (t.status === 'rejected') {
    return t.history
      .filter((h) => h.status !== 'inspected')
      .map((h) => ({
        key: h.status,
        label: STATUS_LABELS[h.status],
        state: h.status === 'rejected' ? 'failed' : 'done',
        caption: caption(h.status),
      }))
  }

  const keys: TimelineStep['key'][] = ['requested', 'approved', 'in_transit', 'received', 'inspected']
  if (t.requiresFinanceApproval) keys.push('finance_review')
  keys.push('refunded')

  const steps: TimelineStep[] = keys.map((key) => {
    if (key === 'finance_review') {
      const done = t.status === 'refunded'
      return {
        key,
        label: 'Refund review',
        state: done ? 'done' : isUnderReview(t) ? 'current' : 'upcoming',
        caption: done ? 'Approved by our team' : upcomingCaption(t, key),
      }
    }
    const reached = reachedAt.has(key)
    return {
      key,
      label: STATUS_LABELS[key],
      state: reached ? 'done' : 'upcoming',
      caption: reached ? caption(key) : upcomingCaption(t, key),
    }
  })

  // The latest reached step is "current" unless the journey is finished or under review.
  if (t.status !== 'refunded' && !isUnderReview(t)) {
    const last = steps.findLastIndex((s) => s.state === 'done')
    if (last >= 0) steps[last] = { ...steps[last], state: 'current' }
  }
  return steps
}

export function statusBadge(t: ReturnTracking): { label: string; variant: BadgeProps['variant'] } {
  if (isUnderReview(t)) return { label: 'Under review', variant: 'warning' }
  switch (t.status) {
    case 'refunded':
      return { label: 'Refunded', variant: 'success' }
    case 'rejected':
      return { label: 'Rejected', variant: 'danger' }
    case 'requested':
      return { label: 'Requested', variant: 'default' }
    default:
      return { label: STATUS_LABELS[t.status], variant: 'info' }
  }
}

export function subtitle(t: ReturnTracking): string {
  if (t.status === 'rejected') return `${t.item.name} · ${formatPaise(t.item.pricePaise)}`
  const method = t.refundMethod === 'store_credit' ? 'store credit' : 'original payment'
  return `${t.item.name} · refund to ${method}`
}

const sentence = (s: string) => {
  const trimmed = s.trim().replace(/[.;\s]+$/, '')
  return trimmed ? `${trimmed[0].toLowerCase()}${trimmed.slice(1)}` : ''
}

/** Explains a reduced refund, or null when the customer got the full price. */
export function refundExplanation(t: ReturnTracking): { title: string; body: string } | null {
  const i = t.inspection
  if (!i || i.refundAmountPaise >= t.item.pricePaise) return null
  const title = `Why ${formatPaise(i.refundAmountPaise)} and not ${formatPaise(t.item.pricePaise)}?`

  if (i.decision === 'partial_accept') {
    const pct = Math.round((i.refundAmountPaise / t.item.pricePaise) * 100)
    const found = [i.partialReason, i.notes].filter(Boolean).map((x) => sentence(x!)).join('; ')
    return { title, body: `At inspection we found: ${found}. So we refunded ${pct}% of the price.` }
  }

  const pct = i.grade ? GRADE_REFUND_PERCENT[i.grade] : 0
  const found = i.notes ? `we found ${sentence(i.notes)} (condition grade ${i.grade})` : `the item was graded ${i.grade}`
  return { title, body: `At inspection ${found}, so the item was refunded at ${pct}% of its price.` }
}

export function rejectionText(t: ReturnTracking): string | null {
  if (!t.rejection) return null
  const reason = REJECT_REASON_LABELS[t.rejection.reason].toLowerCase()
  const note = t.rejection.notes.trim()
  return note ? `Reason: ${reason}. ${note.replace(/(?<![.!?])$/, '.')}` : `Reason: ${reason}.`
}

export const refundLine = (t: ReturnTracking) =>
  t.refund ? `To ${t.refund.destination} · ${formatDate(t.refund.paidAt)}` : null

/** Label (QR) is only useful before the parcel reaches the warehouse. */
export const showReturnLabel = (t: ReturnTracking) =>
  t.status === 'approved' || t.status === 'in_transit'
