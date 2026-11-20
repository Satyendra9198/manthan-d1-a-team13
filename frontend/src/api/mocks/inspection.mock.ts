/**
 * In-browser stand-in for the backend. It enforces the same rules the real API must:
 * state machine, audit log, single refund per return, idempotent decisions.
 */
import {
  assertTransition,
  FINANCE_APPROVAL_ABOVE_PAISE,
  GRADE_LABELS,
  GRADE_REFUND_PERCENT,
  GRADES,
  addDays,
  countRecentReturns,
  isFlaggedForReview,
  maxPartialRefund,
  REJECT_REASON_LABELS,
  REJECT_REASONS,
  refundForGrade,
  rejectNoteRequired,
  requiresFinanceApproval,
  scanBlockReason,
  validatePartialRefund,
  todayIso,
  type ReturnStatus,
} from '@/domain/returns'
import { ApiError } from '../client'
import type {
  Decision,
  Grade,
  InspectionConfig,
  InspectionDecisionRequest,
  InspectionDecisionResponse,
  InspectionOutcome,
  RejectReason,
  ReturnDetails,
  ReturnTracking,
} from '../types'

const LATENCY_MS = import.meta.env.MODE === 'test' ? 0 : 300
const wait = () => new Promise((r) => setTimeout(r, LATENCY_MS))
const daysAgo = (n: number) => addDays(todayIso(), -n)

export const MOCK_CONFIG: InspectionConfig = {
  grades: GRADES.map((grade) => ({
    grade,
    label: GRADE_LABELS[grade],
    refundPercent: GRADE_REFUND_PERCENT[grade],
  })),
  financeApprovalAbovePaise: FINANCE_APPROVAL_ABOVE_PAISE,
}

type StoredDecision = {
  decision: Decision
  grade: Grade | null
  notes: string
  refundAmountPaise: number
  requiresFinanceApproval: boolean
  partialReason: string | null
  rejectReason: RejectReason | null
  decidedAt: string
  refund: { paidAt: string; reference: string } | null
}

type MockReturn = Omit<ReturnDetails, 'customer'> & {
  customer: { id: string; name: string; returnDates: string[] }
  /** Where an original-payment refund lands, e.g. "UPI · ananya@okbank". */
  payoutLabel: string
  /** Every status reached, with time. Feeds the customer timeline. */
  history: { status: ReturnStatus; at: string }[]
  decision: StoredDecision | null
}

const at = (local: string) => new Date(`${local}:00+05:30`).toISOString() // IST → ISO

