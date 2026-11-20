import { ApiError, http, USE_MOCKS } from './client'
import type { BackendFinanceQueueItem, BackendReviewDecision } from './live/backend-types'
import { toFinanceDecision, toFinanceQueueItem } from './live/mappers'
import { financeMock } from './mocks/inspection.mock'
import type { DeclineRefundRequest, FinanceDecisionResponse, FinanceQueueItem } from './types'

/** Someone else already decided it: the screen drops the row and reloads the queue. */
const alreadyDecided = (e: unknown) =>
  e instanceof ApiError && e.code === 'ILLEGAL_TRANSITION'
    ? new ApiError(409, 'This refund was already decided by someone else.', 'NOT_AWAITING_APPROVAL')
    : e

const returnPath = (rma: string) => `/finance/returns/${encodeURIComponent(rma)}`

const live = {
  async getQueue(): Promise<FinanceQueueItem[]> {
    const items = await http.get<BackendFinanceQueueItem[]>('/finance/returns/pending')
    return items.map(toFinanceQueueItem)
  },

  /**
   * `returnId` is the RMA number. The backend pays each return at most once with its own refund
   * key, so a double click or retry can never pay twice.
   */
  async approveRefund(returnId: string, idempotencyKey: string): Promise<FinanceDecisionResponse> {
    try {
      const decision = await http.post<BackendReviewDecision>(`${returnPath(returnId)}/approve`, {}, {
        'Idempotency-Key': idempotencyKey,
      })
      return toFinanceDecision(decision, 'approve')
    } catch (e) {
      throw alreadyDecided(e)
    }
  },

  async declineRefund(
    returnId: string,
    body: DeclineRefundRequest,
    idempotencyKey: string,
  ): Promise<FinanceDecisionResponse> {
    try {
      const decision = await http.post<BackendReviewDecision>(`${returnPath(returnId)}/decline`, body, {
        'Idempotency-Key': idempotencyKey,
      })
      return toFinanceDecision(decision, 'decline')
    } catch (e) {
      throw alreadyDecided(e)
    }
  },
}

export type FinanceApi = typeof live

export const financeApi: FinanceApi = USE_MOCKS ? financeMock : live
