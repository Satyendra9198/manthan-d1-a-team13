import { describe, expect, it } from 'vitest'
import { buildDashboard } from '@/api/mocks/dashboard.mock'
import { generateDataset } from '@/api/mocks/seed/synthetic'
import { csvCell, formatDays, formatPercent, sortedCategories, toCsv } from './dashboard'

const now = new Date('2026-11-20T12:00:00Z')
const d = buildDashboard(generateDataset({ now }), 30, now)

describe('formatting', () => {
  it('formats rates and days, with — for no data', () => {
    expect(formatPercent(0.0841)).toBe('8.4%')
    expect(formatPercent(0.34, 0)).toBe('34%')
    expect(formatPercent(null)).toBe('—')
    expect(formatDays(4.21)).toBe('4.2 days')
    expect(formatDays(null)).toBe('—')
  })
})

it('sorts categories by rate with n/a last', () => {
  const s = sortedCategories(d)
  const rates = s.map((c) => c.rate)
  expect(rates.slice(-2)).toEqual([null, null])
  const known = rates.filter((r): r is number => r !== null)
  expect([...known].sort((a, b) => b - a)).toEqual(known)
})

describe('CSV export', () => {
  it('quotes commas/quotes and neutralises formulas', () => {
    expect(csvCell('plain')).toBe('plain')
    expect(csvCell('a, b')).toBe('"a, b"')
    expect(csvCell('say "hi"')).toBe('"say ""hi"""')
    expect(csvCell('=HYPERLINK("x")')).toBe(`"'=HYPERLINK(""x"")"`)
    expect(csvCell('@SUM(A1)')).toBe("'@SUM(A1)")
    expect(csvCell(-5)).toBe('-5') // numbers stay numbers
  })

  it('includes every section', () => {
    const csv = toCsv(d)
    expect(csv.split('\n')[0]).toBe('section,name,value,detail')
    for (const s of ['summary', 'return_rate_by_category', 'top_reasons', 'flagged_customers']) {
      expect(csv).toContain(`\n${s},`)
    }
    expect(csv).toContain('return_rate_by_category,Innerwear,n/a,')
  })
})
