/**
 * Pure mapping between the backend's payloads and the screen types. No network here, so every
 * rule is unit-tested in mappers.test.ts.
 *
 * The screens were designed around one item per return; the backend allows several. A return is
 * shown as one combined item (name "Linen shirt + 1 more", price = total), and a decision is spread
 * across its lines.
 */
import type {
  CustomerPhoto,
  FinanceDecisionResponse,
  FinanceQueueItem,
  InspectionDecisionRequest,
  InspectionDecisionResponse,
  ProductCategory,
  RefundMethod,
  RejectReason,
  ReturnDetails,
  ReturnStatus,
  ReturnTracking,
} from '../types'
import { GRADE_REFUND_PERCENT, REJECT_REASON_LABELS, type Grade } from '@/domain/returns'
import type {
  BackendCategory,
  BackendFinanceQueueItem,
  BackendInspectedItem,
  BackendInspectionRequest,
  BackendInspectionResult,
  BackendRefundMethod,
  BackendRejectRequest,
  BackendRejectionReason,
  BackendReturnReason,
  BackendReturnStatus,
  BackendReviewDecision,
  BackendTimelineStep,
  BackendTracking,
  BackendWarehouseReturn,
} from './backend-types'

// ─── Shared vocabularies ──────────────────────────────────────────────

/** Review and payout steps are internal; the screens show them as "inspected". */
export const RETURN_STATUS: Record<BackendReturnStatus, ReturnStatus> = {
  REQUESTED: 'requested',
  APPROVED: 'approved',
  IN_TRANSIT: 'in_transit',
  RECEIVED: 'received',
  INSPECTED: 'inspected',
  AWAITING_FINANCE_APPROVAL: 'inspected',
  REFUND_PENDING: 'inspected',
  REFUNDED: 'refunded',
  REJECTED: 'rejected',
  CANCELLED: 'rejected',
}

const TIMELINE_STATUS: Record<BackendTimelineStep, ReturnStatus> = {
  REQUESTED: 'requested',
  APPROVED: 'approved',
  IN_TRANSIT: 'in_transit',
  RECEIVED: 'received',
  INSPECTED: 'inspected',
  REFUNDED: 'refunded',
  REJECTED: 'rejected',
  CANCELLED: 'rejected',
}

/** The screens have no beauty category; it is not displayed, so it maps to the nearest one. */
const CATEGORY: Record<BackendCategory, ProductCategory> = {
  ELECTRONICS: 'electronics',
  APPAREL: 'apparel',
  HOME_KITCHEN: 'home',
  BEAUTY: 'home',
  INNERWEAR: 'innerwear',
  PERISHABLES: 'perishables',
}

export const RETURN_REASON_LABELS: Record<BackendReturnReason, string> = {
  TOO_BIG: 'Too big',
  TOO_SMALL: 'Too small',
  DAMAGED_ON_ARRIVAL: 'Damaged on arrival',
  NOT_AS_DESCRIBED: 'Not as described',
  CHANGED_MY_MIND: 'Changed my mind',
}

const REJECT_REASON: Record<BackendRejectionReason, RejectReason> = {
  NOT_AS_DESCRIBED: 'not_as_described',
  USED_OR_WORN: 'used_or_worn',
  MISSING_PARTS_OR_TAGS: 'missing_parts_or_tags',
  OUTSIDE_RETURN_WINDOW: 'outside_return_window',
  OTHER: 'other',
  REFUND_DECLINED: 'declined_after_review',
  REQUEST_DECLINED: 'other',
}

const refundMethod = (method: BackendRefundMethod): RefundMethod =>
  method === 'STORE_CREDIT' ? 'store_credit' : 'original_payment'

const unique = <T>(values: T[]) => [...new Set(values)]

/** "Linen shirt", "Linen shirt ×2" or "Linen shirt + 1 more". */
export function itemLabel(items: { productName: string; quantity: number }[]): string {
  if (items.length === 0) return 'Return'
  const [first] = items
  const name = first.quantity > 1 ? `${first.productName} ×${first.quantity}` : first.productName
  return items.length === 1 ? name : `${name} + ${items.length - 1} more`
}

const gradedRefund = (unitPricePaise: number, quantity: number, grade: Grade) =>
  Math.floor((unitPricePaise * quantity * GRADE_REFUND_PERCENT[grade]) / 100)

