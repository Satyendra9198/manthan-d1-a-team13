/**
 * Returns API: the rules and types the wizard uses, submitting a return, and
 * tracking it afterwards. The backend should accept the same reason codes and
 * enforce the same limits.
 */
import { MOCK_ORDERS } from "./mock-orders"
import { findMockReturn, MOCK_LABEL, rememberMockReturn } from "./mock-returns"

/** Reasons a customer can give. `code` is what the API will receive. */
export const RETURN_REASONS = [
  { code: "too_large", label: "Too large" },
  { code: "too_small", label: "Too small" },
  { code: "damaged", label: "Damaged on arrival" },
  { code: "not_as_described", label: "Not as described" },
  { code: "changed_mind", label: "Changed my mind" },
] as const

export type ReturnReasonCode = (typeof RETURN_REASONS)[number]["code"]

export const MAX_PHOTOS = 3
export const MAX_PHOTO_MB = 5
export const MAX_PHOTO_BYTES = MAX_PHOTO_MB * 1024 * 1024
export const PHOTO_TYPES: readonly string[] = ["image/jpeg", "image/png"]
export const NOTES_MAX_LENGTH = 500

/** What the customer tells us about one item they're returning. */
export type ItemReturnDetails = {
  reason?: ReturnReasonCode
  notes: string
  /** Kept in the browser until the return is submitted. */
  photos: File[]
}

export const EMPTY_ITEM_DETAILS: ItemReturnDetails = { notes: "", photos: [] }

/** A reason and at least one photo are required; notes are optional. */
export function isItemDetailsComplete(details: ItemReturnDetails): boolean {
  return details.reason !== undefined && details.photos.length > 0
}

/** Where the refund goes. Keys are what the API will receive. */
export const REFUND_METHODS = {
  original_payment: "Original payment",
  store_credit: "Store credit",
} as const

export type RefundMethod = keyof typeof REFUND_METHODS

export function reasonLabel(code: ReturnReasonCode): string {
  return RETURN_REASONS.find((reason) => reason.code === code)?.label ?? code
}

/** Everything the customer filled in, ready to send. */
export type ReturnRequest = {
  orderNumber: string
  email: string
  refundMethod: RefundMethod
  items: {
    itemId: string
    reason: ReturnReasonCode
    notes: string
    photos: File[]
  }[]
}

/** The normal path of a return, in order. A return can also end as "rejected". */
export const RETURN_STAGES = [
  "requested",
  "approved",
  "in_transit",
  "received",
  "inspected",
  "refunded",
] as const

export type ReturnStage = (typeof RETURN_STAGES)[number] | "rejected"

export type ReturnEvent = {
  stage: ReturnStage
  /** ISO date-time it happened. */
  at: string
  /** Short detail, e.g. "label ready", "picked up by courier", "grade C". */
  note?: string
}

/** A submitted return, as the customer tracks it. */
export type TrackedReturn = {
  /** Return number shown to the customer, e.g. "RMA-2026-00731". */
  rmaNumber: string
  orderNumber: string
  email: string
  /** Prices in paise. */
  items: { name: string; pricePaise: number }[]
  refundMethod: RefundMethod
  /** The order's payment, for refunds to the original payment method. */
  payment: { method: string; account: string }
  /** What has happened so far, oldest first. The last one is where the return is now. */
  events: ReturnEvent[]
  /** Present once the return is approved. */
  label?: { qrCodeUrl: string; downloadUrl: string }
  /** Present once the refund is paid. */
  refund?: {
    amountPaise: number
    /** ISO date-time. */
    paidAt: string
    /** Payment reference the customer can quote to their bank. */
    reference: string
    /** Why the amount is less than the items' price, when it is. */
    adjustment?: string
  }
  /** Present when the return was rejected. */
  rejection?: { reason: string; details: string }
}

export function currentStage(tracked: TrackedReturn): ReturnStage {
  return tracked.events[tracked.events.length - 1].stage
}

/** Fake network latency so loading states are visible. None in tests. */
const MOCK_DELAY_MS = import.meta.env.MODE === "test" ? 0 : 800
const mockDelay = () => new Promise((resolve) => setTimeout(resolve, MOCK_DELAY_MS))

/**
 * Submit the return request. Resolves with the created return.
 *
 * MOCK: the backend isn't ready, so this creates an approved return with a
 * sample label and keeps it for this browser session. To integrate, POST the
 * request as multipart/form-data (the photos are Files) to the returns
 * endpoint and return the created return.
 */
export async function submitReturn(request: ReturnRequest): Promise<TrackedReturn> {
  await mockDelay()
  const order = MOCK_ORDERS.find((candidate) => candidate.orderNumber === request.orderNumber)
  const itemIds = request.items.map((item) => item.itemId)
  if (!order || itemIds.length === 0) throw new Error("Invalid return request")

  const now = new Date().toISOString()
  const tracked: TrackedReturn = {
    rmaNumber: `RMA-${new Date().getFullYear()}-${String(Math.floor(Math.random() * 100000)).padStart(5, "0")}`,
    orderNumber: order.orderNumber,
    email: order.email,
    items: order.items
      .filter((item) => itemIds.includes(item.id))
      .map((item) => ({ name: item.name, pricePaise: item.pricePaise })),
    refundMethod: request.refundMethod,
    payment: order.payment,
    events: [
      { stage: "requested", at: now },
      { stage: "approved", at: now, note: "label ready" },
    ],
    label: MOCK_LABEL,
  }
  rememberMockReturn(tracked)
  return tracked
}

/**
 * Look up a return by its RMA number. Resolves with null when there's no such return.
 *
 * MOCK: reads the sample returns plus those submitted in this session. To
 * integrate, GET it from the returns endpoint (404 → null).
 */
export async function getReturn(rmaNumber: string): Promise<TrackedReturn | null> {
  await mockDelay()
  return findMockReturn(rmaNumber.trim().toUpperCase())
}
