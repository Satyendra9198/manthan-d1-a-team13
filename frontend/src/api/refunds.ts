import { http, USE_MOCKS } from './client'
import { refundsMock } from './mocks/inspection.mock'
import type { LedgerResponse, RefundRecord } from './types'

const live = {
  getRefunds: () => http.get<RefundRecord[]>('/refunds'),

  getLedger: () => http.get<LedgerResponse>('/ledger'),

  /** Retry a failed refund. Send the refund's own idempotencyKey; it is never charged twice. */
  retryRefund: (refundId: string, idempotencyKey: string) =>
    http.post<RefundRecord>(
      `/refunds/${encodeURIComponent(refundId)}/retry`,
      {},
      { 'Idempotency-Key': idempotencyKey },
    ),
}

export type RefundsApi = typeof live

export const refundsApi: RefundsApi = USE_MOCKS ? refundsMock : live
