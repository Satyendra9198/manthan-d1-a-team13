import { describe, expect, it } from 'vitest'
import {
  FINANCE_APPROVAL_ABOVE_PAISE,
  maxPartialRefund,
  needsFinanceApproval,
  refundForGrade,
  requiresFinanceApproval,
  validatePartialRefund,
} from './refund'

describe('refundForGrade', () => {
  it('pays 100% for A and B, 50% for C', () => {
    expect(refundForGrade(189_900, 'A')).toBe(189_900)
    expect(refundForGrade(189_900, 'B')).toBe(189_900)
    expect(refundForGrade(189_900, 'C')).toBe(94_950)
  })

  it('rounds half-paise down so we never overpay', () => {
    expect(refundForGrade(189_999, 'C')).toBe(94_999)
  })
})

describe('needsFinanceApproval (above ₹10,000)', () => {
  it.each([
    [999_999, false],
    [1_000_000, false], // exactly ₹10,000 → no approval needed
    [1_000_001, true],
  ])('%i paise → %s', (amount, expected) => {
    expect(needsFinanceApproval(amount)).toBe(expected)
  })

  it('uses the refund after grading, not the item price', () => {
    // ₹15,000 item, grade C → ₹7,500 refund → no approval
    expect(needsFinanceApproval(refundForGrade(1_500_000, 'C'))).toBe(false)
    expect(needsFinanceApproval(refundForGrade(1_500_000, 'A'))).toBe(true)
    expect(FINANCE_APPROVAL_ABOVE_PAISE).toBe(1_000_000)
  })
})


describe('requiresFinanceApproval (above ₹10,000 OR flagged customer)', () => {
  it.each([
    [94_950, false, false],
    [1_000_000, false, false],
    [1_000_001, false, true],
    [94_950, true, true], // flagged customer, small refund
    [1_000_001, true, true],
  ])('%i paise, flagged=%s → %s', (amount, flagged, expected) => {
    expect(requiresFinanceApproval(amount, flagged)).toBe(expected)
  })
})

describe('partial refunds', () => {
  it('caps at the grade refund', () => {
    expect(maxPartialRefund(429_900, 'B')).toBe(429_900)
    expect(maxPartialRefund(429_900, 'C')).toBe(214_950)
  })

  it.each([
    [null, 'REQUIRED'],
    [0, 'NOT_POSITIVE'],
    [-100, 'NOT_POSITIVE'],
    [1, null],
    [429_900, null], // exactly the max
    [429_901, 'ABOVE_MAX'],
  ])('%s paise → %s', (amount, expected) => {
    expect(validatePartialRefund(amount, 429_900)).toBe(expected)
  })
})
