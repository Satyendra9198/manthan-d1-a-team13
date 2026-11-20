import type { TrackedReturn } from "@/api/returns"

/**
 * Returns that were just loaded elsewhere (the lookup page, or the confirmation
 * after submitting), so the status page can show them straight away instead of
 * loading them again. Only trusted briefly, since a return's status changes.
 */
const FRESH_FOR_MS = 30_000
const recent = new Map<string, { tracked: TrackedReturn; at: number }>()

export function rememberLoadedReturn(tracked: TrackedReturn) {
  recent.set(tracked.rmaNumber, { tracked, at: Date.now() })
}

export function recentlyLoadedReturn(rmaNumber: string): TrackedReturn | undefined {
  const entry = recent.get(rmaNumber)
  return entry && Date.now() - entry.at < FRESH_FOR_MS ? entry.tracked : undefined
}
