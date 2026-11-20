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
  FINANCE_DECLINE_REASON,
  validatePartialRefund,
  todayIso,
  type ReturnStatus,
} from '@/domain/returns'
import { ApiError } from '../client'
import {
  charge,
  GATEWAY_MAX_ATTEMPTS,
  GatewayTimeoutError,
  resetMockGateway,
} from './gateway.mock'
import type {
  Decision,
  Grade,
  InspectionConfig,
  InspectionDecisionRequest,
  InspectionDecisionResponse,
  InspectionOutcome,
  RejectReason,
  DeclineRefundRequest,
  FinanceDecisionResponse,
  FinanceQueueItem,
  LedgerEntry,
  LedgerResponse,
  RefundRecord,
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
  /** Agent who inspected (mock: the signed-in agent). */
  inspectedBy: string
  /** One key per refund, assigned when it is queued for finance. Approving reuses it. */
  refundKey: string | null
  /** Set once finance approved or declined (approved refunds may still fail at the gateway). */
  financeDecidedAt?: string
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

function baseSeeds(): Record<string, MockReturn> {
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
        inspectedBy: 'Vikram',
        refundKey: 'rr-refund-87640',
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
        inspectedBy: 'Vikram',
        refundKey: null,
        refund: null,
      },
      photos: [],
      customerNote: null,
    },
  }
}

const hoursAgo = (h: number) => new Date(Date.now() - h * 3_600_000).toISOString()

/** The signed-in warehouse agent in mock mode. */
const MOCK_AGENT = 'Vikram'

/** A return inspected `waitingHours` ago and waiting for finance (requiresFinanceApproval = true). */
function queued(o: {
  n: string
  qr: string
  item: string
  category: MockReturn['item']['category']
  pricePaise: number
  grade: Grade
  customer: string
  returnDates?: string[]
  payoutLabel: string
  waitingHours: number
  refundKey: string
}): MockReturn {
  const inspectedAt = hoursAgo(o.waitingHours)
  const refundAmountPaise = refundForGrade(o.pricePaise, o.grade)
  return {
    id: `ret_${o.n}`,
    rmaNumber: `RMA-2026-${o.n}`,
    qrCode: o.qr,
    item: { name: o.item, sku: `SKU-${o.n}`, category: o.category, pricePaise: o.pricePaise },
    customer: { id: `cus_${o.n}`, name: o.customer, returnDates: o.returnDates ?? [daysAgo(5)] },
    payoutLabel: o.payoutLabel,
    reason: 'Not as expected',
    refundMethod: 'original_payment',
    status: 'inspected',
    previousStatus: 'received',
    history: [
      { status: 'requested', at: hoursAgo(o.waitingHours + 96) },
      { status: 'approved', at: hoursAgo(o.waitingHours + 96) },
      { status: 'in_transit', at: hoursAgo(o.waitingHours + 72) },
      { status: 'received', at: hoursAgo(o.waitingHours + 2) },
      { status: 'inspected', at: inspectedAt },
    ],
    decision: {
      decision: 'accept',
      grade: o.grade,
      notes: '',
      refundAmountPaise,
      requiresFinanceApproval: true,
      partialReason: null,
      rejectReason: null,
      decidedAt: inspectedAt,
      inspectedBy: MOCK_AGENT,
      refundKey: o.refundKey,
      refund: null,
    },
    photos: [],
    customerNote: null,
  }
}

