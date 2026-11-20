/**
 * Request / response payloads shared with the backend.
 * Money is always in paise (integer minor units): ₹1,899 → 189900.
 * Business rules live in src/domain/returns. The backend must enforce the same rules.
 */
import type { Grade, ProductCategory, RejectReason, ReturnStatus } from '@/domain/returns'

export type { Grade, ProductCategory, RejectReason, ReturnStatus }

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
  reason: RejectReason
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
