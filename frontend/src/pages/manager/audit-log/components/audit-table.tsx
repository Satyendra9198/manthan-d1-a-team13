import type { AuditLogEntry } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { formatDateTime } from '@/lib/format'
import { cn } from '@/lib/utils'
import { actionBadge, actorLabel, changeLabel } from '../audit'

const th = 'px-4 py-3 text-left text-[13px] font-normal text-muted-foreground whitespace-nowrap'
const td = 'px-4 py-3.5 text-sm'

export function AuditTable({ entries }: { entries: AuditLogEntry[] }) {
  return (
    <div className="overflow-x-auto rounded-xl border bg-background">
      <table className="w-full min-w-[980px]">
        <caption className="sr-only">Audit log entries, newest first</caption>
        <thead className="border-b bg-muted/40">
          <tr>
            {['Time', 'Action', 'Change', 'Actor', 'Details', 'Request'].map((h) => (
              <th key={h} scope="col" className={th}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {entries.map((e) => (
            <tr key={e.id} data-testid="audit-row" className="border-b last:border-b-0">
              <td className={cn(td, 'whitespace-nowrap')}>{formatDateTime(e.at)}</td>
              <td className={cn(td, 'whitespace-nowrap')}>
                <Badge variant={actionBadge(e)} className="h-5 px-2.5 text-xs">
                  {e.action}
                </Badge>
              </td>
              <td className={cn(td, 'whitespace-nowrap')}>
                {changeLabel(e)}
                <span className="block text-xs text-muted-foreground">{e.rmaNumber ?? e.customerName}</span>
              </td>
              <td className={cn(td, 'whitespace-nowrap')}>{actorLabel(e)}</td>
              <td className={cn(td, 'max-w-[360px]')}>{e.details}</td>
              <td className={cn(td, 'whitespace-nowrap text-muted-foreground')}>{e.requestId}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
