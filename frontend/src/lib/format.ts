const inr = (fractionDigits: number) =>
  new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  })

const whole = inr(0)
const exact = inr(2)

/** 189900 → "₹1,899", 94950 → "₹949.50". */
export function formatPaise(paise: number): string {
  return paise % 100 === 0 ? whole.format(paise / 100) : exact.format(paise / 100)
}

/**
 * Parse what an agent types into a rupee field → paise.
 * "3000", "₹3,000", "3,000.5" → 300000 / 300000 / 300050. Empty → null. Invalid or >2 decimals → NaN.
 */
export function parseRupeesToPaise(input: string): number | null {
  const cleaned = input.replace(/[₹,\s]/g, '')
  if (cleaned === '') return null
  if (!/^-?\d+(\.\d{1,2})?$/.test(cleaned)) return Number.NaN
  const [whole, frac = ''] = cleaned.replace('-', '').split('.')
  const paise = Number(whole) * 100 + Number(frac.padEnd(2, '0'))
  return cleaned.startsWith('-') ? -paise : paise
}

const IST = 'Asia/Kolkata'
const dateTime = new Intl.DateTimeFormat('en-IN', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
  timeZone: IST,
})
const dateOnly = new Intl.DateTimeFormat('en-IN', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
  timeZone: IST,
})

/** ISO → "17 Nov 2026, 10:12" (IST) */
export const formatDateTime = (iso: string) => dateTime.format(new Date(iso))
/** ISO → "20 Nov 2026" (IST) */
export const formatDate = (iso: string) => dateOnly.format(new Date(iso))

const shortDateTime = new Intl.DateTimeFormat('en-IN', {
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
  timeZone: IST,
})

/** ISO → "20 Nov, 15:32" (IST) */
export const formatShortDateTime = (iso: string) => shortDateTime.format(new Date(iso))
