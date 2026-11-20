import { describe, expect, it } from 'vitest'
import { countRecentReturns, flagRaisedAt, isFlagActive, isFlaggedForReview } from './flagging'

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


describe('flag lifecycle', () => {
  const now = new Date('2026-11-20T12:00:00Z')
  const ago = (d: number) => new Date(now.getTime() - d * 86_400_000).toISOString()
  const four = [ago(20), ago(12), ago(5), ago(1)]

  it('raised by the return that takes the count over 3', () => {
    expect(flagRaisedAt(four, now)).toBe(ago(1))
    expect(flagRaisedAt(four.slice(0, 3), now)).toBeNull()
    expect(flagRaisedAt([ago(40), ...four], now)).toBe(ago(1)) // old returns don't count
  })

  it('cleared flags stay cleared until a new return arrives', () => {
    expect(isFlagActive(four, now)).toBe(true)
    expect(isFlagActive(four, now, ago(0.5))).toBe(false)
    expect(isFlagActive([...four, ago(0.1)], now, ago(0.5))).toBe(true) // new return after clearing
  })

  it('a new return that does not take the count over 3 does not raise it', () => {
    expect(isFlagActive([ago(25), ago(0.1)], now, ago(0.5))).toBe(false)
  })
})
