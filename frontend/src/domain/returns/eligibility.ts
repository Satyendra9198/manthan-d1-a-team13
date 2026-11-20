import { addDays, daysBetween, formatDay } from './dates'

export const PRODUCT_CATEGORIES = [
  'apparel',
  'footwear',
  'home',
  'electronics',
  'innerwear',
  'perishables',
] as const

export type ProductCategory = (typeof PRODUCT_CATEGORIES)[number]

export const CATEGORY_LABELS: Record<ProductCategory, string> = {
  apparel: 'Apparel',
  footwear: 'Footwear',
  home: 'Home',
  electronics: 'Electronics',
  innerwear: 'Innerwear',
  perishables: 'Perishables',
}

/** Business rule: 30 days standard, electronics 10, innerwear and perishables never. */
export const RETURN_WINDOW_DAYS: Record<ProductCategory, number | null> = {
  apparel: 30,
  footwear: 30,
  home: 30,
  electronics: 10,
  innerwear: null,
  perishables: null,
}

export type IneligibleCode = 'NOT_RETURNABLE' | 'NOT_DELIVERED' | 'WINDOW_EXPIRED'

export type EligibilityResult =
  | { eligible: true; windowDays: number; lastDay: string; daysLeft: number; message: string }
  | { eligible: false; code: IneligibleCode; message: string }

const NOT_RETURNABLE_MESSAGE: Partial<Record<ProductCategory, string>> = {
  innerwear: "Innerwear can't be returned for hygiene reasons.",
  perishables: "Perishable items can't be returned once delivered.",
}

/**
 * Is this item returnable on `today`? The window counts calendar days from delivery:
 * delivered 1 Jan with a 30-day window → returnable through 31 Jan (day 30), not 1 Feb (day 31).
 */
export function checkEligibility(
  item: { category: ProductCategory; deliveredOn: string | null },
  today: string,
): EligibilityResult {
  const windowDays = RETURN_WINDOW_DAYS[item.category]

  if (windowDays === null) {
    return {
      eligible: false,
      code: 'NOT_RETURNABLE',
      message: NOT_RETURNABLE_MESSAGE[item.category] ?? "This item can't be returned.",
    }
  }

  if (!item.deliveredOn || daysBetween(item.deliveredOn, today) < 0) {
    return {
      eligible: false,
      code: 'NOT_DELIVERED',
      message: "This item hasn't been delivered yet, so it can't be returned.",
    }
  }

  const lastDay = addDays(item.deliveredOn, windowDays)
  const daysLeft = daysBetween(today, lastDay)

  if (daysLeft < 0) {
    return {
      eligible: false,
      code: 'WINDOW_EXPIRED',
      message: `The ${windowDays}-day return window for ${CATEGORY_LABELS[item.category].toLowerCase()} ended on ${formatDay(lastDay)}.`,
    }
  }

  return {
    eligible: true,
    windowDays,
    lastDay,
    daysLeft,
    message:
      daysLeft === 0
        ? 'Last day to return this item is today.'
        : `Returnable until ${formatDay(lastDay)} (${daysLeft} ${daysLeft === 1 ? 'day' : 'days'} left).`,
  }
}
