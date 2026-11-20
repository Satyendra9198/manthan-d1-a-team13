import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { FailedRefundBanner } from './components/failed-refund-banner'
import { LedgerTable, RefundsTable } from './components/tables'
import { failedCountLabel } from './ledger'
import { useRefundsLedger } from './use-refunds-ledger'

/** Finance: every refund attempt and every ledger posting. */
export function RefundsLedgerPage() {
  const r = useRefundsLedger()

  return (
    <div className="space-y-5">
      <PageHeader
        title="Refunds and ledger"
        description="Every gateway attempt and every ledger posting. Retries always reuse the same key."
        actions={r.failed.length > 0 && <Badge variant="danger">{failedCountLabel(r.failed.length)}</Badge>}
      />

      {r.result && (
        <p
          role="status"
          className={cn(
            'rounded-md px-4 py-3 text-sm',
            r.result.kind === 'success' ? 'bg-success text-success-foreground' : 'bg-danger text-danger-foreground',
          )}
        >
          {r.result.message}
        </p>
      )}

      {r.data.status === 'loading' && <p className="text-sm text-muted-foreground">Loading refunds…</p>}

      {r.data.status === 'error' && (
        <div role="alert" className="flex items-center justify-between gap-4 rounded-xl border bg-background p-4">
          <p className="text-sm text-destructive">{r.data.message}</p>
          <Button variant="outline" size="sm" onClick={() => void r.reload()}>
            Try again
          </Button>
        </div>
      )}

      {r.data.status === 'loaded' && (
        <>
          {r.failed.map((f) => (
            <FailedRefundBanner
              key={f.refundId}
              refund={f}
              retrying={r.retrying === f.refundId}
              disabled={r.retrying !== null}
              onRetry={() => void r.retry(f)}
            />
          ))}
          <RefundsTable refunds={r.refunds} />
          <LedgerTable ledger={r.data.ledger} />
          <p className="text-[13px] text-muted-foreground">
            Ledger rows are insert-only and posted only after the gateway confirms success. Failed and pending
            refunds have no ledger rows.
          </p>
        </>
      )}
    </div>
  )
}