function seed(): Record<string, MockReturn> {
  return {
    // Already scanned (in transit → received): ready to inspect. Matches the agent design.
    'rr-qr-7f3a9c41': {
      id: 'ret_00731',
      rmaNumber: 'RMA-2026-00731',
      qrCode: 'rr-qr-7f3a9c41',
      item: { name: 'Linen shirt', sku: 'LS-L-WHT', category: 'apparel', pricePaise: 189_900 },
      customer: { id: 'cus_4412', name: 'Ananya Rao', returnDates: [daysAgo(6)] },
      payoutLabel: 'UPI · ananya@okbank',
      reason: 'Too large',
      refundMethod: 'original_payment',
      status: 'received',
      previousStatus: 'in_transit',
      history: [
        { status: 'requested', at: at('2026-11-17T10:12') },
        { status: 'approved', at: at('2026-11-17T10:12') },
        { status: 'in_transit', at: at('2026-11-19T16:40') },
        { status: 'received', at: at('2026-11-20T14:58') },
      ],
      decision: null,
      photos: [
        { url: '', alt: 'Customer photo of the linen shirt, front' },
        { url: '', alt: 'Customer photo of the size tag' },
      ],
      customerNote: 'Ordered L, fits like XL. Tags still attached.',
    },
    // In transit: scanning moves it to received. Above ₹10,000. Customer is flagged (4 in 30 days).
    'rr-qr-1b2c3d4e': {
      id: 'ret_00742',
      rmaNumber: 'RMA-2026-00742',
      qrCode: 'rr-qr-1b2c3d4e',
      item: { name: 'Wireless headphones', sku: 'WH-1000-BLK', category: 'electronics', pricePaise: 2_499_900 },
      customer: {
        id: 'cus_5120',
        name: 'Rohan Mehta',
        returnDates: [daysAgo(2), daysAgo(9), daysAgo(15), daysAgo(25)],
      },
      payoutLabel: 'Store credit',
      reason: 'Defective',
      refundMethod: 'store_credit',
      status: 'in_transit',
      previousStatus: 'approved',
      history: [
        { status: 'requested', at: at('2026-11-18T09:05') },
        { status: 'approved', at: at('2026-11-18T09:05') },
        { status: 'in_transit', at: at('2026-11-19T18:20') },
      ],
      decision: null,
      photos: [{ url: '', alt: 'Customer photo of the headphones' }],
      customerNote: 'Left ear cup has no sound.',
    },
    // Used in the Reject popup design.
    'rr-qr-a1d0e702': {
      id: 'ret_00702',
      rmaNumber: 'RMA-2026-00702',
      qrCode: 'rr-qr-a1d0e702',
      item: { name: 'Denim shorts', sku: 'DS-M-BLU', category: 'apparel', pricePaise: 149_900 },
      customer: { id: 'cus_3310', name: 'Kabir Shah', returnDates: [daysAgo(3)] },
      payoutLabel: 'UPI · kabir@okicici',
      reason: 'Changed my mind',
      refundMethod: 'original_payment',
      status: 'received',
      previousStatus: 'in_transit',
      history: [
        { status: 'requested', at: at('2026-11-12T09:40') },
        { status: 'approved', at: at('2026-11-12T09:40') },
        { status: 'in_transit', at: at('2026-11-14T11:05') },
        { status: 'received', at: at('2026-11-18T10:20') },
      ],
      decision: null,
      photos: [{ url: '', alt: 'Customer photo of the denim shorts' }],
      customerNote: null,
    },
    // Used in the Partially accept popup design.
    'rr-qr-c4f5e744': {
      id: 'ret_00744',
      rmaNumber: 'RMA-2026-00744',
      qrCode: 'rr-qr-c4f5e744',
      item: { name: 'Running shoes', sku: 'RS-9-GRY', category: 'footwear', pricePaise: 429_900 },
      customer: { id: 'cus_4087', name: 'Priya Nair', returnDates: [daysAgo(8)] },
      payoutLabel: 'Visa •••• 4242',
      reason: 'Uncomfortable',
      refundMethod: 'original_payment',
      status: 'received',
      previousStatus: 'in_transit',
      history: [
        { status: 'requested', at: at('2026-11-15T12:30') },
        { status: 'approved', at: at('2026-11-15T12:30') },
        { status: 'in_transit', at: at('2026-11-16T15:10') },
        { status: 'received', at: at('2026-11-19T11:45') },
      ],
      decision: null,
      photos: [{ url: '', alt: 'Customer photo of the running shoes' }],
      customerNote: 'Worn twice indoors.',
    },
    // Done: refunded in full. Scanning again must be refused.
    'rr-qr-9e8d7c6b': {
      id: 'ret_00690',
      rmaNumber: 'RMA-2026-00690',
      qrCode: 'rr-qr-9e8d7c6b',
      item: { name: 'Cotton bedsheet', sku: 'BS-Q-BLU', category: 'home', pricePaise: 149_900 },
      customer: { id: 'cus_2203', name: 'Meera Iyer', returnDates: [daysAgo(12)] },
      payoutLabel: 'UPI · meera@oksbi',
      reason: 'Colour different from photo',
      refundMethod: 'original_payment',
      status: 'refunded',
      previousStatus: 'inspected',
      history: [
        { status: 'requested', at: at('2026-11-06T08:15') },
        { status: 'approved', at: at('2026-11-06T08:15') },
        { status: 'in_transit', at: at('2026-11-07T13:00') },
        { status: 'received', at: at('2026-11-10T10:05') },
        { status: 'inspected', at: at('2026-11-10T16:20') },
        { status: 'refunded', at: at('2026-11-10T16:21') },
      ],
      decision: {
        decision: 'accept',
        grade: 'A',
        notes: '',
        refundAmountPaise: 149_900,
        requiresFinanceApproval: false,
        partialReason: null,
        rejectReason: null,
        decidedAt: at('2026-11-10T16:20'),
        refund: { paidAt: at('2026-11-10T16:21'), reference: 'GW-5521-2026' },
      },
      photos: [],
      customerNote: null,
    },
    // Done: rejected (customer status screen design).
    'rr-qr-5d6e7f80': {
      id: 'ret_00655',
      rmaNumber: 'RMA-2026-00655',
      qrCode: 'rr-qr-5d6e7f80',
      item: { name: 'Denim jacket', sku: 'DJ-M-IND', category: 'apparel', pricePaise: 349_900 },
      customer: { id: 'cus_3310', name: 'Kabir Shah', returnDates: [daysAgo(10)] },
      payoutLabel: 'UPI · kabir@okicici',
      reason: 'Changed my mind',
      refundMethod: 'original_payment',
      status: 'rejected',
      previousStatus: 'inspected',
      history: [
        { status: 'requested', at: at('2026-11-12T09:40') },
        { status: 'approved', at: at('2026-11-12T09:40') },
        { status: 'in_transit', at: at('2026-11-14T11:05') },
        { status: 'received', at: at('2026-11-18T10:20') },
        { status: 'inspected', at: at('2026-11-18T10:41') },
        { status: 'rejected', at: at('2026-11-18T10:41') },
      ],
      decision: {
        decision: 'reject',
        grade: null,
        notes: 'Visible wear on the hem; tags removed.',
        refundAmountPaise: 0,
        requiresFinanceApproval: false,
        partialReason: null,
        rejectReason: 'used_or_worn',
        decidedAt: at('2026-11-18T10:41'),
        refund: null,
      },
      photos: [],
      customerNote: null,
    },
    // Inspected, refund waiting for finance (requiresFinanceApproval = true).
    'rr-qr-8a9b0c1d': {
      id: 'ret_00720',
      rmaNumber: 'RMA-2026-00720',
      qrCode: 'rr-qr-8a9b0c1d',
      item: { name: 'Smart watch', sku: 'SW-44-BLK', category: 'electronics', pricePaise: 1_899_900 },
      customer: { id: 'cus_6001', name: 'Arjun Das', returnDates: [daysAgo(4)] },
      payoutLabel: 'Mastercard •••• 8812',
      reason: 'Battery drains fast',
      refundMethod: 'original_payment',
      status: 'inspected',
      previousStatus: 'received',
      history: [
        { status: 'requested', at: at('2026-11-14T19:00') },
        { status: 'approved', at: at('2026-11-14T19:00') },
        { status: 'in_transit', at: at('2026-11-15T10:30') },
        { status: 'received', at: at('2026-11-18T09:15') },
        { status: 'inspected', at: at('2026-11-18T12:02') },
      ],
      decision: {
        decision: 'accept',
        grade: 'A',
        notes: '',
        refundAmountPaise: 1_899_900,
        requiresFinanceApproval: true,
        partialReason: null,
        rejectReason: null,
        decidedAt: at('2026-11-18T12:02'),
        refund: null,
      },
      photos: [],
      customerNote: null,
    },
  }
}

