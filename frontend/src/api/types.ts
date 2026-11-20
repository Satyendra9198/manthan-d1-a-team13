/**
 * Request / response payloads shared with the backend.
 * Money is always in paise (integer minor units): ₹1,899 → 189900.
 * Business rules live in src/domain/returns. The backend must enforce the same rules.
 */
import type { AgentRejectReason, Grade, ProductCategory, RejectReason, ReturnStatus } from '@/domain/returns'

export type { AgentRejectReason, Grade, ProductCategory, RejectReason, ReturnStatus }

export type RefundMethod = 'original_payment' | 'store_credit'

export type Decision = 'accept' | 'partial_accept' | 'reject'

export type CustomerPhoto = {
  url: string
  alt: string
}

/** POST /returns/scan: look up by QR and mark the parcel received. */
export type ScanReturnRequest = {
  qrCode: string
}

export type ReturnDetails = {
  id: string
  /** Human-facing number, e.g. "RMA-2026-00731". */
  rmaNumber: string
  qrCode: string
  item: {
    name: string
    sku: string
    category: ProductCategory
    pricePaise: number
  }
  customer: {
    id: string
    name: string
    /** Returns requested in the last 30 days, including this one. */
    returnsLast30Days: number
    /** More than 3 returns in 30 days. */
    flaggedForReview: boolean
  }
  /** Reason the customer picked, as display text, e.g. "Too large". */
  reason: string
  refundMethod: RefundMethod
  status: ReturnStatus
  /** Status before the most recent change, so the UI can show "In transit → Received". */
  previousStatus: ReturnStatus | null
  photos: CustomerPhoto[]
  customerNote: string | null
}

export type GradeOption = {
  grade: Grade
  /** e.g. "Like new". */
  label: string
  /** 0–100. */
  refundPercent: number
}

/** GET /inspection/config */
export type InspectionConfig = {
  grades: GradeOption[]
  /** Refunds strictly above this need finance approval. */
  financeApprovalAbovePaise: number
}

/** GET /inspection/stats/today */
export type InspectionStats = {
  inspectedToday: number
}

/**
 * POST /returns/:returnId/inspection
 * Header `Idempotency-Key: <uuid>` is required. Retrying with the same key returns
 * the original response and never pays twice; reusing a key with a different body → 422.
 *
 * `requiresFinanceApproval` is the client's routing flag: true when the refund is above
 * ₹10,000 or the customer is flagged. The server recomputes it and its answer wins.
 */
export type AcceptRequest = {
  decision: 'accept'
  grade: Grade
  notes: string
  requiresFinanceApproval: boolean
}

export type PartialAcceptRequest = {
  decision: 'partial_accept'
  grade: Grade
  /** 1 … grade refund (grade C → 50% of price). */
  refundAmountPaise: number
  /** Shown to the customer, e.g. "Missing parts or accessories". */
  reason: string
  notes: string
  requiresFinanceApproval: boolean
}

export type RejectRequest = {
  decision: 'reject'
  grade: Grade | null
  /** Label is shown to the customer on their status page. */
  reason: AgentRejectReason
  /** Required when reason is "other". */
  notes: string
}

export type InspectionDecisionRequest = AcceptRequest | PartialAcceptRequest | RejectRequest

export type InspectionOutcome = 'refund_initiated' | 'sent_to_finance' | 'rejected'

export type InspectionDecisionResponse = {
  inspectionId: string
  returnId: string
  decision: Decision
  grade: Grade | null
  /** Final amount calculated by the server. 0 when rejected. */
  refundAmountPaise: number
  /** Server's routing decision (above ₹10,000 or flagged customer). false when rejected. */
  requiresFinanceApproval: boolean
  outcome: InspectionOutcome
  /** Return status after the decision: refunded, inspected (awaiting finance) or rejected. */
  status: ReturnStatus
  /** ISO timestamp. */
  decidedAt: string
}

