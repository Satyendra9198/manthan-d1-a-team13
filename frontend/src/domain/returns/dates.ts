/** Date helpers that work on calendar days ("YYYY-MM-DD"), so time zones can't shift a boundary. */

const DAY_MS = 86_400_000

function toUtcDay(date: string): number {
  const [y, m, d] = date.slice(0, 10).split('-').map(Number)
  return Date.UTC(y, m - 1, d)
}

/** Whole calendar days from `from` to `to` (same day = 0). */
export function daysBetween(from: string, to: string): number {
  return Math.round((toUtcDay(to) - toUtcDay(from)) / DAY_MS)
}

export function addDays(date: string, days: number): string {
  return new Date(toUtcDay(date) + days * DAY_MS).toISOString().slice(0, 10)
}

export function todayIso(now: Date = new Date()): string {
  return now.toISOString().slice(0, 10)
}

const display = new Intl.DateTimeFormat('en-IN', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  timeZone: 'UTC',
})

/** "2026-01-31" → "31 Jan 2026" */
export function formatDay(date: string): string {
  return display.format(new Date(toUtcDay(date)))
}
