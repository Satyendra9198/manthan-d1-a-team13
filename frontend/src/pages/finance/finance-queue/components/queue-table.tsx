import type { FinanceQueueItem } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { formatPaise } from '@/lib/format'
import { cn } from '@/lib/utils'
import { formatWaiting, queueFlags } from '../finance'

type Props = {
  items: FinanceQueueItem[]
  selectedId: string | null
  onReview: (id: string) => void
  panelId: string
}

const th = 'px-4 py-3 text-left text-[13px] font-normal text-muted-foreground'
const td = 'px-4 py-4 text-sm whitespace-nowrap'

export function QueueTable({ items, selectedId, onReview, panelId }: Props) {
  return (
    <div className="overflow-x-auto rounded-xl border bg-background">
      <table className="w-full min-w-[880px]">
        <caption className="sr-only">Refunds awaiting approval</caption>
        <thead className="border-b bg-muted/40">
          <tr>
            <th scope="col" className={th}>RMA</th>
            <th scope="col" className={th}>Customer</th>
            <th scope="col" className={th}>Item</th>
            <th scope="col" className={th}>Grade</th>
            <th scope="col" className={th}>Amount</th>
            <th scope="col" className={th}>Flags</th>
            <th scope="col" className={th}>Waiting</th>
            <th scope="col" className={th}><span className="sr-only">Action</span></th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => {
            const flags = queueFlags(item)
            const selected = item.returnId === selectedId
            return (
              <tr
                key={item.returnId}
                data-testid="queue-row"
                aria-selected={selected}
                className={cn('border-b last:border-b-0', selected && 'bg-muted/60')}
              >
                <td className={cn(td, 'font-medium')}>{item.rmaNumber}</td>
                <td className={td}>{item.customer.name}</td>
                <td className={td}>{item.item.name}</td>
                <td className={td}>{item.grade ?? '—'}</td>
                <td className={cn(td, 'font-medium')}>{formatPaise(item.refundAmountPaise)}</td>
                <td className={td}>
                  {flags.length ? (
                    <div className="flex gap-1.5">
                      {flags.map((f) => (
                        <Badge key={f.label} variant={f.variant} className="h-5 px-2.5 text-xs">
                          {f.label}
                        </Badge>
                      ))}
                    </div>
                  ) : (
                    <span className="text-muted-foreground" aria-label="No flags">—</span>
                  )}
                </td>
                <td className={td}>{formatWaiting(item.inspectedAt)}</td>
                <td className={cn(td, 'text-right')}>
                  <Button
                    variant="outline"
                    onClick={() => onReview(item.returnId)}
                    aria-expanded={selected}
                    aria-controls={selected ? panelId : undefined}
                    aria-label={`Review ${item.rmaNumber}`}
                  >
                    Review
                  </Button>
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