export type AuditEntry = {
  id: string
  at: string
  actor: 'warehouse_agent' | 'system'
  returnId: string
  from: ReturnStatus
  to: ReturnStatus
  note?: string
}

export type LedgerEntry = {
  id: string
  returnId: string
  amountPaise: number
  method: ReturnDetails['refundMethod']
  idempotencyKey: string
  createdAt: string
}

let returns = seed()
let auditLog: AuditEntry[] = []
let ledger: LedgerEntry[] = []
let idempotency = new Map<string, { fingerprint: string; response: InspectionDecisionResponse }>()
let inspectedToday = 37
let seq = 0

/** Reset all mock state (used by tests). */
export function resetInspectionMock() {
  returns = seed()
  auditLog = []
  ledger = []
  idempotency = new Map()
  inspectedToday = 37
  seq = 0
}

export const getMockAuditLog = () => [...auditLog]
export const getMockLedger = () => [...ledger]

function transition(ret: MockReturn, to: ReturnStatus, actor: AuditEntry['actor'], note?: string) {
  assertTransition(ret.status, to)
  auditLog.push({
    id: `aud_${++seq}`,
    at: new Date().toISOString(),
    actor,
    returnId: ret.id,
    from: ret.status,
    to,
    note,
  })
  ret.previousStatus = ret.status
  ret.status = to
  ret.history.push({ status: to, at: new Date().toISOString() })
}

