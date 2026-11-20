import { Link } from 'react-router'
import type { DashboardResponse } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { formatDate, formatPaise } from '@/lib/format'

const th = 'px-5 py-3 text-left text-[13px] font-normal text-muted-foreground whitespace-nowrap'
const td = 'px-5 py-3.5 text-sm whitespace-nowrap'

export function FlaggedCustomers({ customers }: { customers: DashboardResponse['flaggedCustomers'] }) {
  return (
    <section aria-label="Flagged customers" className="overflow-hidden rounded-xl border bg-background">
      <div className="flex items-center justify-between gap-3 px-5 py-4">
        <h2 className="text-[15px] font-medium">Flagged customers</h2>
        <span className="text-xs text-muted-foreground">Flag = more than 3 returns in 30 days</span>
      </div>
      {customers.length === 0 ? (
        <p className="border-t px-5 py-6 text-sm text-muted-foreground">No flagged customers right now.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px]">
            <thead className="border-y bg-muted/40">
              <tr>
                <th scope="col" className={th}>Customer</th>
                <th scope="col" className={th}>Returns in 30 days</th>
                <th scope="col" className={th}>Refunded value</th>
                <th scope="col" className={th}>Last return</th>
                <th scope="col" className={th}><span className="sr-only">History</span></th>
              </tr>
            </thead>
            <tbody>
              {customers.map((c) => (
                <tr key={c.customerId} data-testid="flagged-row" className="border-b last:border-b-0">
                  <td className={td}>{c.name}</td>
                  <td className={td}>
                    <Badge variant="warning" className="h-5 px-2.5 text-xs">
                      {c.returns30d} returns
                    </Badge>
                  </td>
                  <td className={td}>{formatPaise(c.refundedPaise)}</td>
                  <td className={td}>{formatDate(c.lastReturnAt)}</td>
                  <td className={td}>
                    <Link
                      to={`/dashboard/customers/${encodeURIComponent(c.customerId)}`}
                      className="font-medium hover:underline"
                      aria-label={`View history for ${c.name}`}
                    >
                      View history
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
