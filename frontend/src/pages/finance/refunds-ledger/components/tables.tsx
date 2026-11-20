import type { ReactNode } from 'react'
import type { LedgerResponse, RefundRecord } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { formatDateTime, formatPaise, formatShortDateTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import { REFUND_STATUS_BADGE } from '../ledger'

const th = 'px-4 py-3 text-left text-[13px] font-normal text-muted-foreground whitespace-nowrap'
const td = 'px-4 py-3 text-sm whitespace-nowrap'

function TableCard({
  title,
  action,
  children,
}: {
  title: string
  action?: ReactNode
  children: ReactNode
}) {
  return (
    <section aria-label={title} className="overflow-hidden rounded-xl border bg-background">
      <div className="flex items-center justify-between px-4 py-3.5">
        <h2 className="text-[15px] font-medium">{title}</h2>
        {action}
      </div>
      <div className="overflow-x-auto">{children}</div>
    </section>
  )
}

export function RefundsTable({ refunds }: { refunds: RefundRecord[] }) {
  return (
    <TableCard title="Refunds">
      <table className="w-full min-w-[820px]">
        <thead className="border-y bg-muted/40">
          <tr>
            {['RMA', 'Amount', 'Method', 'Status', 'Attempts', 'Idempotency key', 'Updated'].map((h) => (
              <th key={h} scope="col" className={th}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {refunds.map((r) => {
            const badge = REFUND_STATUS_BADGE[r.status]
            return (
              <tr key={r.refundId} data-testid="refund-row" className="border-b last:border-b-0">
                <td className={cn(td, 'font-medium')}>{r.rmaNumber}</td>
                <td className={td}>{formatPaise(r.amountPaise)}</td>
                <td className={td}>{r.method}</td>
                <td className={td}>
                  <Badge variant={badge.variant} className="h-5 px-2.5 text-xs">
                    {badge.label}
                  </Badge>
                </td>
                <td className={td}>{r.attempts}</td>
                <td className={td}>{r.idempotencyKey}</td>
                <td className={td}>{formatDateTime(r.updatedAt)}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </TableCard>
  )
}

const ACCOUNT_TEXT = ''

export function LedgerTable({ ledger }: { ledger: LedgerResponse }) {
  return (
    <TableCard
      title="Ledger entries"
      action={
        <Badge variant={ledger.balanced ? 'success' : 'danger'} className="h-5 px-2.5 text-xs" data-testid="ledger-balance">
          {ledger.balanced ? 'Balanced' : 'Out of balance'}
        </Badge>
      }
    >
      <table className="w-full min-w-[820px]">
        <thead className="border-y bg-muted/40">
          <tr>
            {['Journal', 'RMA', 'Account', 'Debit', 'Credit', 'Type', 'Posted'].map((h) => (
              <th key={h} scope="col" className={th}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {ledger.entries.map((e) => (
            <tr key={`${e.journalId}-${e.account}`} data-testid="ledger-row" className="border-b">
              <td className={td}>{e.journalId}</td>
              <td className={td}>{e.rmaNumber}</td>
              <td className={cn(td, ACCOUNT_TEXT)}>{e.account}</td>
              <td className={td}>{e.debitPaise ? formatPaise(e.debitPaise) : ''}</td>
              <td className={td}>{e.creditPaise ? formatPaise(e.creditPaise) : ''}</td>
              <td className={cn(td, ACCOUNT_TEXT)}>{e.type}</td>
              <td className={td}>{formatShortDateTime(e.postedAt)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot className="bg-muted/40">
          <tr>
            <td colSpan={7} className="px-4 py-3 text-right text-sm font-semibold">
              <span data-testid="ledger-totals">
                Debits {formatPaise(ledger.totals.debitPaise)}
                <span className="ml-5">Credits {formatPaise(ledger.totals.creditPaise)}</span>
              </span>
            </td>
          </tr>
        </tfoot>
      </table>
    </TableCard>
  )
}
