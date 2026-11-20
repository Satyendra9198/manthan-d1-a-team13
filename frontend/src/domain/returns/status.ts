/**
 * Return state machine.
 *
 *   requested ─► approved ─► in_transit ─► received ─► inspected ─► refunded
 *       │                                                   │
 *       └──────────────► rejected ◄─────────────────────────┘
 *
 * Every transition must be written to the audit log.
 */

export const RETURN_STATUSES = [
  'requested',
  'approved',
  'in_transit',
  'received',
  'inspected',
  'refunded',
  'rejected',
] as const

export type ReturnStatus = (typeof RETURN_STATUSES)[number]

export const STATUS_LABELS: Record<ReturnStatus, string> = {
  requested: 'Requested',
  approved: 'Approved',
  in_transit: 'In transit',
  received: 'Received',
  inspected: 'Inspected',
  refunded: 'Refunded',
  rejected: 'Rejected',
}

const TRANSITIONS: Record<ReturnStatus, readonly ReturnStatus[]> = {
  requested: ['approved', 'rejected'], // eligibility engine
  approved: ['in_transit'], // carrier pickup
  in_transit: ['received'], // warehouse scan
  received: ['inspected'], // agent decision
  inspected: ['refunded', 'rejected'], // refund paid / rejected (by agent or finance)
  refunded: [],
  rejected: [],
}

export function canTransition(from: ReturnStatus, to: ReturnStatus): boolean {
  return TRANSITIONS[from].includes(to)
}

export class InvalidTransitionError extends Error {
  constructor(from: ReturnStatus, to: ReturnStatus) {
    super(`Can't move a return from ${STATUS_LABELS[from]} to ${STATUS_LABELS[to]}.`)
    this.name = 'InvalidTransitionError'
  }
}

export function assertTransition(from: ReturnStatus, to: ReturnStatus): void {
  if (!canTransition(from, to)) throw new InvalidTransitionError(from, to)
}

/**
 * Why a warehouse scan can't start an inspection, or null if it can.
 * - approved: allowed. The parcel arrived but the carrier scan was missed, so we record
 *   approved → in_transit → received instead of leaving the parcel stuck.
 * - in_transit / received: allowed.
 */
export function scanBlockReason(status: ReturnStatus): string | null {
  switch (status) {
    case 'requested':
      return "This return hasn't been approved yet, so it can't be received. Set the parcel aside."
    case 'inspected':
      return 'This return has already been inspected. Each return can only be inspected once.'
    case 'refunded':
      return 'This return has already been refunded. Nothing more to do.'
    case 'rejected':
      return 'This return was rejected. Nothing more to do.'
    default:
      return null
  }
}

/** The customer-facing timeline, in order. Rejected replaces Refunded when it happens. */
export function timelineFor(status: ReturnStatus): ReturnStatus[] {
  const happy: ReturnStatus[] = ['requested', 'approved', 'in_transit', 'received', 'inspected']
  return [...happy, status === 'rejected' ? 'rejected' : 'refunded']
}
