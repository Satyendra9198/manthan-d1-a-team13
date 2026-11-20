import type { CustomerProfile } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { formatPaise } from '@/lib/format'
import { cn } from '@/lib/utils'
import { RETURN_STATUS_BADGE } from '../customer-flag'

const th = 'px-4 py-3 text-left text-[13px] font-normal text-muted-foreground whitespace-nowrap'
const td = 'px-4 py-3 text-sm whitespace-nowrap'

export function ReturnsTable({ returns }: { returns: CustomerProfile['returns'] }) {
  return (
    <section aria-label="Returns in the last 30 days" className="overflow-hidden rounded-xl border bg-background">
      <h2 className="px-4 py-3.5 text-[15px] font-medium">Returns in the last 30 days</h2>
      {returns.length === 0 ? (
        <p className="border-t px-4 py-6 text-sm text-muted-foreground">No returns in the last 30 days.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px]">
            <thead className="border-y bg-muted/40">
              <tr>
                {['RMA', 'Item', 'Reason', 'Grade', 'Status', 'Refund'].map((h) => (
                  <th key={h} scope="col" className={th}>
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {returns.map((r) => {
                const badge = RETURN_STATUS_BADGE[r.status]
                return (
                  <tr key={r.rmaNumber} data-testid="customer-return" className="border-b last:border-b-0">
                    <td className={cn(td, 'font-medium')}>{r.rmaNumber}</td>
                    <td className={td}>{r.itemName}</td>
                    <td className={td}>{r.reason}</td>
                    <td className={td}>{r.grade ?? '—'}</td>
                    <td className={td}>
                      <Badge variant={badge.variant} className="h-5 px-2.5 text-xs">
                        {badge.label}
                      </Badge>
                    </td>
                    <td className={td}>{r.status === 'rejected' ? '—' : formatPaise(r.refundPaise)}</td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
