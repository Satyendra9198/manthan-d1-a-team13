/**
 * Synthetic data seed: 2,000 orders, 40 products in 6 categories, 500 customers.
 * Deterministic: the same seed + `now` always gives the same data, so dashboards and
 * tests are reproducible. Pure TypeScript, so the backend seed script can reuse it.
 */
import {
  RETURN_REASONS,
  RETURN_WINDOW_DAYS,
  type AnalyticsOrder,
  type AnalyticsReturn,
  type ProductCategory,
  type ReturnReasonCode,
} from '@/domain/returns'

/** Small, fast, seedable PRNG (mulberry32). */
export function mulberry32(seed: number) {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4_294_967_296
  }
}

export type SynthProduct = { id: string; name: string; category: ProductCategory; pricePaise: number }
export type SynthCustomer = { id: string; name: string; email: string; customerSince: string }

const PRODUCTS: Record<ProductCategory, { names: string[]; price: [number, number] }> = {
  apparel: {
    names: ['Linen shirt', 'Denim jacket', 'Kurta set', 'Cotton tee', 'Chinos', 'Maxi dress', 'Hoodie', 'Saree', 'Polo shirt', 'Blazer', 'Track pants', 'Denim shorts'],
    price: [499, 4999],
  },
  footwear: { names: ['Running shoes', 'Sneakers', 'Sandals', 'Loafers', 'Boots', 'Flip flops'], price: [399, 7999] },
  electronics: {
    names: ['Wireless earbuds', 'Smartwatch S3', 'Bluetooth speaker', 'Power bank', '55" 4K TV', 'Smartphone X2', 'Laptop stand', 'Headphones'],
    price: [999, 49999],
  },
  home: {
    names: ['Cotton bedsheet', 'Sofa cover set', 'Espresso machine', 'Table lamp', 'Curtains', 'Cookware set', 'Cushion covers', 'Wall clock'],
    price: [399, 24999],
  },
  innerwear: { names: ['Briefs pack', 'Sports bra', 'Vest pack'], price: [299, 1499] },
  perishables: { names: ['Coffee beans', 'Dry fruit box', 'Tea sampler'], price: [249, 1999] },
}

/** Share of orders per category, and the chance an order in it is returned. */
const CATEGORY_MIX: Record<ProductCategory, { share: number; returnRate: number }> = {
  apparel: { share: 0.28, returnRate: 0.146 },
  electronics: { share: 0.18, returnRate: 0.112 },
  footwear: { share: 0.14, returnRate: 0.097 },
  home: { share: 0.2, returnRate: 0.061 },
  innerwear: { share: 0.1, returnRate: 0 },
  perishables: { share: 0.1, returnRate: 0 },
}

const REASON_WEIGHTS: Record<ReturnReasonCode, number> = {
  wrong_size: 34,
  damaged_in_transit: 21,
  not_as_described: 18,
  changed_mind: 15,
  defective: 12,
}

const FIRST = ['Aarav', 'Priya', 'Kabir', 'Neha', 'Arjun', 'Ananya', 'Rohan', 'Ishita', 'Vikram', 'Meera', 'Aditya', 'Sana', 'Karan', 'Pooja', 'Dev', 'Riya', 'Nikhil', 'Tara', 'Yash', 'Diya']
const LAST = ['Sharma', 'Singh', 'Gupta', 'Mehta', 'Rao', 'Iyer', 'Nair', 'Das', 'Khan', 'Roy', 'Shah', 'Verma', 'Jain', 'Patel', 'Reddy', 'Kapoor', 'Bose', 'Menon', 'Joshi', 'Malhotra']

/** Customers who return a lot. The first three match the dashboard design. */
const SERIAL_RETURNERS = ['Rahul Verma', 'Sneha Iyer', 'Mohit Jain', 'Farah Ali', 'Gaurav Sen', 'Leela Pillai', 'Omar Sheikh', 'Kiran Bedi', 'Zoya Mirza', 'Varun Khanna', 'Esha Gill', 'Tanvi Rao']

export type SynthReturn = AnalyticsReturn & {
  rmaNumber: string
  productName: string
  /** null while not yet inspected. */
  grade: 'A' | 'B' | 'C' | null
}

export type SyntheticDataset = {
  products: SynthProduct[]
  customers: SynthCustomer[]
  orders: (AnalyticsOrder & { productId: string; pricePaise: number })[]
  returns: SynthReturn[]
}

