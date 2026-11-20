import { useState } from 'react'
import type { DashboardResponse } from '@/api/types'
import { cn } from '@/lib/utils'
import { formatPercent } from '../dashboard'
import { Panel, TableView } from './panel'

type Category = DashboardResponse['byCategory'][number]

const PLOT_HEIGHT = 132

/** Single-series bar chart: one hue (--chart-1), direct labels, hover/focus tooltip, table view. */
export function CategoryChart({ categories }: { categories: Category[] }) {
  const [active, setActive] = useState<string | null>(null)
  const max = Math.max(...categories.map((c) => c.rate ?? 0), 0.0001)
  const notReturnable = categories.filter((c) => c.rate === null).map((c) => c.label.toLowerCase())

  return (
    <Panel title="Return rate by category">
      <ul className="flex items-end gap-2" aria-label="Return rate by category">
        {categories.map((c, i) => {
          const label = c.rate === null ? 'n/a' : formatPercent(c.rate)
          const detail =
            c.rate === null ? 'Not returnable' : `${c.returns} of ${c.orders.toLocaleString('en-IN')} orders`
          const height = c.rate === null ? 0 : Math.max(2, (c.rate / max) * PLOT_HEIGHT)
          return (
            <li
              key={c.category}
              tabIndex={0}
              aria-label={`${c.label}: ${c.rate === null ? 'not returnable' : `${label} return rate, ${detail}`}`}
              data-testid="category-bar"
              onPointerEnter={() => setActive(c.category)}
              onPointerLeave={() => setActive(null)}
              onFocus={() => setActive(c.category)}
              onBlur={() => setActive(null)}
              className="group relative flex min-w-0 flex-1 flex-col items-center rounded-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              {active === c.category && (
                <div
                  role="tooltip"
                  className={cn(
                    'pointer-events-none absolute top-8 z-10 rounded-md border bg-popover px-2.5 py-1.5 text-xs whitespace-nowrap shadow-md',
                    // keep it inside the card: open rightwards on the left half, leftwards on the right half
                    i < categories.length / 2 ? 'left-0' : 'right-0',
                  )}
                >
                  <span className="block font-semibold">{label}</span>
                  <span className="text-muted-foreground">
                    {c.label} · {detail}
                  </span>
                </div>
              )}
              <div className="flex w-full flex-col items-center justify-end" style={{ height: PLOT_HEIGHT + 20 }}>
                <span
                  aria-hidden
                  className={cn('mb-1 text-xs', c.rate === null ? 'text-muted-foreground' : 'font-semibold')}
                >
                  {label}
                </span>
                {c.rate === null ? (
                  <span aria-hidden className="h-px w-full bg-border" />
                ) : (
                  <span
                    aria-hidden
                    className="w-full rounded-t-[4px] bg-chart-1 transition-opacity group-hover:opacity-85"
                    style={{ height }}
                  />
                )}
              </div>
              <span aria-hidden className="mt-2 w-full truncate text-center text-xs text-muted-foreground">
                {c.label}
              </span>
            </li>
          )
        })}
      </ul>
      {notReturnable.length > 0 && (
        <p className="mt-4 text-xs text-muted-foreground">
          {notReturnable.map((n, i) => (i === 0 ? n[0].toUpperCase() + n.slice(1) : n)).join(' and ')}{' '}
          {notReturnable.length === 1 ? 'is' : 'are'} not returnable.
        </p>
      )}
      <TableView
        caption="Return rate by category"
        head={['Category', 'Return rate', 'Returns', 'Orders']}
        rows={categories.map((c) => [
          c.label,
          c.rate === null ? 'n/a' : formatPercent(c.rate),
          String(c.returns),
          c.orders.toLocaleString('en-IN'),
        ])}
      />
    </Panel>
  )
}
