import type { TrackedReturn } from "./returns"

/** Sample label. The QR image is a placeholder, not a scannable code. */
export const MOCK_LABEL = {
  qrCodeUrl: `${import.meta.env.BASE_URL}mock/return-label-qr.svg`,
  downloadUrl: `${import.meta.env.BASE_URL}mock/return-label-qr.svg`,
}

/**
 * Sample returns, one per status screen:
 *   #/returns/RMA-2026-00731  in transit
 *   #/returns/RMA-2026-00689  refunded (partly)
 *   #/returns/RMA-2026-00702  rejected
 */
const MOCK_RETURNS = new Map<string, TrackedReturn>(
  (
    [
      {
        rmaNumber: "RMA-2026-00731",
        orderNumber: "RR-10482",
        email: "ananya.rao@example.com",
        items: [{ name: "Linen shirt", pricePaise: 189900 }],
        refundMethod: "original_payment",
        payment: { method: "UPI", account: "ananya@okbank" },
        events: [
          { stage: "requested", at: "2026-11-17T10:12:00+05:30" },
          { stage: "approved", at: "2026-11-17T10:12:00+05:30", note: "label ready" },
          { stage: "in_transit", at: "2026-11-19T16:40:00+05:30", note: "picked up by courier" },
        ],
        label: MOCK_LABEL,
      },
      {
        rmaNumber: "RMA-2026-00689",
        orderNumber: "RR-10391",
        email: "ananya.rao@example.com",
        items: [{ name: "Linen shirt", pricePaise: 189900 }],
        refundMethod: "original_payment",
        payment: { method: "UPI", account: "ananya@okbank" },
        events: [
          { stage: "requested", at: "2026-11-17T10:12:00+05:30" },
          { stage: "approved", at: "2026-11-17T10:12:00+05:30" },
          { stage: "in_transit", at: "2026-11-19T16:40:00+05:30" },
          { stage: "received", at: "2026-11-20T14:58:00+05:30" },
          { stage: "inspected", at: "2026-11-20T15:31:00+05:30", note: "grade C" },
          { stage: "refunded", at: "2026-11-20T15:44:00+05:30" },
        ],
        label: MOCK_LABEL,
        refund: {
          amountPaise: 94950,
          paidAt: "2026-11-20T15:44:00+05:30",
          reference: "GW-7781-2026",
          adjustment:
            "At inspection we found a torn seam (condition grade C), so the item was refunded at 50% of its price.",
        },
      },
      {
        rmaNumber: "RMA-2026-00702",
        orderNumber: "RR-10377",
        email: "ananya.rao@example.com",
        items: [{ name: "Denim shorts", pricePaise: 149900 }],
        refundMethod: "original_payment",
        payment: { method: "UPI", account: "ananya@okbank" },
        events: [
          { stage: "requested", at: "2026-11-12T09:40:00+05:30" },
          { stage: "approved", at: "2026-11-12T09:40:00+05:30" },
          { stage: "in_transit", at: "2026-11-14T11:05:00+05:30" },
          { stage: "received", at: "2026-11-18T10:20:00+05:30" },
          { stage: "rejected", at: "2026-11-18T10:41:00+05:30", note: "used or worn" },
        ],
        label: MOCK_LABEL,
        rejection: {
          reason: "used or worn",
          details: "Our inspection found visible wear on the hem and the tags had been removed.",
        },
      },
    ] satisfies TrackedReturn[]
  ).map((tracked) => [tracked.rmaNumber, tracked]),
)

// Returns submitted in this tab survive a page reload (photos aren't kept).
const SESSION_KEY = "returnright.mock-returns"

function readSession(): Record<string, TrackedReturn> {
  try {
    return JSON.parse(sessionStorage.getItem(SESSION_KEY) ?? "{}")
  } catch {
    return {}
  }
}

export function rememberMockReturn(tracked: TrackedReturn) {
  MOCK_RETURNS.set(tracked.rmaNumber, tracked)
  try {
    sessionStorage.setItem(
      SESSION_KEY,
      JSON.stringify({ ...readSession(), [tracked.rmaNumber]: tracked }),
    )
  } catch {
    // Storage unavailable: keep it in memory only.
  }
}

export function findMockReturn(rmaNumber: string): TrackedReturn | null {
  return MOCK_RETURNS.get(rmaNumber) ?? readSession()[rmaNumber] ?? null
}
