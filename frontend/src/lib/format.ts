/** "₹1,899": whole amounts without decimals, others with two ("₹1,899.50"). */
export function formatMoney(amount: number, currency: string): string {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
    trailingZeroDisplay: "stripIfInteger",
  }).format(amount)
}

const DAY_MONTH_YEAR = { day: "numeric", month: "short", year: "numeric" } as const

// A plain ISO date ("2026-11-06") parses as UTC midnight; format it in UTC so
// the day never shifts. Date-times are shown in the customer's time zone.
const DATE_ONLY_FORMAT = new Intl.DateTimeFormat("en-GB", { ...DAY_MONTH_YEAR, timeZone: "UTC" })
const LOCAL_DATE_FORMAT = new Intl.DateTimeFormat("en-GB", DAY_MONTH_YEAR)
const LOCAL_DATE_TIME_FORMAT = new Intl.DateTimeFormat("en-GB", {
  ...DAY_MONTH_YEAR,
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
})

/** "6 Nov 2026", from an ISO date ("2026-11-06") or date-time. */
export function formatDate(iso: string): string {
  return (iso.includes("T") ? LOCAL_DATE_FORMAT : DATE_ONLY_FORMAT).format(new Date(iso))
}

/** "17 Nov 2026, 10:12", from an ISO date-time, in the customer's time zone. */
export function formatDateTime(iso: string): string {
  return LOCAL_DATE_TIME_FORMAT.format(new Date(iso))
}