function toDetails(ret: MockReturn): ReturnDetails {
  const { returnDates, ...customer } = ret.customer
  const { payoutLabel: _p, history: _h, decision: _d, ...rest } = ret
  const today = todayIso()
  return structuredClone({
    ...rest,
    customer: {
      ...customer,
      returnsLast30Days: countRecentReturns(returnDates, today),
      flaggedForReview: isFlaggedForReview(returnDates, today),
    },
  })
}

/** Synchronous read for static screens. Does not change state. */
export function getMockReturnDetails(qrCode: string): ReturnDetails {
  return toDetails(returns[qrCode])
}

export const inspectionMock = {
  async scanReturn({ qrCode }: { qrCode: string }): Promise<ReturnDetails> {
    await wait()
    const ret = returns[qrCode.trim().toLowerCase()]
    if (!ret) throw new ApiError(404, `No return found for "${qrCode.trim()}". Check the code and try again.`, 'RETURN_NOT_FOUND')

    const blocked = scanBlockReason(ret.status)
    if (blocked) throw new ApiError(409, blocked, 'RETURN_NOT_INSPECTABLE')

    if (ret.status === 'approved') transition(ret, 'in_transit', 'system', 'Carrier scan missing; set at warehouse')
    if (ret.status === 'in_transit') transition(ret, 'received', 'warehouse_agent')
    return toDetails(ret)
  },

  async getConfig(): Promise<InspectionConfig> {
    await wait()
    return structuredClone(MOCK_CONFIG)
  },

  async getTodayStats() {
    await wait()
    return { inspectedToday }
  },

  async submitDecision(
    returnId: string,
    body: InspectionDecisionRequest,
    idempotencyKey: string,
  ): Promise<InspectionDecisionResponse> {
    await wait()
    if (!idempotencyKey) throw new ApiError(400, 'Missing Idempotency-Key header.', 'IDEMPOTENCY_KEY_REQUIRED')

    // Retry of a request we already handled: replay the original answer, do nothing again.
    const fingerprint = JSON.stringify([returnId, body])
    const seen = idempotency.get(idempotencyKey)
    if (seen) {
      if (seen.fingerprint !== fingerprint) {
        throw new ApiError(422, 'This request key was already used for a different decision.', 'IDEMPOTENCY_KEY_REUSED')
      }
      return structuredClone(seen.response)
    }

    const ret = Object.values(returns).find((r) => r.id === returnId)
    if (!ret) throw new ApiError(404, 'Return not found.', 'RETURN_NOT_FOUND')
    if (ret.status !== 'received') {
      throw new ApiError(
        409,
        scanBlockReason(ret.status) ?? 'Scan this parcel before inspecting it.',
        'RETURN_NOT_INSPECTABLE',
      )
    }
    const customerFlagged = toDetails(ret).customer.flaggedForReview

    // Validate before changing anything.
    let refundAmountPaise = 0
    let auditNote: string | undefined
    if (body.decision === 'reject') {
      if (!REJECT_REASONS.includes(body.reason)) {
        throw new ApiError(422, 'Pick a reason to show the customer.', 'REASON_REQUIRED')
      }
      if (rejectNoteRequired(body.reason) && !body.notes.trim()) {
        throw new ApiError(422, 'Add a note explaining the reason.', 'NOTES_REQUIRED')
      }
      auditNote = [REJECT_REASON_LABELS[body.reason], body.notes.trim()].filter(Boolean).join(': ')
    } else {
      if (!body.grade) throw new ApiError(422, 'Pick a condition grade first.', 'GRADE_REQUIRED')
      if (body.decision === 'partial_accept') {
        const max = maxPartialRefund(ret.item.pricePaise, body.grade)
        if (validatePartialRefund(body.refundAmountPaise, max)) {
          throw new ApiError(422, 'Enter a refund amount between ₹0.01 and the maximum.', 'INVALID_REFUND_AMOUNT')
        }
        if (!body.reason.trim()) throw new ApiError(422, 'Add a reason for the customer.', 'REASON_REQUIRED')
        refundAmountPaise = body.refundAmountPaise
        auditNote = [body.reason.trim(), body.notes.trim()].filter(Boolean).join(': ')
      } else {
        refundAmountPaise = refundForGrade(ret.item.pricePaise, body.grade)
        auditNote = body.notes.trim() || undefined
      }
    }

    transition(ret, 'inspected', 'warehouse_agent', auditNote)

    // Server decides routing; the client's requiresFinanceApproval is only a hint.
    const needsApproval =
      body.decision !== 'reject' &&
      requiresFinanceApproval(refundAmountPaise, customerFlagged, MOCK_CONFIG.financeApprovalAbovePaise)

    const decidedAt = new Date().toISOString()
    ret.decision = {
      decision: body.decision,
      grade: body.grade ?? null,
      notes: body.notes.trim(),
      refundAmountPaise,
      requiresFinanceApproval: needsApproval,
      partialReason: body.decision === 'partial_accept' ? body.reason.trim() : null,
      rejectReason: body.decision === 'reject' ? body.reason : null,
      decidedAt,
      refund: null,
    }

    let outcome: InspectionOutcome
    if (body.decision === 'reject') {
      transition(ret, 'rejected', 'warehouse_agent', auditNote)
      outcome = 'rejected'
    } else if (needsApproval) {
      outcome = 'sent_to_finance'
    } else {
      // One refund per return, whatever key is used (second line of defence).
      if (ledger.some((l) => l.returnId === ret.id)) {
        throw new ApiError(409, 'A refund already exists for this return.', 'DUPLICATE_REFUND')
      }
      ledger.push({
        id: `led_${++seq}`,
        returnId: ret.id,
        amountPaise: refundAmountPaise,
        method: ret.refundMethod,
        idempotencyKey,
        createdAt: new Date().toISOString(),
      })
      transition(ret, 'refunded', 'system', 'Refund paid via mock gateway')
      ret.decision.refund = {
        paidAt: new Date().toISOString(),
        reference: `GW-${String(1000 + (seq % 9000)).padStart(4, '0')}-2026`,
      }
      outcome = 'refund_initiated'
    }

    inspectedToday += 1
    const response: InspectionDecisionResponse = {
      inspectionId: `insp_${++seq}`,
      returnId,
      decision: body.decision,
      grade: body.grade ?? null,
      refundAmountPaise,
      requiresFinanceApproval: needsApproval,
      outcome,
      status: ret.status,
      decidedAt,
    }
    idempotency.set(idempotencyKey, { fingerprint, response })
    return structuredClone(response)
  },
}

