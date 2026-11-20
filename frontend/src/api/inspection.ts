import { http, USE_MOCKS } from './client'
import { inspectionMock } from './mocks/inspection.mock'
import type {
  InspectionConfig,
  InspectionDecisionRequest,
  InspectionDecisionResponse,
  InspectionStats,
  ReturnDetails,
  ScanReturnRequest,
} from './types'

const live = {
  /** Look up a return by QR and mark it received. 404 unknown code, 409 wrong status. */
  scanReturn: (body: ScanReturnRequest) => http.post<ReturnDetails>('/returns/scan', body),

  getConfig: () => http.get<InspectionConfig>('/inspection/config'),

  getTodayStats: () => http.get<InspectionStats>('/inspection/stats/today'),

  /** Reuse the same idempotencyKey when retrying after a timeout or network error. */
  submitDecision: (returnId: string, body: InspectionDecisionRequest, idempotencyKey: string) =>
    http.post<InspectionDecisionResponse>(
      `/returns/${encodeURIComponent(returnId)}/inspection`,
      body,
      { 'Idempotency-Key': idempotencyKey },
    ),
}

export type InspectionApi = typeof live

/** Swap between real backend and mocks with VITE_USE_MOCKS. */
export const inspectionApi: InspectionApi = USE_MOCKS ? inspectionMock : live
