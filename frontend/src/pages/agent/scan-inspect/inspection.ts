import type { Decision, InspectionDecisionResponse, RefundMethod } from '@/api/types'
import { needsFinanceApproval } from '@/domain/returns'
import { formatPaise } from '@/lib/format'

export { STATUS_LABELS } from '@/domain/returns'

export const REFUND_METHOD_LABELS: Record<RefundMethod, string> = {
  original_payment: 'Original payment',
  store_credit: 'Store credit',
}

/** Keyboard shortcuts shown on the buttons. Grades use 1, 2, 3 in config order. */
export const DECISION_SHORTCUTS: Record<Decision, string> = {
  accept: 'A',
  partial_accept: 'P',
  reject: 'R',
}

/**
 * Explains where a refund will go. Finance approves refunds above ₹10,000 and refunds
 * for flagged customers. `panel` copy ends with "after you accept"; `dialog` copy also says
 * when the customer is not flagged.
 */
export function routingHint(
  refundPaise: number,
  limitPaise: number,
  customerFlagged: boolean,
  context: 'panel' | 'dialog' = 'panel',
): string {
  const limit = formatPaise(limitPaise)
  const over = needsFinanceApproval(refundPaise, limitPaise)
  const tail = context === 'panel' ? ' after you accept' : ''

  if (over && customerFlagged) return `Over ${limit} and customer flagged, so finance must approve this refund${tail}.`
  if (over) return `Over ${limit}, so finance must approve this refund${tail}.`
  if (customerFlagged) return `Customer is flagged for review, so finance must approve this refund${tail}.`

  const amount = refundPaise === limitPaise ? `Exactly ${limit}` : `Under ${limit}`
  return context === 'panel'
    ? `${amount}, so this goes straight to refund after you accept.`
    : `${amount} and customer not flagged, so this goes straight to refund.`
}

export function decisionMessage(rma: string, res: InspectionDecisionResponse): string {
  const amount = formatPaise(res.refundAmountPaise)
  const partial = res.decision === 'partial_accept' ? 'Partial refund' : 'Refund'
  switch (res.outcome) {
    case 'refund_initiated':
      return res.status === 'refunded'
        ? `${rma} saved. ${partial} of ${amount} paid.`
        : `${rma} saved. ${partial} of ${amount} approved, but the payment gateway failed. Finance will retry it.`
    case 'sent_to_finance':
      return `${rma} saved. ${partial} of ${amount} sent to the finance queue for approval.`
    case 'rejected':
      return `${rma} rejected. The customer will see the reason on their status page.`
  }
}
