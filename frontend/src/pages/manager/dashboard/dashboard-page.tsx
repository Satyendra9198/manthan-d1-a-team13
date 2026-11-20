import type { DashboardPeriodDays } from '@/api/types'
import { Button } from '@/components/ui/button'
import { downloadCsv } from '@/lib/csv'
import { cn } from '@/lib/utils'
import { CategoryChart } from './components/category-chart'
import { FlaggedCustomers } from './components/flagged-customers'
import { StatTile } from './components/panel'
import { ReasonsChart } from './components/reasons-chart'
import { formatDays, formatPercent, sortedCategories, toCsv } from './dashboard'
import { useDashboard } from './use-dashboard'

const PERIODS: DashboardPeriodDays[] = [7, 14, 30]

/** Operations manager: returns overview. */
export function DashboardPage() {
  const { days, setDays, data, error, refreshing } = useDashboard()

  function exportCsv() {
    if (data) downloadCsv(`returns-overview-${data.periodDays}d.csv`, toCsv(data))
  }

  return (
    <div className="space-y-5">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-[26px] leading-tight font-medium tracking-tight">Returns overview</h1>
          <p className="mt-1 text-[15px] text-muted-foreground">
            Last {days} days · sample data from the seed script
          </p>
        </div>
        <div className="flex gap-3">
          <label className="sr-only" htmlFor="period">
            Period
          </label>
          <select
            id="period"
            value={days}
            onChange={(e) => setDays(Number(e.target.value) as DashboardPeriodDays)}
            className="h-10 cursor-pointer rounded-[6px] border bg-background px-3 text-[15px] outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
          >
            {PERIODS.map((p) => (
              <option key={p} value={p}>
                Last {p} days
              </option>
            ))}
          </select>
          <Button variant="outline" onClick={exportCsv} disabled={!data}>
            Export CSV
          </Button>
        </div>
      </header>

      {error && (
        <p role="alert" className="rounded-md bg-danger px-4 py-3 text-sm text-danger-foreground">
          {error}
        </p>
      )}
      {!data && !error && <p className="text-sm text-muted-foreground">Loading dashboard…</p>}

      {data && (
        <div aria-busy={refreshing} className={cn('space-y-5 transition-opacity', refreshing && 'opacity-60')}>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatTile
              label="Return rate"
              value={formatPercent(data.returnRate)}
              caption={`of ${data.orders.toLocaleString('en-IN')} orders`}
            />
            <StatTile label="Avg cycle time" value={formatDays(data.avgCycleDays)} caption="request → refund or reject" />
            <StatTile
              label="Refund success rate"
              value={formatPercent(data.refundSuccessRate)}
              caption={`mock gateway, ${data.periodDays} days`}
            />
            <StatTile
              label="Flagged customers"
              value={String(data.flaggedCustomers.length)}
              caption="> 3 returns in 30 days"
            />
          </div>

          <div className="grid items-start gap-4 lg:grid-cols-2">
            <CategoryChart categories={sortedCategories(data)} />
            <ReasonsChart reasons={data.topReasons} />
          </div>

          <FlaggedCustomers customers={data.flaggedCustomers} />
        </div>
      )}
    </div>
  )
}