/** Error codes the inspection endpoints can return. */
export type InspectionErrorCode =
  | 'RETURN_NOT_FOUND'
  | 'RETURN_NOT_INSPECTABLE' // 409: wrong status (already inspected, refunded, …)
  | 'GRADE_REQUIRED' // 422
  | 'NOTES_REQUIRED' // 422
  | 'REASON_REQUIRED' // 422
  | 'INVALID_REFUND_AMOUNT' // 422: partial amount ≤ 0 or above the grade refund
  | 'IDEMPOTENCY_KEY_REQUIRED' // 400
  | 'IDEMPOTENCY_KEY_REUSED' // 422: same key, different body
  | 'DUPLICATE_REFUND' // 409: a refund already exists for this return

/**
 * GET /returns/:rmaNumber/tracking: what the customer sees on their status page.
 * Must never expose internal data (customer flags, audit log, agent identity).
 * In production this needs the order email / a signed link, not just the RMA number.
 */
export type ReturnTracking = {
  rmaNumber: string
  /** Encoded in the return label QR code. */
  qrCode: string
  item: { name: string; pricePaise: number }
  refundMethod: RefundMethod
  status: ReturnStatus
  /** Every status reached so far, oldest first. ISO timestamps. */
  history: { status: ReturnStatus; at: string }[]
  /**
   * The routing flag from inspection: true → refund waits for a finance review before payment.
   * null until the return is inspected (and for rejected returns).
   */
  requiresFinanceApproval: boolean | null
  inspection: {
    decision: Exclude<Decision, 'reject'>
    grade: Grade | null
    /** Inspector's note, shown to the customer when the refund is reduced. */
    notes: string
    /** Partial accept reason shown to the customer. */
    partialReason: string | null
    refundAmountPaise: number
  } | null
  rejection: { reason: RejectReason; notes: string; at: string } | null
  refund: {
    amountPaise: number
    paidAt: string
    /** e.g. "UPI · ananya@okbank" or "Store credit". */
    destination: string
    /** Gateway reference, e.g. "GW-7781-2026". */
    reference: string
  } | null
}

/** GET /finance/queue: refunds held by requiresFinanceApproval, oldest first. */
export type FinanceQueueItem = {
  returnId: string
  rmaNumber: string
  customer: {
    name: string
    flaggedForReview: boolean
    returnsLast30Days: number
  }
  item: { name: string; pricePaise: number }
  decision: 'accept' | 'partial_accept'
  grade: Grade | null
  /** Amount to pay after grading / partial accept. */
  refundAmountPaise: number
  refundMethod: RefundMethod
  /** e.g. "UPI", "Visa", "Store credit". */
  payoutMethod: string
  inspectedBy: string
  /** When it entered the queue (ISO). */
  inspectedAt: string
  /**
   * The refund's idempotency key, assigned when it was queued. Send it as `Idempotency-Key`
   * on approve / decline: clicking twice or retrying after a timeout can never pay twice.
   */
  refundKey: string
}

/** POST /finance/refunds/:returnId/decline */
export type DeclineRefundRequest = {
  /** Shown to the customer on their status page. */
  note: string
}

/** Response for approve (POST /finance/refunds/:returnId/approve) and decline. */
export type FinanceDecisionResponse = {
  returnId: string
  /** payment_failed: approved, but the gateway failed after all retries (see Refunds & ledger). */
  outcome: 'paid' | 'payment_failed' | 'declined'
  status: ReturnStatus
  refund: { amountPaise: number; reference: string; paidAt: string } | null
  decidedAt: string
}

export type RefundStatus = 'pending' | 'succeeded' | 'failed' | 'cancelled'

/** GET /refunds: one row per refund (at most one per return). Newest first. */
export type RefundRecord = {
  refundId: string
  returnId: string
  rmaNumber: string
  itemName: string
  amountPaise: number
  /** "UPI", "Card" or "Store credit". */
  method: string
  /**
   * pending: waiting for finance approval or payment · succeeded: paid and posted to the ledger ·
   * failed: gateway failed on every attempt (nothing charged; retry with the same key) ·
   * cancelled: finance declined it.
   */
  status: RefundStatus
  /** Gateway attempts so far (1 + up to 3 automatic retries per run). */
  attempts: number
  /** Assigned when the refund is created; every attempt and retry reuses it. */
  idempotencyKey: string
  /** Gateway reference once paid. */
  reference: string | null
  lastError: string | null
  createdAt: string
  updatedAt: string
}

export type LedgerAccount = 'returns_expense' | 'gateway_clearing' | 'store_credit_liability'

