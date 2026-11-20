/** Refund rules. Money is in paise (integer): ₹1,899 → 189900. */

export type Grade = 'A' | 'B' | 'C'

export const GRADES: readonly Grade[] = ['A', 'B', 'C']

/** Business rule: only grade C is reduced (50%). */
export const GRADE_REFUND_PERCENT: Record<Grade, number> = { A: 100, B: 100, C: 50 }

export const GRADE_LABELS: Record<Grade, string> = {
  A: 'Like new',
  B: 'Minor wear',
  C: 'Damaged',
}

/** Business rule: refunds ABOVE ₹10,000 need finance approval. Exactly ₹10,000 does not. */
export const FINANCE_APPROVAL_ABOVE_PAISE = 1_000_000

/** Rounds down so we never pay more than the rule allows. */
export function refundForGrade(pricePaise: number, grade: Grade): number {
  return Math.floor((pricePaise * GRADE_REFUND_PERCENT[grade]) / 100)
}

export function needsFinanceApproval(
  refundPaise: number,
  limitPaise: number = FINANCE_APPROVAL_ABOVE_PAISE,
): boolean {
  return refundPaise > limitPaise
}

/**
 * Should this refund wait for a finance reviewer?
 * Yes when it is above ₹10,000 OR the customer is flagged for review (more than 3 returns in 30 days).
 * Sent as `requiresFinanceApproval` with accept / partial accept; the server recomputes it.
 */
export function requiresFinanceApproval(
  refundPaise: number,
  customerFlagged: boolean,
  limitPaise: number = FINANCE_APPROVAL_ABOVE_PAISE,
): boolean {
  return needsFinanceApproval(refundPaise, limitPaise) || customerFlagged
}

/** Partial refunds can't exceed what the grade allows (grade C → 50% of price). */
export function maxPartialRefund(pricePaise: number, grade: Grade): number {
  return refundForGrade(pricePaise, grade)
}

export type PartialRefundError = 'REQUIRED' | 'NOT_POSITIVE' | 'ABOVE_MAX'

export function validatePartialRefund(
  amountPaise: number | null,
  maxPaise: number,
): PartialRefundError | null {
  if (amountPaise === null || Number.isNaN(amountPaise)) return 'REQUIRED'
  if (!Number.isInteger(amountPaise) || amountPaise <= 0) return 'NOT_POSITIVE'
  if (amountPaise > maxPaise) return 'ABOVE_MAX'
  return null
}
