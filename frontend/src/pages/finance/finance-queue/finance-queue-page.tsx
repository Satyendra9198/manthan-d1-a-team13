import { useId } from 'react'
import { PageHeader } from '@/components/layout/page-header'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { formatPaise } from '@/lib/format'
import { FINANCE_APPROVAL_ABOVE_PAISE } from '@/domain/returns'
import { cn } from '@/lib/utils'
import { QueueTable } from './components/queue-table'
import { ReviewPanel } from './components/review-panel'
import { queueSummary } from './finance'
import { useFinanceQueue } from './use-finance-queue'

/** Finance reviewer: approve or decline refunds held by requiresFinanceApproval. */
export function FinanceQueuePage() {
  const q = useFinanceQueue()
  const panelId = useId()
  const limit = formatPaise(FINANCE_APPROVAL_ABOVE_PAISE)

  return (
    <div className="space-y-6">
      <PageHeader
        title="Refunds awaiting approval"
        description={`Every refund above ${limit}, and every refund for a flagged customer, needs a finance decision before it is paid.`}
        actions={
          q.queue.status === 'loaded' &&
          q.items.length > 0 && <Badge variant="warning">{queueSummary(q.items)}</Badge>
        }
      />

      {q.result && (
        <p
          role="status"
          className={cn(
            'rounded-md px-4 py-3 text-sm',
            q.result.kind === 'success' ? 'bg-success text-success-foreground' : 'bg-danger text-danger-foreground',
          )}
        >
          {q.result.message}
        </p>
      )}

      {q.queue.status === 'loading' && <p className="text-sm text-muted-foreground">Loading queue…</p>}

      {q.queue.status === 'error' && (
        <div role="alert" className="flex items-center justify-between gap-4 rounded-xl border bg-background p-4">
          <p className="text-sm text-destructive">{q.queue.message}</p>
          <Button variant="outline" size="sm" onClick={() => void q.reload()}>
            Try again
          </Button>
        </div>
      )}

      {q.queue.status === 'loaded' &&
        (q.items.length === 0 ? (
          <div className="rounded-xl border border-dashed bg-background p-10 text-center text-sm text-muted-foreground">
            Nothing waiting for approval. Refunds above {limit} and refunds for flagged customers appear here
            after inspection.
          </div>
        ) : (
          <QueueTable
            items={q.items}
            selectedId={q.selected?.returnId ?? null}
            onReview={(id) => q.select(q.selected?.returnId === id ? null : id)}
            panelId={panelId}
          />
        ))}

      {q.selected && (
        <ReviewPanel
          key={q.selected.returnId}
          id={panelId}
          item={q.selected}
          busy={q.busy}
          error={q.panelError}
          onApprove={() => void q.approve(q.selected!)}
          onDecline={(note) => void q.decline(q.selected!, note)}
          onClose={() => q.select(null)}
        />
      )}
    </div>
  )
}