// ─── Scan & inspect ───────────────────────────────────────────────────

export function toReturnDetails(
  ret: BackendWarehouseReturn,
  previousStatus: BackendReturnStatus | null,
  photos: CustomerPhoto[],
): ReturnDetails {
  const comments = ret.items.map((item) => item.customerComment).filter((c): c is string => !!c)
  return {
    id: ret.rmaNumber,
    rmaNumber: ret.rmaNumber,
    qrCode: ret.rmaNumber,
    item: {
      name: itemLabel(ret.items),
      sku: ret.items.map((item) => item.sku).join(', '),
      category: CATEGORY[ret.items[0]?.category ?? 'APPAREL'],
      pricePaise: ret.items.reduce((sum, item) => sum + item.unitPricePaise * item.quantity, 0),
    },
    customer: {
      id: ret.orderNumber,
      name: ret.customerName,
      returnsLast30Days: ret.customerReturnsLast30Days,
      flaggedForReview: ret.customerFlagged,
    },
    reason: unique(ret.items.map((item) => RETURN_REASON_LABELS[item.reason])).join(', '),
    refundMethod: refundMethod(ret.refundMethod),
    status: RETURN_STATUS[ret.status],
    previousStatus: previousStatus ? RETURN_STATUS[previousStatus] : null,
    photos,
    customerNote: comments.length ? comments.join(' · ') : null,
  }
}

export type BackendDecision =
  | { kind: 'inspection'; body: BackendInspectionRequest }
  | { kind: 'reject'; body: BackendRejectRequest }

/**
 * Turns the screen's single decision into the backend's per-line inspection:
 * - accept: every unit accepted at the chosen grade;
 * - partial accept: the amount is spread over the lines in order, each up to its graded refund;
 *   a line paid less than its graded refund carries the reason (as "other" + the text), and a
 *   line that gets nothing is not accepted;
 * - reject: the whole return, with the agent's reason.
 */
export function toBackendDecision(
  ret: BackendWarehouseReturn,
  decision: InspectionDecisionRequest,
): BackendDecision {
  const note = (text: string) => (text.trim() ? text.trim() : undefined)

  if (decision.decision === 'reject') {
    return {
      kind: 'reject',
      body: { reason: decision.reason.toUpperCase() as BackendRejectionReason, note: note(decision.notes) },
    }
  }

  if (decision.decision === 'accept') {
    return {
      kind: 'inspection',
      body: {
        items: ret.items.map((item) => ({
          returnItemId: item.returnItemId,
          grade: decision.grade,
          acceptedQuantity: item.quantity,
          note: note(decision.notes),
        })),
      },
    }
  }

  const reasonNote = [decision.reason, decision.notes].map((s) => s.trim()).filter(Boolean).join(' — ')
  let remaining = decision.refundAmountPaise
  const items: BackendInspectedItem[] = ret.items.map((item) => {
    const graded = gradedRefund(item.unitPricePaise, item.quantity, decision.grade)
    const paid = Math.min(remaining, graded)
    remaining -= paid
    if (paid === graded) {
      return { returnItemId: item.returnItemId, grade: decision.grade, acceptedQuantity: item.quantity }
    }
    if (paid === 0) {
      return {
        returnItemId: item.returnItemId,
        grade: null,
        acceptedQuantity: 0,
        rejectionReason: 'OTHER',
        note: reasonNote,
      }
    }
    return {
      returnItemId: item.returnItemId,
      grade: decision.grade,
      acceptedQuantity: item.quantity,
      rejectionReason: 'OTHER',
      note: reasonNote,
      refundAmountPaise: paid,
    }
  })
  return { kind: 'inspection', body: { items } }
}

export function toDecisionResponse(
  ret: BackendWarehouseReturn,
  decision: InspectionDecisionRequest,
  result: BackendInspectionResult,
  decidedAt: string,
): InspectionDecisionResponse {
  const status = RETURN_STATUS[result.status]
  const outcome =
    status === 'rejected' ? 'rejected' : result.financeApprovalRequired ? 'sent_to_finance' : 'refund_initiated'
  return {
    inspectionId: ret.returnId,
    returnId: ret.rmaNumber,
    decision: decision.decision,
    grade: decision.grade,
    refundAmountPaise: result.refundAmountPaise,
    requiresFinanceApproval: result.financeApprovalRequired,
    outcome,
    status,
    decidedAt,
  }
}

