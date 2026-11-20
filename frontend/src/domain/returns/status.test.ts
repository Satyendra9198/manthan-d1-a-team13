import { describe, expect, it } from 'vitest'
import {
  assertTransition,
  canTransition,
  RETURN_STATUSES,
  scanBlockReason,
  timelineFor,
  type ReturnStatus,
} from './status'

describe('return state machine', () => {
  it('allows the happy path in order', () => {
    const path: ReturnStatus[] = ['requested', 'approved', 'in_transit', 'received', 'inspected', 'refunded']
    for (let i = 0; i < path.length - 1; i++) expect(canTransition(path[i], path[i + 1])).toBe(true)
  })

  it('allows rejection only after request or inspection', () => {
    const canReject = RETURN_STATUSES.filter((s) => canTransition(s, 'rejected'))
    expect(canReject).toEqual(['requested', 'inspected'])
  })

  it('never skips a step or goes backwards', () => {
    expect(canTransition('requested', 'received')).toBe(false)
    expect(canTransition('received', 'refunded')).toBe(false)
    expect(canTransition('inspected', 'received')).toBe(false)
  })

  it('treats refunded and rejected as final', () => {
    for (const to of RETURN_STATUSES) {
      expect(canTransition('refunded', to)).toBe(false)
      expect(canTransition('rejected', to)).toBe(false)
    }
  })

  it('throws a readable error on an invalid transition', () => {
    expect(() => assertTransition('refunded', 'refunded')).toThrow("Can't move a return from Refunded to Refunded.")
  })

  it('only lets the warehouse scan approved, in-transit or received returns', () => {
    const scannable = RETURN_STATUSES.filter((s) => scanBlockReason(s) === null)
    expect(scannable).toEqual(['approved', 'in_transit', 'received'])
    expect(scanBlockReason('inspected')).toMatch(/already been inspected/)
  })

  it('builds the customer timeline', () => {
    expect(timelineFor('in_transit').at(-1)).toBe('refunded')
    expect(timelineFor('rejected').at(-1)).toBe('rejected')
  })
})
