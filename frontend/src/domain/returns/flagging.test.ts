import { describe, expect, it } from 'vitest'
import { countRecentReturns, isFlaggedForReview } from './flagging'

const today = '2026-03-31'

describe('isFlaggedForReview (more than 3 returns in 30 days)', () => {
  it('3 returns is not flagged, 4 is', () => {
    expect(isFlaggedForReview(['2026-03-01', '2026-03-10', '2026-03-20'], today)).toBe(false)
    expect(isFlaggedForReview(['2026-03-02', '2026-03-10', '2026-03-20', '2026-03-31'], today)).toBe(true)
  })

  it('counts days 0–29 ago, not day 30', () => {
    expect(countRecentReturns(['2026-03-02'], today)).toBe(1) // 29 days ago
    expect(countRecentReturns(['2026-03-01'], today)).toBe(0) // 30 days ago
  })

  it('ignores future-dated entries', () => {
    expect(countRecentReturns(['2026-04-01'], today)).toBe(0)
  })
})