// ─── Finance ──────────────────────────────────────────────────────────

export function toFinanceQueueItem(item: BackendFinanceQueueItem): FinanceQueueItem {
  return {
    returnId: item.rmaNumber,
    rmaNumber: item.rmaNumber,
    customer: {
      name: item.customerName,
      flaggedForReview: item.customerFlagged,
      returnsLast30Days: item.customerReturnsLast30Days,
    },
    item: {
      name: itemLabel(item.itemNames.map((productName) => ({ productName, quantity: 1 }))),
      pricePaise: item.requestedAmountPaise,
    },
    decision: item.partiallyAccepted ? 'partial_accept' : 'accept',
    grade: item.grade,
    refundAmountPaise: item.refundAmountPaise,
    refundMethod: refundMethod(item.refundMethod),
    payoutMethod: item.refundMethod === 'STORE_CREDIT' ? 'Store credit' : 'Original payment',
    inspectedBy: item.inspectedByName ?? '—',
    inspectedAt: item.inspectedAt,
    refundKey: item.refundKey,
  }
}

export function toFinanceDecision(
  decision: BackendReviewDecision,
  action: 'approve' | 'decline',
): FinanceDecisionResponse {
  const status = RETURN_STATUS[decision.status]
  const paid = action === 'approve' && decision.status === 'REFUNDED'
  return {
    returnId: decision.rmaNumber,
    outcome: action === 'decline' ? 'declined' : paid ? 'paid' : 'payment_failed',
    status,
    refund: paid
      ? {
          amountPaise: decision.refundAmountPaise ?? 0,
          reference: decision.refundReference ?? '—',
          paidAt: decision.decidedAt,
        }
      : null,
    decidedAt: decision.decidedAt,
  }
}

// ─── Customer tracking ────────────────────────────────────────────────

export function toReturnTracking(t: BackendTracking): ReturnTracking {
  const history: ReturnTracking['history'] = []
  for (const entry of t.timeline) {
    const status = TIMELINE_STATUS[entry.step]
    if (entry.at && !history.some((h) => h.status === status)) history.push({ status, at: entry.at })
  }

  const inspected = t.items.filter((item) => item.acceptedQuantity !== null)
  const partialLine = inspected.find(
    (item) => item.rejectionReason !== null || (item.acceptedQuantity ?? 0) < item.quantity,
  )
  const partialReason = partialLine
    ? partialLine.rejectionReason === 'OTHER' || !partialLine.rejectionReason
      ? partialLine.inspectionNote
      : REJECT_REASON_LABELS[REJECT_REASON[partialLine.rejectionReason]]
    : null
  const notes = inspected
    .filter((item) => item !== partialLine || partialLine.rejectionReason !== 'OTHER')
    .map((item) => item.inspectionNote)
    .filter((n): n is string => !!n)
    .join('; ')

  const rejectedAt = t.timeline.find((e) => e.step === 'REJECTED' || e.step === 'CANCELLED')?.at

  return {
    rmaNumber: t.rmaNumber,
    qrCode: t.label?.qrCodeValue ?? t.rmaNumber,
    item: { name: itemLabel(t.items), pricePaise: t.estimatedRefundPaise },
    refundMethod: refundMethod(t.refundMethod),
    status: TIMELINE_STATUS[t.status],
    history,
    requiresFinanceApproval: t.refund ? t.refund.financeReview : null,
    inspection:
      t.refund && inspected.length
        ? {
            decision: partialLine ? 'partial_accept' : 'accept',
            grade: inspected.find((item) => item.grade)?.grade ?? null,
            notes,
            partialReason,
            refundAmountPaise: t.refund.amountPaise,
          }
        : null,
    rejection: t.rejection
      ? {
          reason: REJECT_REASON[t.rejection.reason],
          notes: t.rejection.note ?? (t.rejection.reason === 'REQUEST_DECLINED' ? t.rejection.message : ''),
          at: rejectedAt ?? t.createdAt,
        }
      : null,
    refund:
      t.refund && t.status === 'REFUNDED' && t.refund.refundedAt
        ? {
            amountPaise: t.refund.amountPaise,
            paidAt: t.refund.refundedAt,
            destination: t.refundMethod === 'STORE_CREDIT' ? 'Store credit' : 'Original payment method',
            reference: t.refund.reference ?? '—',
          }
        : null,
  }
}
