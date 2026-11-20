import { http, USE_MOCKS } from './client'
import { financeMock } from './mocks/inspection.mock'
import type { DeclineRefundRequest, FinanceDecisionResponse, FinanceQueueItem } from './types'

const live = {
  getQueue: () => http.get<FinanceQueueItem[]>('/finance/queue'),

  /** Pass the item's refundKey. Retries with the same key never pay twice. */
  approveRefund: (returnId: string, idempotencyKey: string) =>
    http.post<FinanceDecisionResponse>(
      `/finance/refunds/${encodeURIComponent(returnId)}/approve`,
      {},
      { 'Idempotency-Key': idempotencyKey },
    ),

  declineRefund: (returnId: string, body: DeclineRefundRequest, idempotencyKey: string) =>
    http.post<FinanceDecisionResponse>(
      `/finance/refunds/${encodeURIComponent(returnId)}/decline`,
      body,
      { 'Idempotency-Key': idempotencyKey },
    ),
}

export type FinanceApi = typeof live

export const financeApi: FinanceApi = USE_MOCKS ? financeMock : live
