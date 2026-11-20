import { describe, expect, it } from 'vitest'
import type { RefundRecord } from '@/api/types'
import { failedCountLabel, failureMessage, failureTitle } from './ledger'

const refund: RefundRecord = {
  refundId: 'rfd_1',
  returnId: 'ret_00698',
  rmaNumber: 'RMA-2026-00698',
  itemName: 'Smartwatch S3',
  amountPaise: 1_849_900,
  method: 'Card',
  status: 'failed',
  attempts: 4,
  idempotencyKey: 'rr-refund-88190',
  reference: null,
  lastError: 'Gateway timed out',
  createdAt: '',
  updatedAt: '',
}

describe('failed refund copy', () => {
  it('matches the design', () => {
    expect(failureTitle(refund)).toBe('Refund failed · RMA-2026-00698 · Smartwatch S3 · ₹18,499')
    expect(failureMessage(refund)).toBe(
      'Gateway timed out on all 4 attempts (1 + 3 automatic retries). Key rr-refund-88190 has not been charged; retrying with it is safe.',
    )
  })

  it('pluralises', () => {
    expect(failedCountLabel(1)).toBe('1 failed refund')
    expect(failedCountLabel(2)).toBe('2 failed refunds')
    expect(failureMessage({ ...refund, attempts: 1 })).toMatch(/on the only attempt/)
  })
})