/** Customer-facing view of a return. Never includes internal data such as flags or audit notes. */
function toTracking(ret: MockReturn): ReturnTracking {
  const d = ret.decision
  return structuredClone({
    rmaNumber: ret.rmaNumber,
    qrCode: ret.qrCode,
    item: { name: ret.item.name, pricePaise: ret.item.pricePaise },
    refundMethod: ret.refundMethod,
    status: ret.status,
    history: ret.history,
    requiresFinanceApproval: d && d.decision !== 'reject' ? d.requiresFinanceApproval : null,
    inspection:
      d && d.decision !== 'reject'
        ? {
            decision: d.decision,
            grade: d.grade,
            notes: d.notes,
            partialReason: d.partialReason,
            refundAmountPaise: d.refundAmountPaise,
          }
        : null,
    rejection: d?.rejectReason ? { reason: d.rejectReason, notes: d.notes, at: d.decidedAt } : null,
    refund: d?.refund
      ? {
          amountPaise: d.refundAmountPaise,
          paidAt: d.refund.paidAt,
          destination: ret.refundMethod === 'store_credit' ? 'Store credit' : ret.payoutLabel,
          reference: d.refund.reference,
        }
      : null,
  })
}

export const trackingMock = {
  async getTracking(rmaNumber: string): Promise<ReturnTracking> {
    await wait()
    const ret = Object.values(returns).find(
      (r) => r.rmaNumber.toLowerCase() === rmaNumber.trim().toLowerCase(),
    )
    if (!ret) throw new ApiError(404, `We couldn't find return ${rmaNumber}.`, 'RETURN_NOT_FOUND')
    return toTracking(ret)
  },
}
