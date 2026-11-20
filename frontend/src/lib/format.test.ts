import { describe, expect, it } from 'vitest'
import { formatPaise, parseRupeesToPaise } from './format'

describe('parseRupeesToPaise', () => {
  it.each([
    ['3000', 300_000],
    ['₹3,000', 300_000],
    [' 3,000.5 ', 300_050],
    ['0.01', 1],
    ['-5', -500],
    ['', null],
    ['  ', null],
  ])('%j → %s', (input, expected) => {
    expect(parseRupeesToPaise(input)).toBe(expected)
  })

  it.each(['abc', '3.005', '3..0', '1e3'])('%j is invalid', (input) => {
    expect(parseRupeesToPaise(input)).toBeNaN()
  })

  it('round-trips with formatPaise', () => {
    expect(formatPaise(parseRupeesToPaise('₹949.50')!)).toBe('₹949.50')
  })
})
