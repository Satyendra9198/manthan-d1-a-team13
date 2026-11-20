import { Link } from 'react-router'
import type { RefundRecord } from '@/api/types'
import { Button } from '@/components/ui/button'
import { failureMessage, failureTitle } from '../ledger'

type Props = {
  refund: RefundRecord
  retrying: boolean
  disabled: boolean
  onRetry: () => void
}

export function FailedRefundBanner({ refund, retrying, disabled, onRetry }: Props) {
  return (
    <section
      aria-label={`Failed refund ${refund.rmaNumber}`}
      className="flex flex-col gap-4 rounded-xl bg-danger px-5 py-4 sm:flex-row sm:items-center sm:justify-between"
    >
      <div className="min-w-0">
        <h2 className="font-semibold text-danger-foreground">{failureTitle(refund)}</h2>
        <p className="mt-1 text-[13px] text-danger-foreground">{failureMessage(refund)}</p>
      </div>
      <div className="flex shrink-0 gap-3">
        <Button variant="outline" asChild>
          <Link to={`/audit?rma=${encodeURIComponent(refund.rmaNumber)}`}>View audit trail</Link>
        </Button>
        <Button onClick={onRetry} disabled={disabled}>
          {retrying ? 'Retrying…' : 'Retry with same key'}
        </Button>
      </div>
    </section>
  )
}
