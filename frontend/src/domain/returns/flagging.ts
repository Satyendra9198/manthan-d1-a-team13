import { daysBetween } from './dates'

/** Business rule: more than 3 returns in 30 days flags the customer for review. */
export const FLAG_WINDOW_DAYS = 30
export const FLAG_MAX_RETURNS = 3

/** Returns requested in the last 30 days, counting today as day 1 (so days 0–29 ago). */
export function countRecentReturns(requestedOn: string[], today: string): number {
  return requestedOn.filter((d) => {
    const ago = daysBetween(d, today)
    return ago >= 0 && ago < FLAG_WINDOW_DAYS
  }).length
}

export function isFlaggedForReview(requestedOn: string[], today: string): boolean {
  return countRecentReturns(requestedOn, today) > FLAG_MAX_RETURNS
}

const DAY_MS = 86_400_000

/** Return requests (ISO timestamps) in the 30 days before `now`, oldest first. */
function recentRequests(requestedAt: string[], now: Date): string[] {
  const from = now.getTime() - FLAG_WINDOW_DAYS * DAY_MS
  return requestedAt
    .filter((iso) => {
      const t = new Date(iso).getTime()
      return t > from && t <= now.getTime()
    })
    .sort()
}

/** When the flag was raised: the request that took the 30-day count over 3. null if not over. */
export function flagRaisedAt(requestedAt: string[], now: Date): string | null {
  return recentRequests(requestedAt, now)[FLAG_MAX_RETURNS] ?? null
}

/**
 * Is the flag active right now? More than 3 returns in 30 days, unless a manager cleared it
 * and no new return has arrived since. A new return that keeps the count over 3 raises it again.
 */
export function isFlagActive(requestedAt: string[], now: Date, clearedAt: string | null = null): boolean {
  const recent = recentRequests(requestedAt, now)
  if (recent.length <= FLAG_MAX_RETURNS) return false
  return !clearedAt || recent.some((iso) => iso > clearedAt)
}
