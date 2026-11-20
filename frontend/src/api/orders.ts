import { MOCK_ORDERS } from "./mock-orders"

/** Whether an item can still be returned. Decided by the backend's return policy. */
export type ReturnEligibility =
  | {
      returnable: true
      /** ISO date of the last day the item can be returned. */
      returnBy: string
    }
  | {
      returnable: false
      /** Why not, shown to the customer as-is. */
      reason: string
    }

export type OrderItem = {
  id: string
  name: string
  /** e.g. "Apparel", "Electronics". */
  category: string
  /** Amount paid for this item, in paise (₹1,899 → 189900), like the rest of the API. */
  pricePaise: number
  eligibility: ReturnEligibility
}

export type Order = {
  orderNumber: string
  email: string
  customerName: string
  /** ISO date the order was delivered. */
  deliveredAt: string
  /** How the order was paid, shown as "UPI · ananya@okbank" or "Card · •••• 4242". */
  payment: { method: string; account: string }
  items: OrderItem[]
}

export type FindOrderInput = {
  orderNumber: string
  email: string
}

export type OrderLookupErrorCode = "not_found" | "rate_limited" | "unknown"

export class OrderLookupError extends Error {
  readonly code: OrderLookupErrorCode

  constructor(code: OrderLookupErrorCode) {
    super(`Order lookup failed: ${code}`)
    this.name = "OrderLookupError"
    this.code = code
  }
}

/** Fake network latency so the loading state is visible. None in tests. */
const MOCK_DELAY_MS = import.meta.env.MODE === "test" ? 0 : 600

/**
 * Find an order by its number and the email it was placed with.
 * Rejects with an OrderLookupError.
 *
 * MOCK: the backend isn't ready, so this searches MOCK_ORDERS. To integrate,
 * replace the body with a request to the order-lookup endpoint and map its
 * responses: 404 → "not_found", 429 → "rate_limited", anything else → "unknown".
 * The UI only depends on this function's signature and the types above.
 */
export async function findOrder({ orderNumber, email }: FindOrderInput): Promise<Order> {
  await new Promise((resolve) => setTimeout(resolve, MOCK_DELAY_MS))

  const wantedNumber = orderNumber.trim().toUpperCase()
  const wantedEmail = email.trim().toLowerCase()
  const order = MOCK_ORDERS.find(
    (candidate) =>
      candidate.orderNumber.toUpperCase() === wantedNumber &&
      candidate.email.toLowerCase() === wantedEmail,
  )

  if (!order) throw new OrderLookupError("not_found")
  return order
}
