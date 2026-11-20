import { describe, expect, it } from 'vitest'
import { checkEligibility } from './eligibility'

const delivered = '2026-01-01'

describe('checkEligibility', () => {
  describe('standard 30-day window', () => {
    it.each([
      ['same day (day 0)', '2026-01-01', true],
      ['day 29', '2026-01-30', true],
      ['day 30 (last day)', '2026-01-31', true],
      ['day 31', '2026-02-01', false],
    ])('%s → eligible=%s', (_, today, eligible) => {
      expect(checkEligibility({ category: 'apparel', deliveredOn: delivered }, today).eligible).toBe(eligible)
    })

    it('applies to footwear and home too', () => {
      for (const category of ['footwear', 'home'] as const) {
        expect(checkEligibility({ category, deliveredOn: delivered }, '2026-01-31').eligible).toBe(true)
        expect(checkEligibility({ category, deliveredOn: delivered }, '2026-02-01').eligible).toBe(false)
      }
    })

    it('explains how long is left', () => {
      expect(checkEligibility({ category: 'apparel', deliveredOn: delivered }, '2026-01-29').message).toBe(
        'Returnable until 31 Jan 2026 (2 days left).',
      )
      expect(checkEligibility({ category: 'apparel', deliveredOn: delivered }, '2026-01-30').message).toMatch(
        /\(1 day left\)/,
      )
      expect(checkEligibility({ category: 'apparel', deliveredOn: delivered }, '2026-01-31').message).toBe(
        'Last day to return this item is today.',
      )
    })

    it('explains an expired window', () => {
      const r = checkEligibility({ category: 'apparel', deliveredOn: delivered }, '2026-02-01')
      expect(r).toMatchObject({
        eligible: false,
        code: 'WINDOW_EXPIRED',
        message: 'The 30-day return window for apparel ended on 31 Jan 2026.',
      })
    })
  })

  describe('electronics 10-day window', () => {
    it.each([
      ['day 10', '2026-01-11', true],
      ['day 11', '2026-01-12', false],
    ])('%s → eligible=%s', (_, today, eligible) => {
      expect(checkEligibility({ category: 'electronics', deliveredOn: delivered }, today).eligible).toBe(eligible)
    })
  })

  it.each(['innerwear', 'perishables'] as const)('%s is never returnable, even on day 0', (category) => {
    const r = checkEligibility({ category, deliveredOn: delivered }, delivered)
    expect(r).toMatchObject({ eligible: false, code: 'NOT_RETURNABLE' })
    expect(r.message).toMatch(/can't be returned/)
  })

  it('rejects items not yet delivered', () => {
    expect(checkEligibility({ category: 'apparel', deliveredOn: null }, '2026-01-05')).toMatchObject({
      eligible: false,
      code: 'NOT_DELIVERED',
    })
    expect(checkEligibility({ category: 'apparel', deliveredOn: '2026-01-10' }, '2026-01-05')).toMatchObject({
      code: 'NOT_DELIVERED',
    })
  })

  it('handles month and leap-year boundaries', () => {
    // 2028 is a leap year: 30 Jan + 30 days = 29 Feb
    expect(checkEligibility({ category: 'apparel', deliveredOn: '2028-01-30' }, '2028-02-29').eligible).toBe(true)
    expect(checkEligibility({ category: 'apparel', deliveredOn: '2028-01-30' }, '2028-03-01').eligible).toBe(false)
  })

  it('evaluates each item of a mixed basket on its own', () => {
    const today = '2026-01-15' // day 14
    const basket = [
      { category: 'apparel', deliveredOn: delivered },
      { category: 'electronics', deliveredOn: delivered },
      { category: 'innerwear', deliveredOn: delivered },
    ] as const
    expect(basket.map((i) => checkEligibility(i, today).eligible)).toEqual([true, false, false])
  })
})