/** Finance queue seeds matching the design (total ₹1,17,938). */
function queueSeeds(): Record<string, MockReturn> {
  const list = [
    queued({ n: '00712', qr: 'rr-qr-0712aa01', item: '55" 4K TV', category: 'electronics', pricePaise: 4_299_000, grade: 'A', customer: 'Priya Sharma', payoutLabel: 'UPI · priya@okhdfc', waitingHours: 2, refundKey: 'rr-refund-88214' }),
    queued({ n: '00720', qr: 'rr-qr-0720bb02', item: 'Smartphone X2', category: 'electronics', pricePaise: 3_299_900, grade: 'A', customer: 'Kabir Singh', payoutLabel: 'Visa •••• 1881', waitingHours: 1, refundKey: 'rr-refund-88231' }),
    queued({ n: '00705', qr: 'rr-qr-0705cc03', item: 'Espresso machine', category: 'home', pricePaise: 2_450_000, grade: 'C', customer: 'Neha Gupta', payoutLabel: 'UPI · neha@okaxis', waitingHours: 5, refundKey: 'rr-refund-88190' }),
    queued({ n: '00698', qr: 'rr-qr-0698dd04', item: 'Smartwatch S3', category: 'electronics', pricePaise: 1_849_900, grade: 'B', customer: 'Arjun Mehta', payoutLabel: 'Mastercard •••• 8812', waitingHours: 26, refundKey: 'rr-refund-88102' }),
    queued({ n: '00716', qr: 'rr-qr-0716ee05', item: 'Sofa cover set', category: 'home', pricePaise: 1_120_000, grade: 'A', customer: 'Rahul Verma', returnDates: [daysAgo(1), daysAgo(6), daysAgo(13), daysAgo(20)], payoutLabel: 'UPI · rahul@okicici', waitingHours: 3, refundKey: 'rr-refund-88220' }),
  ]

  // Approved by finance, but the gateway timed out on all 4 attempts (Refunds & ledger banner).
  const failed = queued({ n: '00701', qr: 'rr-qr-0701ff06', item: 'Bluetooth speaker', category: 'electronics', pricePaise: 1_499_900, grade: 'A', customer: 'Sana Khan', payoutLabel: 'Visa •••• 3321', waitingHours: 6, refundKey: 'rr-refund-88175' })
  failed.decision!.financeDecidedAt = hoursAgo(4)

  // Store credit refund, already issued.
  const credit = queued({ n: '00668', qr: 'rr-qr-0668ab07', item: 'Kurta set', category: 'apparel', pricePaise: 235_000, grade: 'A', customer: 'Ishita Roy', payoutLabel: 'Store credit', waitingHours: 20, refundKey: 'rr-refund-87911' })
  credit.refundMethod = 'store_credit'
  credit.status = 'refunded'
  credit.previousStatus = 'inspected'
  credit.history.push({ status: 'refunded', at: hoursAgo(20) })
  credit.decision!.requiresFinanceApproval = false
  credit.decision!.refund = { paidAt: hoursAgo(20), reference: 'SC-5102' }

  // Seeded bedsheet refund needs a key for its ledger row.
  return Object.fromEntries([...list, failed, credit].map((r) => [r.qrCode, r]))
}

function seed(): Record<string, MockReturn> {
  return { ...baseSeeds(), ...queueSeeds() }
}

export type AuditEntry = {
  id: string
  at: string
  actor: 'warehouse_agent' | 'finance_reviewer' | 'system'
  returnId: string
  from: ReturnStatus
  to: ReturnStatus
  note?: string
}

type MockRefund = Omit<RefundRecord, 'rmaNumber' | 'itemName'>

let returns = seed()
let auditLog: AuditEntry[] = []
let { refunds, journal }: { refunds: MockRefund[]; journal: LedgerEntry[] } = seedLedger(returns)
let journalSeq = 5102
/** Refunds already paid at reset, so tests can count only new payments. */
const paidIds = (list: MockRefund[]) => new Set(list.filter((r) => r.status === 'succeeded').map((r) => r.refundId))
let seededRefundIds = paidIds(refunds)
let idempotency = new Map<string, { fingerprint: string; response: InspectionDecisionResponse }>()
let inspectedToday = 37
let seq = 0

/** Reset all mock state (used by tests). */
export function resetInspectionMock() {
  financeReplays = new Map()
  retryReplays = new Map()
  refundKeySeq = 88_300
  returns = seed()
  auditLog = []
  ;({ refunds, journal } = seedLedger(returns))
  seededRefundIds = paidIds(refunds)
  journalSeq = 5102
  resetMockGateway()
  idempotency = new Map()
  inspectedToday = 37
  seq = 0
}

export const getMockAuditLog = () => [...auditLog]
/** Refunds paid since the last reset: one per return, never more. */
export const getNewPayments = () =>
  refunds
    .filter((r) => r.status === 'succeeded' && !seededRefundIds.has(r.refundId))
    .map((r) => ({ returnId: r.returnId, amountPaise: r.amountPaise, idempotencyKey: r.idempotencyKey, method: r.method }))
