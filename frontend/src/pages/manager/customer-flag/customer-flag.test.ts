import { expect, it } from 'vitest'
import { pendingCaption, plural } from './customer-flag'

it('summarises pending finance RMAs', () => {
  expect(pendingCaption([])).toBe('nothing waiting')
  expect(pendingCaption(['RMA-2026-00716'])).toBe('RMA-2026-00716')
  expect(pendingCaption(['RMA-1', 'RMA-2', 'RMA-3'])).toBe('RMA-1 +2 more')
})

it('pluralises', () => {
  expect(plural(1, 'return')).toBe('1 return')
  expect(plural(3, 'return')).toBe('3 returns')
})
