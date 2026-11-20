import { PageHeader } from '@/components/layout/page-header'
import { Button } from '@/components/ui/button'
import { downloadCsv } from '@/lib/csv'
import { cn } from '@/lib/utils'
import { auditCsv } from './audit'
import { AuditFilters } from './components/audit-filters'
import { AuditTable } from './components/audit-table'
import { useAuditLog } from './use-audit-log'

/** Operations manager: read-only audit trail of every state change. */
export function AuditLogPage() {
  const a = useAuditLog()
  const customerName = a.entries?.find((e) => e.customerId === a.filters.customer)?.customerName ?? null

  return (
    <div className="space-y-5">
      <PageHeader
        title="Audit log"
        description="Every state change, written in the same transaction. Read-only."
        actions={
          <Button
            variant="outline"
            disabled={!a.entries?.length}
            onClick={() => a.entries && downloadCsv(`audit-log-${new Date().toISOString().slice(0, 10)}.csv`, auditCsv(a.entries))}
          >
            Export CSV
          </Button>
        }
      />

      <AuditFilters filters={a.filters} customerName={customerName} onChange={a.setFilter} />

      {a.error && (
        <p role="alert" className="rounded-md bg-danger px-4 py-3 text-sm text-danger-foreground">
          {a.error}
        </p>
      )}
      {!a.entries && !a.error && <p className="text-sm text-muted-foreground">Loading audit log…</p>}

      {a.entries && (
        <div aria-busy={a.refreshing} className={cn('transition-opacity', a.refreshing && 'opacity-60')}>
          {a.entries.length === 0 ? (
            <div className="rounded-xl border border-dashed bg-background p-10 text-center text-sm text-muted-foreground">
              No audit entries match these filters.
            </div>
          ) : (
            <AuditTable entries={a.entries} />
          )}
          <p className="mt-4 text-[13px] text-muted-foreground" data-testid="audit-count">
            {a.entries.length} {a.entries.length === 1 ? 'row' : 'rows'}. Rows with the same request ID were written by
            one API call. Rows cannot be edited or deleted.
          </p>
        </div>
      )}
    </div>
  )
}
