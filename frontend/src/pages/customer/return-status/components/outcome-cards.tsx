import type { ReturnTracking } from '@/api/types'
import { Button } from '@/components/ui/button'
import { supportMailto } from '@/config/support'
import { formatPaise } from '@/lib/format'
import { refundExplanation, refundLine, rejectionText } from '../tracking'
import { Card } from './card'

export function RefundSummary({ tracking }: { tracking: ReturnTracking }) {
  if (!tracking.refund) return null
  const why = refundExplanation(tracking)
  const timing =
    tracking.refundMethod === 'store_credit'
      ? 'Store credit is available in your account right away.'
      : 'Refunds can take 1–2 business days to show in your account.'

  return (
    <>
      <div className="rounded-xl bg-success px-4 py-3.5">
        <p className="text-lg font-semibold text-success-foreground">
          {formatPaise(tracking.refund.amountPaise)} refunded
        </p>
        <p className="mt-1 text-xs">{refundLine(tracking)}</p>
      </div>
      <Card>
        {why && (
          <>
            <h2 className="text-sm font-medium">{why.title}</h2>
            <p className="mt-1.5 text-xs text-muted-foreground">{why.body}</p>
          </>
        )}
        <p className={why ? 'mt-2 text-xs text-muted-foreground' : 'text-xs text-muted-foreground'}>
          {timing} Reference: {tracking.refund.reference}.
        </p>
      </Card>
    </>
  )
}

/** Shown while requiresFinanceApproval holds the refund for review. Never mentions why. */
export function ReviewNotice({ tracking }: { tracking: ReturnTracking }) {
  const amount = tracking.inspection?.refundAmountPaise
  return (
    <div role="status" className="rounded-xl bg-warning px-4 py-3.5 text-warning-foreground">
      <p className="font-medium">Your refund is being reviewed</p>
      <p className="mt-1 text-xs text-foreground">
        We've inspected your return{amount ? ` and approved ${formatPaise(amount)}` : ''}. Our team
        checks some refunds before paying them. This usually takes up to 2 business days.
      </p>
    </div>
  )
}

export function RejectionNotice({ tracking }: { tracking: ReturnTracking }) {
  return (
    <>
      <div className="rounded-xl bg-danger px-4 py-3.5">
        <p className="font-medium text-danger-foreground">We couldn't accept this return</p>
        <p className="mt-1 text-xs">{rejectionText(tracking)}</p>
        <p className="mt-2 text-xs">No refund has been paid.</p>
      </div>
      <Card>
        <h2 className="text-sm font-medium">Think this is a mistake?</h2>
        <p className="mt-1.5 text-xs text-muted-foreground">
          Contact support within 7 days and quote your RMA number. We'll review the inspection
          photos with you.
        </p>
        <Button variant="outline" size="sm" className="mt-3 w-full" asChild>
          <a href={supportMailto(`Rejected return ${tracking.rmaNumber}`)}>Contact support</a>
        </Button>
      </Card>
    </>
  )
}
