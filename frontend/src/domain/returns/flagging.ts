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
