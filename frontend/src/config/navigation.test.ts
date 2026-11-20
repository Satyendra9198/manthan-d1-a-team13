import { describe, expect, it } from 'vitest'
import { canOpen, homePathFor, navItemsFor } from './navigation'

describe('role navigation', () => {
  it('each role sees only its screens', () => {
    expect(navItemsFor('WAREHOUSE_AGENT').map((i) => i.path)).toEqual(['/scan'])
    expect(navItemsFor('FINANCE_REVIEWER').map((i) => i.path)).toEqual(['/finance', '/refunds'])
    expect(navItemsFor('OPERATIONS_MANAGER').map((i) => i.path)).toEqual(['/dashboard', '/audit'])
  })

  it('each role lands on its first screen', () => {
    expect(homePathFor('WAREHOUSE_AGENT')).toBe('/scan')
    expect(homePathFor('FINANCE_REVIEWER')).toBe('/finance')
    expect(homePathFor('OPERATIONS_MANAGER')).toBe('/dashboard')
  })

  it('a role may not open another role screen', () => {
    expect(canOpen('FINANCE_REVIEWER', '/refunds')).toBe(true)
    expect(canOpen('FINANCE_REVIEWER', '/scan')).toBe(false)
  })
})
