import type { Order } from "./orders"

/**
 * Sample orders used until the backend exists.
 * Try: RR-10482 + ananya.rao@example.com, or RR-10517 + rahul.mehta@example.com.
 */
export const MOCK_ORDERS: Order[] = [
  {
    orderNumber: "RR-10482",
    email: "ananya.rao@example.com",
    customerName: "Ananya Rao",
    deliveredAt: "2026-11-06",
    currency: "INR",
    payment: { method: "UPI", account: "ananya@okbank" },
    items: [
      {
        id: "item-1",
        name: "Linen shirt",
        category: "Apparel",
        price: 1899,
        eligibility: { returnable: true, returnBy: "2026-12-06" },
      },
      {
        id: "item-2",
        name: "Ceramic mug set",
        category: "Home",
        price: 1250,
        eligibility: { returnable: true, returnBy: "2026-12-06" },
      },
      {
        id: "item-3",
        name: "Wireless earbuds Pro",
        category: "Electronics",
        price: 6499,
        eligibility: {
          returnable: false,
          reason: "Electronics have a 10-day return window, which closed on 16 Nov 2026.",
        },
      },
      {
        id: "item-4",
        name: "Cotton briefs, 3-pack",
        category: "Innerwear",
        price: 799,
        eligibility: {
          returnable: false,
          reason: "Innerwear can’t be returned for hygiene reasons.",
        },
      },
    ],
  },
  {
    orderNumber: "RR-10517",
    email: "rahul.mehta@example.com",
    customerName: "Rahul Mehta",
    deliveredAt: "2026-11-15",
    currency: "INR",
    payment: { method: "Card", account: "•••• 4242" },
    items: [
      {
        id: "item-5",
        name: "Denim jacket",
        category: "Apparel",
        price: 3499,
        eligibility: { returnable: true, returnBy: "2026-12-15" },
      },
      {
        id: "item-6",
        name: "Steel water bottle",
        category: "Home",
        price: 699,
        eligibility: { returnable: true, returnBy: "2026-12-15" },
      },
    ],
  },
]