/** One line of a double-entry journal. Insert-only: never updated or deleted. */
export type LedgerEntry = {
  journalId: string
  rmaNumber: string
  account: LedgerAccount
  debitPaise: number
  creditPaise: number
  type: 'REFUND' | 'STORE_CREDIT'
  postedAt: string
}

/** GET /ledger */
export type LedgerResponse = {
  /** Newest first. */
  entries: LedgerEntry[]
  totals: { debitPaise: number; creditPaise: number }
  balanced: boolean
}

export type DashboardPeriodDays = 7 | 14 | 30

/** GET /dashboard?days=30: manager overview. Rates are 0–1; null = no data. */
export type DashboardResponse = {
  periodDays: DashboardPeriodDays
  generatedAt: string
  /** Orders placed in the period. */
  orders: number
  returnRate: number | null
  /** Mean days from request to refund or reject. */
  avgCycleDays: number | null
  /** Successful gateway attempts / all attempts. */
  refundSuccessRate: number | null
  byCategory: {
    category: ProductCategory
    label: string
    orders: number
    returns: number
    /** null for categories that can't be returned (innerwear, perishables). */
    rate: number | null
  }[]
  topReasons: { reason: string; label: string; returns: number; share: number }[]
  /** Fixed rule: more than 3 returns in the last 30 days, whatever the period. */
  flaggedCustomers: {
    customerId: string
    name: string
    returns30d: number
    refundedPaise: number
    lastReturnAt: string
  }[]
}

export type CustomerReturnStatus = 'in_progress' | 'finance_review' | 'refunded' | 'rejected'

export type FlagReview = {
  action: 'keep' | 'clear'
  note: string
  at: string
  by: string
}

/** GET /customers/:customerId/returns-profile */
export type CustomerProfile = {
  customerId: string
  name: string
  email: string
  customerSince: string
  flag: {
    /** Flag in force right now (over the limit and not cleared since the last return). */
    active: boolean
    returns30d: number
    /** The request that took the count over 3; null if never over. */
    raisedAt: string | null
    raisedBy: 'system'
    lastReview: FlagReview | null
  }
  stats: {
    refundedPaise: number
    refundedCount: number
    pendingFinancePaise: number
    pendingFinanceRmas: string[]
  }
  /** Returns requested in the last 30 days, newest first. */
  returns: {
    rmaNumber: string
    itemName: string
    reason: string
    grade: Grade | null
    status: CustomerReturnStatus
    refundPaise: number
    requestedAt: string
  }[]
}

/**
 * POST /customers/:customerId/flag-review
 * keep → FLAG_REVIEWED audit row; clear → FLAG_CHANGED audit row and the flag is off until a
 * new return takes the 30-day count over 3 again.
 */
export type FlagReviewRequest = { action: 'keep' | 'clear'; note: string }

export type AuditAction =
  | 'STATUS_CHANGED'
  | 'GRADED'
  | 'REFUND_ROUTED'
  | 'REFUND_APPROVED'
  | 'REFUND_DECLINED'
  | 'REFUND_ATTEMPTED'
  | 'FLAG_CHANGED'
  | 'FLAG_REVIEWED'

export type AuditActor = 'customer' | 'warehouse_agent' | 'finance_reviewer' | 'operations_manager' | 'system'

/** One audit row. Insert-only: rows are never edited or deleted. */
export type AuditLogEntry = {
  id: string
  at: string
  /** Rows written by the same API call share a request ID. */
  requestId: string
  action: AuditAction
  actor: AuditActor
  /** Person's name when known, e.g. "Vikram". */
  actorName: string | null
  rmaNumber: string | null
  customerId: string | null
  customerName: string | null
  /** Lower-case state names, e.g. "in_transit" → "received". from is null for creation. */
  from: string | null
  to: string | null
  details: string
}

/** GET /audit?rma=&actor=&action=&days=&customer= → AuditLogEntry[] newest first. */
export type AuditQuery = {
  /** Case-insensitive "contains" match on the RMA number. */
  rma?: string
  actor?: AuditActor
  action?: AuditAction
  /** Only rows from the last N days; omit for all time. */
  days?: number
  customer?: string
}
