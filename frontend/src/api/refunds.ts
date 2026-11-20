import { http, USE_MOCKS } from './client'
import { refundsMock } from './mocks/inspection.mock'
import type { LedgerResponse, RefundRecord } from './types'

/** Flip to true when the backend serves /refunds and /ledger. */
const LEDGER_BACKEND_AVAILABLE = false

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

/**
 * The backend has no refunds or ledger endpoints yet (docs/api/README.md), so this screen stays on
 * demo data even when VITE_USE_MOCKS is false. Switch to `live` once those endpoints exist.
 */
export const refundsApi: RefundsApi = USE_MOCKS || !LEDGER_BACKEND_AVAILABLE ? refundsMock : live
