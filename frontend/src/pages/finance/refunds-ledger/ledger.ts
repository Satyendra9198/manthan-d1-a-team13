import type { RefundRecord, RefundStatus } from '@/api/types'
import type { BadgeProps } from '@/components/ui/badge'
import { formatPaise } from '@/lib/format'

export const REFUND_STATUS_BADGE: Record<RefundStatus, { label: string; variant: BadgeProps['variant'] }> = {
  pending: { label: 'Pending', variant: 'warning' },
  succeeded: { label: 'Succeeded', variant: 'success' },
  failed: { label: 'Failed', variant: 'danger' },
  cancelled: { label: 'Cancelled', variant: 'default' },
}

export const failedCountLabel = (n: number) => `${n} failed refund${n === 1 ? '' : 's'}`

export function failureTitle(r: RefundRecord): string {
  return `Refund failed · ${r.rmaNumber} · ${r.itemName} · ${formatPaise(r.amountPaise)}`
}

/** "Gateway timed out on all 4 attempts (1 + 3 automatic retries). Key … has not been charged; retrying with it is safe." */
export function failureMessage(r: RefundRecord): string {
  const cause = (r.lastError ?? 'Gateway failed').replace(/\.$/, '')
  const attempts =
    r.attempts === 1 ? 'on the only attempt' : `on all ${r.attempts} attempts (1 + ${r.attempts - 1} automatic retries)`
  return `${cause} ${attempts}. Key ${r.idempotencyKey} has not been charged; retrying with it is safe.`
}
