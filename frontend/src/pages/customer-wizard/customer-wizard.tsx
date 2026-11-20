import { useEffect, useRef, useState } from "react"
import type { Order } from "@/api/orders"
import type { ItemReturnDetails, RefundMethod, TrackedReturn } from "@/api/returns"
import { StepProgress } from "@/components/ui/step-progress"
import { ChooseItemsStep } from "./choose-items-step"
import { CustomerHeader } from "./customer-header"
import { FindOrderStep } from "./find-order-step"
import { ReasonPhotosStep } from "./reason-photos-step"
import { RefundMethodStep } from "./refund-method-step"
import { ReturnSubmitted } from "./return-submitted"
import { ReviewStep } from "./review-step"

/** Name of each step for the progress caption. */
const STEP_LABELS = [
  "Find your order",
  "Choose items",
  "Reason and photos",
  "Refund method",
  "Review",
]
const TOTAL_STEPS = STEP_LABELS.length

/** The start-a-return page. "Start a return" after submitting begins a fresh wizard. */
export function CustomerWizard() {
  const [run, setRun] = useState(0)
  return <ReturnWizard key={run} onRestart={() => setRun((count) => count + 1)} />
}

/** Customer return wizard: find the order, pick items, give reasons, pick a refund, submit. */
function ReturnWizard({ onRestart }: { onRestart: () => void }) {
  const [step, setStep] = useState(1)
  const [order, setOrder] = useState<Order | null>(null)
  const [selectedItemIds, setSelectedItemIds] = useState<string[]>([])
  const [itemDetails, setItemDetails] = useState<Record<string, ItemReturnDetails>>({})
  const [reasonItemIndex, setReasonItemIndex] = useState(0)
  const [refundMethod, setRefundMethod] = useState<RefundMethod>("original_payment")
  const [submitted, setSubmitted] = useState<TrackedReturn | null>(null)
  const stepRef = useRef<HTMLDivElement>(null)
  const view = submitted ? "submitted" : step
  const shownView = useRef(view)

  // When the screen changes, start at the top and move focus to it so
  // keyboard and screen-reader users land on the new content.
  useEffect(() => {
    if (shownView.current === view) return
    shownView.current = view
    document.documentElement.scrollTop = 0
    stepRef.current?.focus({ preventScroll: true })
  }, [view])

  const selectedItems = order ? order.items.filter((item) => selectedItemIds.includes(item.id)) : []
  const estimatedRefund = selectedItems.reduce((sum, item) => sum + item.pricePaise, 0)

  function renderStep() {
    if (step === 1 || !order) {
      return (
        <FindOrderStep
          defaultValues={order ? { orderNumber: order.orderNumber, email: order.email } : undefined}
          onFound={(found) => {
            // A different order means earlier answers no longer apply.
            if (found.orderNumber !== order?.orderNumber) {
              setSelectedItemIds([])
              setItemDetails({})
              setRefundMethod("original_payment")
            }
            setOrder(found)
            setStep(2)
          }}
        />
      )
    }

    if (step === 2) {
      return (
        <ChooseItemsStep
          order={order}
          selectedIds={selectedItemIds}
          onSelectedIdsChange={setSelectedItemIds}
          onBack={() => setStep(1)}
          onContinue={() => {
            setReasonItemIndex(0)
            setStep(3)
          }}
        />
      )
    }

    if (step === 3) {
      return (
        <ReasonPhotosStep
          items={selectedItems}
          details={itemDetails}
          onDetailsChange={(itemId, details) =>
            setItemDetails((current) => ({ ...current, [itemId]: details }))
          }
          activeIndex={reasonItemIndex}
          onActiveIndexChange={setReasonItemIndex}
          onBack={() => setStep(2)}
          onContinue={() => setStep(4)}
        />
      )
    }

    if (step === 4) {
      return (
        <RefundMethodStep
          order={order}
          refund={estimatedRefund}
          value={refundMethod}
          onValueChange={setRefundMethod}
          onBack={() => {
            // Coming back into step 3 lands on its last item.
            setReasonItemIndex(selectedItems.length - 1)
            setStep(3)
          }}
          onContinue={() => setStep(5)}
        />
      )
    }

    return (
      <ReviewStep
        order={order}
        items={selectedItems}
        details={itemDetails}
        refundMethod={refundMethod}
        refund={estimatedRefund}
        onEditItem={(index) => {
          setReasonItemIndex(index)
          setStep(3)
        }}
        onBack={() => setStep(4)}
        onSubmitted={setSubmitted}
      />
    )
  }

  return (
    <div className="min-h-svh bg-muted/50">
      <CustomerHeader current="start-return" onStartReturn={submitted ? onRestart : undefined} />
      <main className="mx-auto grid w-full max-w-md gap-5 px-4 pt-4 pb-10 sm:pt-10">
        {!submitted && (
          <StepProgress current={step} total={TOTAL_STEPS} label={STEP_LABELS[step - 1]} />
        )}

        <div ref={stepRef} tabIndex={-1} className="outline-none">
          {submitted ? (
            <ReturnSubmitted tracked={submitted} />
          ) : (
            renderStep()
          )}
        </div>
      </main>
    </div>
  )
}
