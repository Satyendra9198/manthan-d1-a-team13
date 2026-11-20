/**
 * Payloads exactly as the Spring Boot backend sends them (docs/api/*.md). The live adapters map
 * these onto the screen types in ../types.ts so the pages don't depend on the wire format.
 */

export type BackendReturnStatus =
  | 'REQUESTED'
  | 'APPROVED'
  | 'IN_TRANSIT'
  | 'RECEIVED'
  | 'INSPECTED'
  | 'AWAITING_FINANCE_APPROVAL'
  | 'REFUND_PENDING'
  | 'REFUNDED'
  | 'REJECTED'
  | 'CANCELLED'

export type BackendTimelineStep =
  | 'REQUESTED'
  | 'APPROVED'
  | 'IN_TRANSIT'
  | 'RECEIVED'
  | 'INSPECTED'
  | 'REFUNDED'
  | 'REJECTED'
  | 'CANCELLED'

export type BackendCategory =
  | 'ELECTRONICS'
  | 'APPAREL'
  | 'HOME_KITCHEN'
  | 'BEAUTY'
  | 'INNERWEAR'
  | 'PERISHABLES'

export type BackendReturnReason =
  | 'TOO_BIG'
  | 'TOO_SMALL'
  | 'DAMAGED_ON_ARRIVAL'
  | 'NOT_AS_DESCRIBED'
  | 'CHANGED_MY_MIND'

export type BackendRejectionReason =
  | 'NOT_AS_DESCRIBED'
  | 'USED_OR_WORN'
  | 'MISSING_PARTS_OR_TAGS'
  | 'OUTSIDE_RETURN_WINDOW'
  | 'OTHER'
  | 'REQUEST_DECLINED'
  | 'REFUND_DECLINED'

export type BackendGrade = 'A' | 'B' | 'C'
export type BackendRefundMethod = 'ORIGINAL_PAYMENT' | 'STORE_CREDIT'
export type BackendRefundStatus = 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'UNKNOWN'

// ─── Auth ─────────────────────────────────────────────────────────────

export type BackendLoginResponse = {
  accessToken: string
  tokenType: 'Bearer'
  expiresAt: string
  staff: {
    id: string
    email: string
    fullName: string
    role: 'WAREHOUSE_AGENT' | 'FINANCE_REVIEWER' | 'OPERATIONS_MANAGER'
  }
}

// ─── Warehouse ────────────────────────────────────────────────────────

export type BackendWarehouseItem = {
  returnItemId: string
  orderItemId: string
  sku: string
  productName: string
  variant: string | null
  category: BackendCategory
  unitPricePaise: number
  quantity: number
  reason: BackendReturnReason
  customerComment: string | null
  photos: { photoId: string; contentType: string; url: string }[]
  grade: BackendGrade | null
  acceptedQuantity: number | null
  rejectionReason: BackendRejectionReason | null
  inspectionNote: string | null
}

export type BackendWarehouseReturn = {
  returnId: string
  rmaNumber: string
  status: BackendReturnStatus
  orderNumber: string
  customerName: string
  customerFlagged: boolean
  customerReturnsLast30Days: number
  refundMethod: BackendRefundMethod
  createdAt: string
  receivedAt: string | null
  refundAmountPaise: number | null
  rejectionReason: BackendRejectionReason | null
  allowedActions: string[]
  items: BackendWarehouseItem[]
}

export type BackendInspectedItem = {
  returnItemId: string
  grade: BackendGrade | null
  acceptedQuantity: number
  rejectionReason?: BackendRejectionReason
  note?: string
  refundAmountPaise?: number
}

export type BackendInspectionRequest = { items: BackendInspectedItem[] }

export type BackendRejectRequest = { reason: BackendRejectionReason; note?: string }

export type BackendInspectionResult = {
  rmaNumber: string
  status: BackendReturnStatus
  refundAmountPaise: number
  financeApprovalRequired: boolean
  refundStatus: BackendRefundStatus | null
}

export type BackendInspectionSummary = {
  date: string
  inspectedToday: number
  inspectedByYouToday: number
}

// ─── Finance ──────────────────────────────────────────────────────────

export type BackendFinanceQueueItem = {
  rmaNumber: string
  orderNumber: string
  customerName: string
  customerFlagged: boolean
  customerReturnsLast30Days: number
  itemNames: string[]
  grade: BackendGrade | null
  partiallyAccepted: boolean
  refundAmountPaise: number
  requestedAmountPaise: number
  refundMethod: BackendRefundMethod
  inspectedByName: string | null
  inspectedAt: string
  refundKey: string
  itemCount: number
}

export type BackendReviewDecision = {
  rmaNumber: string
  status: BackendReturnStatus
  refundStatus: BackendRefundStatus | null
  refundAmountPaise: number | null
  refundReference: string | null
  decidedAt: string
}

// ─── Customer tracking ────────────────────────────────────────────────

export type BackendTracking = {
  rmaNumber: string
  orderNumber: string
  status: BackendTimelineStep
  statusMessage: string
  refundMethod: BackendRefundMethod
  createdAt: string
  estimatedRefundPaise: number
  timeline: { step: BackendTimelineStep; state: 'COMPLETED' | 'CURRENT' | 'UPCOMING'; at: string | null }[]
  items: {
    itemId: string
    productName: string
    variant: string | null
    quantity: number
    reason: BackendReturnReason
    lineTotalPaise: number
    grade: BackendGrade | null
    acceptedQuantity: number | null
    refundPaise: number | null
    rejectionReason: BackendRejectionReason | null
    inspectionNote: string | null
  }[]
  label: { qrCodeValue: string; dropOffBy: string; shipTo: string; instructions: string } | null
  refund: {
    amountPaise: number
    requestedAmountPaise: number
    method: BackendRefundMethod
    status: 'PROCESSING' | 'REFUNDED'
    refundedAt: string | null
    financeReview: boolean
    reference: string | null
    explanation: string[]
  } | null
  rejection: { reason: BackendRejectionReason; message: string; note: string | null } | null
}
