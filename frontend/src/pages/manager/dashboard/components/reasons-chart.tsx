import type { DashboardResponse } from '@/api/types'
import { formatPercent } from '../dashboard'
import { Panel, TableView } from './panel'

/** Horizontal bars; every value is labelled, so no tooltip is needed. */
export function ReasonsChart({ reasons }: { reasons: DashboardResponse['topReasons'] }) {
  const max = Math.max(...reasons.map((r) => r.share), 0.0001)
  return (
    <Panel title="Top return reasons">
      {reasons.length === 0 ? (
        <p className="text-sm text-muted-foreground">No returns in this period.</p>
      ) : (
        <ul className="space-y-3.5">
          {reasons.map((r) => (
            <li key={r.reason} data-testid="reason-row">
              <div className="flex items-center justify-between text-sm">
                <span>{r.label}</span>
                <span className="font-semibold">{formatPercent(r.share, 0)}</span>
              </div>
              <div aria-hidden className="mt-1.5 h-2 rounded-full bg-muted">
                <div className="h-full rounded-full bg-chart-1" style={{ width: `${(r.share / max) * 100}%` }} />
              </div>
            </li>
          ))}
        </ul>
      )}
      <TableView
        caption="Top return reasons"
        head={['Reason', 'Share', 'Returns']}
        rows={reasons.map((r) => [r.label, formatPercent(r.share, 0), String(r.returns)])}
      />
    </Panel>
  )
}
