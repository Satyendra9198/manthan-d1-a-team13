import { ApiError, http, USE_MOCKS } from './client'
import type {
  BackendInspectionResult,
  BackendInspectionSummary,
  BackendReturnStatus,
  BackendWarehouseReturn,
} from './live/backend-types'
import { toBackendDecision, toDecisionResponse, toReturnDetails } from './live/mappers'
import { inspectionMock } from './mocks/inspection.mock'
import type {
  CustomerPhoto,
  InspectionConfig,
  InspectionDecisionRequest,
  InspectionDecisionResponse,
  InspectionStats,
  ReturnDetails,
  ScanReturnRequest,
} from './types'
import {
  FINANCE_APPROVAL_ABOVE_PAISE,
  GRADE_LABELS,
  GRADE_REFUND_PERCENT,
  GRADES,
  scanBlockReason,
} from '@/domain/returns'

const returnPath = (rma: string) => `/warehouse/returns/${encodeURIComponent(rma)}`

/** The last scanned return, kept so a decision can be spread over its lines. */
const scanned = new Map<string, BackendWarehouseReturn>()
/** Object URLs of the photos on screen, released when the next return is scanned. */
let photoUrls: string[] = []

async function loadPhotos(ret: BackendWarehouseReturn): Promise<CustomerPhoto[]> {
  photoUrls.forEach((url) => URL.revokeObjectURL(url))
  photoUrls = []
  const photos = ret.items.flatMap((item) =>
    item.photos.map((photo, index) => ({ item, photo, index })),
  )
  return Promise.all(
    photos.map(async ({ item, photo, index }) => {
      // The photo endpoint needs the staff token, which <img src> can't send.
      const blob = await http.blob(photo.url.replace(/^\/api\/v1/, ''))
      const url = URL.createObjectURL(blob)
      photoUrls.push(url)
      return { url, alt: `${item.productName} · photo ${index + 1}` }
    }),
  )
}

const notInspectable = (e: unknown) =>
  e instanceof ApiError && e.code === 'ILLEGAL_TRANSITION'
    ? new ApiError(409, 'This return has already been decided. Scan the next parcel.', 'RETURN_NOT_INSPECTABLE')
    : e

const live = {
  /**
   * Looks the return up by the code on its label (the RMA number) and scans it in when it is still
   * on its way (A-17: from approved or in transit). Throws 409 when it can't be inspected.
   */
  async scanReturn({ qrCode }: ScanReturnRequest): Promise<ReturnDetails> {
    const rma = qrCode.trim().toUpperCase()
    let ret = await http.get<BackendWarehouseReturn>(returnPath(rma))
    let previous: BackendReturnStatus | null = null
    if (ret.allowedActions.includes('RECEIVE')) {
      previous = ret.status
      ret = await http.post<BackendWarehouseReturn>(`${returnPath(rma)}/receive`, {})
    }
    if (ret.status !== 'RECEIVED') {
      const details = toReturnDetails(ret, null, [])
      throw new ApiError(
        409,
        scanBlockReason(details.status) ?? 'This return cannot be inspected now.',
        'RETURN_NOT_INSPECTABLE',
      )
    }
    scanned.set(ret.rmaNumber, ret)
    return toReturnDetails(ret, previous, await loadPhotos(ret))
  },

  /** Grades and the threshold are business rules shared with the backend (A-06, A-08). */
  async getConfig(): Promise<InspectionConfig> {
    return {
      grades: GRADES.map((grade) => ({
        grade,
        label: GRADE_LABELS[grade],
        refundPercent: GRADE_REFUND_PERCENT[grade],
      })),
      financeApprovalAbovePaise: FINANCE_APPROVAL_ABOVE_PAISE,
    }
  },

  async getTodayStats(): Promise<InspectionStats> {
    const summary = await http.get<BackendInspectionSummary>('/warehouse/inspections/today')
    return { inspectedToday: summary.inspectedToday }
  },

  /**
   * The backend's state machine makes a decision final: a retry of a saved decision is refused
   * with 409 rather than applied twice, and the refund itself is keyed per return.
   */
  async submitDecision(
    returnId: string,
    body: InspectionDecisionRequest,
    idempotencyKey: string,
  ): Promise<InspectionDecisionResponse> {
    const ret = scanned.get(returnId)
    if (!ret) throw new ApiError(409, 'Scan the return again before deciding.', 'RETURN_NOT_FOUND')
    const decision = toBackendDecision(ret, body)
    const headers = { 'Idempotency-Key': idempotencyKey }
    try {
      const result =
        decision.kind === 'reject'
          ? await http.post<BackendInspectionResult>(`${returnPath(returnId)}/reject`, decision.body, headers)
          : await http.post<BackendInspectionResult>(`${returnPath(returnId)}/inspection`, decision.body, headers)
      scanned.delete(returnId)
      return toDecisionResponse(ret, body, result, new Date().toISOString())
    } catch (e) {
      throw notInspectable(e)
    }
  },
}

export type InspectionApi = typeof live

/** Swap between real backend and mocks with VITE_USE_MOCKS. */
export const inspectionApi: InspectionApi = USE_MOCKS ? inspectionMock : live
