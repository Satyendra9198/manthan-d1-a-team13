import { Fragment, useId, useState, type ReactNode } from "react"
import { LoaderCircle } from "lucide-react"
import type { Order, OrderItem } from "@/api/orders"
import {
  EMPTY_ITEM_DETAILS,
  REFUND_METHODS,
  reasonLabel,
  submitReturn,
  type ItemReturnDetails,
  type RefundMethod,
  type ReturnReasonCode,
  type TrackedReturn,
} from "@/api/returns"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Checkbox } from "@/components/ui/checkbox"
import { Label } from "@/components/ui/label"
import { formatPaise } from "@/lib/format"
import { WizardFooter } from "./wizard-footer"

type ReviewStepProps = {
  order: Order
  /** The items being returned, in order. */
  items: OrderItem[]
  details: Record<string, ItemReturnDetails>
  refundMethod: RefundMethod
  /** Estimated refund for all items. */
  refund: number
  /** Go back to step 3 at this item. */
  onEditItem: (index: number) => void
  onBack: () => void
  onSubmitted: (result: TrackedReturn) => void
}

/** Wizard step 5: check every item, confirm, and submit the return. */
export function ReviewStep({
  order,
  items,
  details,
  refundMethod,
  refund,
  onEditItem,
  onBack,
  onSubmitted,
}: ReviewStepProps) {
  const confirmId = useId()
  const [confirmed, setConfirmed] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [failed, setFailed] = useState(false)

  const refundMethodText =
    refundMethod === "original_payment"
      ? `${REFUND_METHODS.original_payment} (${order.payment.method})`
      : REFUND_METHODS.store_credit

  async function handleSubmit() {
    if (!confirmed || submitting) return
    setSubmitting(true)
    setFailed(false)

    let result: TrackedReturn
    try {
      result = await submitReturn({
        orderNumber: order.orderNumber,
        email: order.email,
        refundMethod,
        items: items.map((item) => {
          const itemDetails = details[item.id] ?? EMPTY_ITEM_DETAILS
          return {
            itemId: item.id,
            // Step 3 doesn't let the customer through without a reason.
            reason: itemDetails.reason as ReturnReasonCode,
            notes: itemDetails.notes,
            photos: itemDetails.photos,
          }
        }),
      })
    } catch {
      setFailed(true)
      setSubmitting(false)
      return
    }
    onSubmitted(result)
  }

  return (
    <div className="grid gap-4">
      <div className="grid gap-1">
        <h1 className="text-xl font-semibold tracking-tight">Check your return</h1>
        <p className="text-sm wrap-anywhere text-muted-foreground">
          Order #{order.orderNumber} · {order.email}
        </p>
      </div>

      <Card className="grid gap-3">
        {items.map((item, index) => {
          const { reason, notes, photos } = details[item.id] ?? EMPTY_ITEM_DETAILS
          return (
            <Fragment key={item.id}>
              {index > 0 && <hr className="my-1" />}
              <section aria-label={item.name} className="grid gap-2.5">
                <div className="flex items-baseline justify-between gap-3">
                  <h2 className="font-semibold">{item.name}</h2>
                  <button
                    type="button"
                    onClick={() => onEditItem(index)}
                    className="cursor-pointer rounded-sm text-sm font-medium text-info-foreground underline-offset-4 outline-none hover:underline focus-visible:ring-3 focus-visible:ring-ring/50"
                  >
                    Edit<span className="sr-only"> {item.name}</span>
                  </button>
                </div>
                <dl className="grid gap-2.5">
                  <Row label="Reason">{reason ? reasonLabel(reason) : "—"}</Row>
                  <Row label="Details" title={notes || undefined}>
                    {notes || "None"}
                  </Row>
                  <Row label="Photos">{photos.length} added</Row>
                </dl>
              </section>
            </Fragment>
          )
        })}

        {items.length > 1 && <hr className="my-1" />}
        <dl className="grid gap-3">
          <Row label="Refund method">{refundMethodText}</Row>
          <div className="flex items-baseline justify-between gap-4 border-t pt-3">
            <dt className="text-[15px] font-semibold">Estimated refund</dt>
            <dd className="text-lg font-bold">{formatPaise(refund)}</dd>
          </div>
        </dl>
      </Card>

      <Card className="grid gap-2">
        <h2 className="text-[15px] font-semibold">What happens next</h2>
        <ol className="grid list-inside list-decimal gap-1.5 text-sm text-muted-foreground">
          <li>We check your request and create a return label.</li>
          <li>Drop off the parcel within 14 days and show the QR code.</li>
          <li>We inspect it and refund you. You can track every step.</li>
        </ol>
      </Card>

      <div className="flex items-center gap-3">
        <Checkbox
          id={confirmId}
          checked={confirmed}
          onCheckedChange={(value) => setConfirmed(value === true)}
        />
        <Label htmlFor={confirmId} className="leading-snug">
          The details and photos are accurate.
        </Label>
      </div>

      {failed && (
        <Alert variant="danger">
          <AlertTitle>We couldn’t submit your return</AlertTitle>
          <AlertDescription>Nothing was sent. Please try again in a moment.</AlertDescription>
        </Alert>
      )}

      <WizardFooter>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm">
            {items.length} {items.length === 1 ? "item" : "items"}
          </p>
          <p className="truncate text-[13px] text-muted-foreground">
            Est. refund {formatPaise(refund)}
          </p>
        </div>
        <Button variant="outline" onClick={onBack} disabled={submitting}>
          Back
        </Button>
        <Button onClick={handleSubmit} disabled={!confirmed || submitting}>
          {submitting && <LoaderCircle aria-hidden className="animate-spin" />}
          {submitting ? "Submitting…" : "Submit return"}
        </Button>
      </WizardFooter>
    </div>
  )
}

/** One label/value line; long values are cut off with "…". */
function Row({ label, title, children }: { label: string; title?: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-[auto_minmax(0,1fr)] items-baseline gap-4 text-sm">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="truncate text-right" title={title}>
        {children}
      </dd>
    </div>
  )
}
