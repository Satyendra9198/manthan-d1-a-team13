import type { AuditAction, AuditActor, AuditLogEntry } from '@/api/types'
import type { BadgeProps } from '@/components/ui/badge'
import { toCsvText } from '@/lib/csv'

export const ACTOR_LABELS: Record<AuditActor, string> = {
  customer: 'Customer',
  warehouse_agent: 'Agent',
  finance_reviewer: 'Finance',
  operations_manager: 'Operations manager',
  system: 'System',
}

export const ACTOR_FILTERS: { value: AuditActor; label: string }[] = [
  { value: 'customer', label: 'Customer' },
  { value: 'warehouse_agent', label: 'Warehouse agent' },
  { value: 'finance_reviewer', label: 'Finance reviewer' },
  { value: 'operations_manager', label: 'Operations manager' },
  { value: 'system', label: 'System' },
]

export const AUDIT_ACTIONS: AuditAction[] = [
  'STATUS_CHANGED',
  'GRADED',
  'REFUND_ROUTED',
  'REFUND_APPROVED',
  'REFUND_DECLINED',
  'REFUND_ATTEMPTED',
  'FLAG_CHANGED',
  'FLAG_REVIEWED',
]

export const DATE_FILTERS = [
  { value: '1', label: 'Last 24 hours' },
  { value: '7', label: 'Last 7 days' },
  { value: '30', label: 'Last 30 days' },
  { value: '90', label: 'Last 90 days' },
  { value: 'all', label: 'All time' },
] as const

/** "Vikram · Agent", "System", "Customer". */
export function actorLabel(e: AuditLogEntry): string {
  return e.actorName ? `${e.actorName} · ${ACTOR_LABELS[e.actor]}` : ACTOR_LABELS[e.actor]
}

const state = (s: string | null) => (s ? s.replace(/_/g, ' ').toUpperCase() : '—')

/** "IN TRANSIT → RECEIVED", "— → REQUESTED". */
export const changeLabel = (e: AuditLogEntry) => `${state(e.from)} → ${state(e.to)}`

export function actionBadge(e: AuditLogEntry): BadgeProps['variant'] {
  switch (e.action) {
    case 'GRADED':
      return 'info'
    case 'REFUND_ATTEMPTED':
      return e.to === 'refunded' ? 'success' : 'danger'
    case 'REFUND_APPROVED':
      return 'success'
    case 'REFUND_DECLINED':
      return 'danger'
    case 'FLAG_CHANGED':
    case 'FLAG_REVIEWED':
      return 'warning'
    default:
      return 'default'
  }
}

export function auditCsv(entries: AuditLogEntry[]): string {
  return toCsvText([
    ['time', 'request_id', 'action', 'rma', 'customer', 'from', 'to', 'actor', 'details'],
    ...entries.map((e) => [
      e.at,
      e.requestId,
      e.action,
      e.rmaNumber ?? '',
      e.customerName ?? '',
      e.from ?? '',
      e.to ?? '',
      actorLabel(e),
      e.details,
    ]),
  ])
}