export function generateDataset({
  seed = 2026,
  now = new Date(),
  orderCount = 2000,
  customerCount = 500,
  days = 30,
}: { seed?: number; now?: Date; orderCount?: number; customerCount?: number; days?: number } = {}): SyntheticDataset {
  const rand = mulberry32(seed)
  const pick = <T,>(list: readonly T[]) => list[Math.floor(rand() * list.length)]
  const between = (lo: number, hi: number) => lo + rand() * (hi - lo)
  const nowMs = now.getTime()
  const iso = (ms: number) => new Date(ms).toISOString()

  // 40 products across 6 categories
  const products: SynthProduct[] = []
  for (const [category, def] of Object.entries(PRODUCTS) as [ProductCategory, (typeof PRODUCTS)[ProductCategory]][]) {
    for (const name of def.names) {
      const rupees = Math.round(between(def.price[0], def.price[1]) / 10) * 10 - 1
      products.push({ id: `prd_${products.length + 1}`, name, category, pricePaise: rupees * 100 })
    }
  }

  // 500 customers; the first ones are the serial returners
  const customers: SynthCustomer[] = []
  for (let i = 0; i < customerCount; i++) {
    const name = SERIAL_RETURNERS[i] ?? `${pick(FIRST)} ${pick(LAST)}`
    const id = `cus_${String(i + 1).padStart(4, '0')}`
    // Derived from the index (no extra random draws), so the rest of the data stays stable.
    const since = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() - (14 + ((i * 7) % 30)), 1))
    customers.push({
      id,
      name,
      // Serial returners have unique names; others get a number to keep emails unique.
      email: `${name.toLowerCase().replace(/[^a-z]+/g, '.')}${i < SERIAL_RETURNERS.length ? '' : `.${i + 1}`}@example.com`,
      customerSince: since.toISOString(),
    })
  }
  const serial = new Set(customers.slice(0, SERIAL_RETURNERS.length).map((c) => c.id))

  const categoryRoll = () => {
    let r = rand()
    for (const [c, m] of Object.entries(CATEGORY_MIX) as [ProductCategory, { share: number }][]) {
      if ((r -= m.share) < 0) return c
    }
    return 'apparel' as ProductCategory
  }
  const reasonRoll = (): ReturnReasonCode => {
    const total = Object.values(REASON_WEIGHTS).reduce((a, b) => a + b, 0)
    let r = rand() * total
    for (const reason of RETURN_REASONS) if ((r -= REASON_WEIGHTS[reason]) < 0) return reason
    return 'wrong_size'
  }

  const orders: SyntheticDataset['orders'] = []
  const returns: SynthReturn[] = []

  for (let i = 0; i < orderCount; i++) {
    // Serial returners get 6 orders each in returnable categories.
    const serialIndex = Math.floor(i / 6)
    const isSerial = serialIndex < SERIAL_RETURNERS.length
    const customer = isSerial ? customers[serialIndex] : customers[SERIAL_RETURNERS.length + Math.floor(rand() * (customerCount - SERIAL_RETURNERS.length))]
    const category = isSerial ? pick(['apparel', 'footwear', 'home'] as const) : categoryRoll()
    const product = pick(products.filter((p) => p.category === category))
    const orderedMs = nowMs - between(0, days) * 86_400_000
    const order = {
      id: `ord_${String(i + 1).padStart(5, '0')}`,
      customerId: customer.id,
      productId: product.id,
      category,
      pricePaise: product.pricePaise,
      orderedAt: iso(orderedMs),
    }
    orders.push(order)

    const window = RETURN_WINDOW_DAYS[category]
    const chance = serial.has(customer.id) ? 0.85 : CATEGORY_MIX[category].returnRate
    if (window === null || rand() >= chance) continue

    const deliveredMs = orderedMs + between(1, 3) * 86_400_000
    const requestedMs = deliveredMs + between(0, Math.min(window, 6)) * 86_400_000
    if (requestedMs > nowMs) continue // can't ask for a return in the future

    const rejected = rand() < 0.07
    const cycleDays = 1.5 + rand() * 5.4 // mean ≈ 4.2 days
    const closedMs = requestedMs + cycleDays * 86_400_000
    const open = closedMs > nowMs
    const gradeC = rand() < 0.1
    const refundPaise = rejected ? 0 : gradeC ? Math.floor(product.pricePaise / 2) : product.pricePaise

    // Mock gateway: each attempt fails ~0.8% of the time, 1 + 3 retries.
    const attempts: boolean[] = []
    if (!open && !rejected) {
      for (let a = 0; a < 4; a++) {
        const ok = rand() >= 0.008
        attempts.push(ok)
        if (ok) break
      }
    }

    const n = returns.length + 1
    returns.push({
      id: `ret_s${String(n).padStart(4, '0')}`,
      rmaNumber: `RMA-2026-${20000 + n}`,
      productName: product.name,
      grade: open ? null : gradeC ? 'C' : n % 3 === 0 ? 'B' : 'A',
      orderId: order.id,
      customerId: customer.id,
      category,
      reason: reasonRoll(),
      requestedAt: iso(requestedMs),
      closedAt: open ? null : iso(closedMs),
      outcome: open ? 'open' : rejected ? 'rejected' : 'refunded',
      refundPaise,
      gatewayAttempts: attempts,
    })
  }

  return { products, customers, orders, returns }
}