export const getMockJournal = () => [...journal]
export const getMockRefunds = () => structuredClone(refunds)

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
      if (!(REJECT_REASONS as readonly string[]).includes(body.reason)) {
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
      inspectedBy: MOCK_AGENT,
      refundKey: null,
      refund: null,
    }

    let outcome: InspectionOutcome
    if (body.decision === 'reject') {
      transition(ret, 'rejected', 'warehouse_agent', auditNote)
      outcome = 'rejected'
    } else if (needsApproval) {
      // Refund exists (with its key) but waits for finance before any payment.
      ret.decision.refundKey = createRefund(ret).idempotencyKey
      outcome = 'sent_to_finance'
    } else {
      const refund = createRefund(ret)
      ret.decision.refundKey = refund.idempotencyKey
      payRefund(ret, refund, 'system')
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
    requiresFinanceApproval: d && ret.status !== 'rejected' ? d.requiresFinanceApproval : null,
    inspection:
      d && d.decision !== 'reject' && ret.status !== 'rejected'
        ? {
            decision: d.decision as 'accept' | 'partial_accept',
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

// ─── Refunds, gateway and ledger ───────────────────────────────────────────

function methodOf(ret: MockReturn) {
  return ret.refundMethod === 'store_credit' ? 'Store credit' : ret.payoutLabel.startsWith('UPI') ? 'UPI' : 'Card'
}

let refundKeySeq = 88_300

/** One refund per return. Its key is created here and reused by every attempt and retry. */
function createRefund(ret: MockReturn): MockRefund {
  if (refunds.some((r) => r.returnId === ret.id)) {
    throw new ApiError(409, 'A refund already exists for this return.', 'DUPLICATE_REFUND')
  }
  const now = new Date().toISOString()
  const refund: MockRefund = {
    refundId: `rfd_${ret.id}`,
    returnId: ret.id,
    amountPaise: ret.decision!.refundAmountPaise,
    method: methodOf(ret),
    status: 'pending',
    attempts: 0,
    idempotencyKey: `rr-refund-${++refundKeySeq}`,
    reference: null,
    lastError: null,
    createdAt: now,
    updatedAt: now,
  }
  refunds.push(refund)
  return refund
}

/** Double entry: Dr returns_expense / Cr gateway_clearing (or store_credit_liability). */
function postJournal(ret: MockReturn, refund: MockRefund, at: string) {
  const storeCredit = refund.method === 'Store credit'
  const journalId = `J-${++journalSeq}`
  const type = storeCredit ? 'STORE_CREDIT' : 'REFUND'
  journal.push(
    { journalId, rmaNumber: ret.rmaNumber, account: 'returns_expense', debitPaise: refund.amountPaise, creditPaise: 0, type, postedAt: at },
    {
      journalId,
      rmaNumber: ret.rmaNumber,
      account: storeCredit ? 'store_credit_liability' : 'gateway_clearing',
      debitPaise: 0,
      creditPaise: refund.amountPaise,
      type,
      postedAt: at,
    },
  )
}

/**
 * Pay through the gateway: 1 attempt + 3 automatic retries, always with the refund's key.
 * Ledger rows are posted only after the gateway confirms. Store credit is issued internally.
 */
function payRefund(ret: MockReturn, refund: MockRefund, actor: AuditEntry['actor']) {
  if (refund.status === 'succeeded') return
  let reference: string | null = null
  if (refund.method === 'Store credit') {
    refund.attempts += 1
    reference = `SC-${journalSeq + 1}`
  } else {
    for (let i = 0; i < GATEWAY_MAX_ATTEMPTS && !reference; i++) {
      refund.attempts += 1
      try {
        reference = charge(refund.idempotencyKey, refund.amountPaise).reference
      } catch (e) {
        if (!(e instanceof GatewayTimeoutError)) throw e
        refund.lastError = 'Gateway timed out'
      }
    }
  }
  const now = new Date().toISOString()
  refund.updatedAt = now
  if (!reference) {
    refund.status = 'failed'
    return
  }
  refund.status = 'succeeded'
  refund.reference = reference
  refund.lastError = null
  postJournal(ret, refund, now)
  transition(ret, 'refunded', actor, `Refund paid ${reference}`)
  ret.decision!.refund = { paidAt: now, reference }
}

/** Seed refunds + journal that match the seeded returns. */
function seedLedger(all: Record<string, MockReturn>): { refunds: MockRefund[]; journal: LedgerEntry[] } {
  const list = Object.values(all)
  const out: MockRefund[] = []
  const lines: LedgerEntry[] = []
  for (const r of list) {
    const d = r.decision
    if (!d || d.decision === 'reject' || !d.refundKey) continue
    const paid = r.status === 'refunded' && d.refund
    const failed = r.id === 'ret_00701'
    out.push({
      refundId: `rfd_${r.id}`,
      returnId: r.id,
      amountPaise: d.refundAmountPaise,
      method: methodOf(r),
      status: paid ? 'succeeded' : failed ? 'failed' : 'pending',
      attempts: paid ? 1 : failed ? GATEWAY_MAX_ATTEMPTS : 0,
      idempotencyKey: d.refundKey,
      reference: paid ? d.refund!.reference : null,
      lastError: failed ? 'Gateway timed out' : null,
      createdAt: d.decidedAt,
      updatedAt: paid ? d.refund!.paidAt : failed ? d.financeDecidedAt! : d.decidedAt,
    })
    if (paid) {
      const storeCredit = r.refundMethod === 'store_credit'
      const journalId = r.id === 'ret_00668' ? 'J-5102' : 'J-5098'
      const type = storeCredit ? 'STORE_CREDIT' : 'REFUND'
      lines.push(
        { journalId, rmaNumber: r.rmaNumber, account: 'returns_expense', debitPaise: d.refundAmountPaise, creditPaise: 0, type, postedAt: d.refund!.paidAt },
        { journalId, rmaNumber: r.rmaNumber, account: storeCredit ? 'store_credit_liability' : 'gateway_clearing', debitPaise: 0, creditPaise: d.refundAmountPaise, type, postedAt: d.refund!.paidAt },
      )
    }
  }
  return { refunds: out, journal: lines }
}

const toRecord = (r: MockRefund): RefundRecord => {
  const ret = Object.values(returns).find((x) => x.id === r.returnId)!
  return structuredClone({ ...r, rmaNumber: ret.rmaNumber, itemName: ret.item.name })
}

let retryReplays = new Map<string, RefundRecord>()

export const refundsMock = {
  async getRefunds(): Promise<RefundRecord[]> {
    await wait()
    return refunds.map(toRecord).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
  },

  async getLedger(): Promise<LedgerResponse> {
    await wait()
    const entries = [...journal].sort(
      (a, b) => b.postedAt.localeCompare(a.postedAt) || b.journalId.localeCompare(a.journalId),
    )
    const debitPaise = entries.reduce((s, e) => s + e.debitPaise, 0)
    const creditPaise = entries.reduce((s, e) => s + e.creditPaise, 0)
    return structuredClone({ entries, totals: { debitPaise, creditPaise }, balanced: debitPaise === creditPaise })
  },

  /** Retry a failed refund. Must use the refund's own key; never pays twice. */
  async retryRefund(refundId: string, idempotencyKey: string): Promise<RefundRecord> {
    await wait()
    const refund = refunds.find((r) => r.refundId === refundId)
    if (!refund) throw new ApiError(404, 'Refund not found.', 'REFUND_NOT_FOUND')
    if (refund.idempotencyKey !== idempotencyKey) {
      throw new ApiError(422, "This key doesn't belong to this refund.", 'IDEMPOTENCY_KEY_MISMATCH')
    }
    if (refund.status === 'succeeded') return retryReplays.get(refundId) ?? toRecord(refund)
    if (refund.status !== 'failed') {
      throw new ApiError(409, 'Only failed refunds can be retried.', 'NOT_RETRYABLE')
    }
    const ret = Object.values(returns).find((x) => x.id === refund.returnId)!
    payRefund(ret, refund, 'finance_reviewer')
    const record = toRecord(refund)
    if (record.status === 'succeeded') retryReplays.set(refundId, record)
    return record
  },
}

// ─── Finance ────────────────────────────────────────────────────────────────

const awaitingFinance = (r: MockReturn) =>
  r.status === 'inspected' &&
  r.decision?.requiresFinanceApproval === true &&
  r.decision.decision !== 'reject' &&
  !r.decision.financeDecidedAt

function toQueueItem(r: MockReturn): FinanceQueueItem {
  const d = r.decision!
  const details = toDetails(r)
  return {
    returnId: r.id,
    rmaNumber: r.rmaNumber,
    customer: {
      name: r.customer.name,
      flaggedForReview: details.customer.flaggedForReview,
      returnsLast30Days: details.customer.returnsLast30Days,
    },
    item: { name: r.item.name, pricePaise: r.item.pricePaise },
    decision: d.decision as 'accept' | 'partial_accept',
    grade: d.grade,
    refundAmountPaise: d.refundAmountPaise,
    refundMethod: r.refundMethod,
    payoutMethod: r.refundMethod === 'store_credit' ? 'Store credit' : r.payoutLabel.split(' ')[0],
    inspectedBy: d.inspectedBy,
    inspectedAt: d.decidedAt,
    refundKey: d.refundKey!,
  }
}

/** Replays for approve/decline retries, keyed by the refund's idempotency key. */
let financeReplays = new Map<string, { action: 'approve' | 'decline'; response: FinanceDecisionResponse }>()

function financeTarget(returnId: string, key: string, action: 'approve' | 'decline') {
  if (!key) throw new ApiError(400, 'Missing Idempotency-Key header.', 'IDEMPOTENCY_KEY_REQUIRED')
  const replay = financeReplays.get(key)
  if (replay) {
    if (replay.action !== action || replay.response.returnId !== returnId) {
      throw new ApiError(422, 'This refund was already decided differently.', 'IDEMPOTENCY_KEY_REUSED')
    }
    return { replay: structuredClone(replay.response) }
  }
  const ret = Object.values(returns).find((r) => r.id === returnId)
  if (!ret) throw new ApiError(404, 'Return not found.', 'RETURN_NOT_FOUND')
  if (!awaitingFinance(ret)) {
    throw new ApiError(409, `${ret.rmaNumber} is no longer waiting for approval.`, 'NOT_AWAITING_APPROVAL')
  }
  if (ret.decision!.refundKey !== key) {
    throw new ApiError(422, "This key doesn't belong to this refund.", 'IDEMPOTENCY_KEY_MISMATCH')
  }
  return { ret }
}

export const financeMock = {
  async getQueue(): Promise<FinanceQueueItem[]> {
    await wait()
    return Object.values(returns)
      .filter(awaitingFinance)
      .map(toQueueItem)
      .sort((a, b) => a.inspectedAt.localeCompare(b.inspectedAt))
  },

  async approveRefund(returnId: string, idempotencyKey: string): Promise<FinanceDecisionResponse> {
    await wait()
    const t = financeTarget(returnId, idempotencyKey, 'approve')
    if (t.replay) return t.replay
    const ret = t.ret!
    const d = ret.decision!

    const refund = refunds.find((r) => r.returnId === ret.id)!
    d.financeDecidedAt = new Date().toISOString()
    payRefund(ret, refund, 'finance_reviewer')

    const response: FinanceDecisionResponse = {
      returnId: ret.id,
      outcome: refund.status === 'succeeded' ? 'paid' : 'payment_failed',
      status: ret.status,
      refund:
        refund.status === 'succeeded'
          ? { amountPaise: refund.amountPaise, reference: refund.reference!, paidAt: refund.updatedAt }
          : null,
      decidedAt: d.financeDecidedAt,
    }
    financeReplays.set(idempotencyKey, { action: 'approve', response })
    return structuredClone(response)
  },

  async declineRefund(
    returnId: string,
    body: DeclineRefundRequest,
    idempotencyKey: string,
  ): Promise<FinanceDecisionResponse> {
    await wait()
    const t = financeTarget(returnId, idempotencyKey, 'decline')
    if (t.replay) return t.replay
    const ret = t.ret!
    if (!body.note.trim()) {
      throw new ApiError(422, 'Add a reason the customer will see.', 'NOTES_REQUIRED')
    }
    const decidedAt = new Date().toISOString()
    transition(ret, 'rejected', 'finance_reviewer', body.note.trim())
    ret.decision!.rejectReason = FINANCE_DECLINE_REASON
    ret.decision!.financeDecidedAt = decidedAt
    const pending = refunds.find((r) => r.returnId === ret.id)
    if (pending) Object.assign(pending, { status: 'cancelled', updatedAt: decidedAt })
    ret.decision!.notes = body.note.trim()
    ret.decision!.decidedAt = decidedAt

    const response: FinanceDecisionResponse = {
      returnId: ret.id,
      outcome: 'declined',
      status: ret.status,
      refund: null,
      decidedAt,
    }
    financeReplays.set(idempotencyKey, { action: 'decline', response })
    return structuredClone(response)
  },
}
