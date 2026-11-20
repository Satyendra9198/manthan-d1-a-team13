import { useId } from "react"
import type { Order } from "@/api/orders"
import { REFUND_METHODS, type RefundMethod } from "@/api/returns"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { formatPaise } from "@/lib/format"
import { cn } from "@/lib/utils"
import { WizardFooter } from "./wizard-footer"

type RefundMethodStepProps = {
  order: Order
  /** Estimated refund for the items being returned. */
  refund: number
  value: RefundMethod
  onValueChange: (value: RefundMethod) => void
  onBack: () => void
  onContinue: () => void
}

type RefundOption = {
  value: RefundMethod
  description: string
  timing: string
  timingVariant: "default" | "success"
}

/** Wizard step 4: refund to the original payment method or as store credit. */
export function RefundMethodStep({
  order,
  refund,
  value,
  onValueChange,
  onBack,
  onContinue,
}: RefundMethodStepProps) {
  const headingId = useId()
  const options: RefundOption[] = [
    {
      value: "original_payment",
      description: `${order.payment.method} · ${order.payment.account}`,
      timing: "3–5 business days after inspection",
      timingVariant: "default",
    },
    {
      value: "store_credit",
      description: "Added to your ReturnRight wallet",
      timing: "Instant after inspection",
      timingVariant: "success",
    },
  ]

  return (
    <div className="grid gap-4">
      <div className="grid gap-1">
        <h1 id={headingId} className="text-xl font-semibold tracking-tight">
          How would you like your refund?
        </h1>
        <p className="text-sm text-muted-foreground">You can’t change this after you submit.</p>
      </div>

      <RadioGroup
        value={value}
        onValueChange={(next) => onValueChange(next as RefundMethod)}
        aria-labelledby={headingId}
        className="gap-4"
      >
        {options.map((option) => (
          <RefundOptionCard key={option.value} option={option} checked={option.value === value} />
        ))}
      </RadioGroup>

      <Alert variant="info" role="note">
        <AlertTitle>Your final amount depends on inspection</AlertTitle>
        <AlertDescription>
          Items we grade as damaged (Grade C) are refunded at 50%. We’ll tell you the amount
          before paying it.
        </AlertDescription>
      </Alert>

      <WizardFooter>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm">{REFUND_METHODS[value]}</p>
          <p className="truncate text-[13px] text-muted-foreground">
            Est. refund {formatPaise(refund)}
          </p>
        </div>
        <Button variant="outline" onClick={onBack}>
          Back
        </Button>
        <Button onClick={onContinue}>Continue</Button>
      </WizardFooter>
    </div>
  )
}

/** One refund option as a selectable card; the whole card picks it. */
function RefundOptionCard({ option, checked }: { option: RefundOption; checked: boolean }) {
  const titleId = useId()
  const detailsId = useId()

  return (
    <label
      className={cn(
        "grid cursor-pointer grid-cols-[auto_1fr] gap-x-3 rounded-lg border bg-card p-4 transition-colors hover:border-foreground/40",
        checked && "border-primary ring-1 ring-primary hover:border-primary",
      )}
    >
      <RadioGroupItem
        value={option.value}
        className="mt-0.5"
        aria-labelledby={titleId}
        aria-describedby={detailsId}
      />
      <div className="grid gap-1">
        <span id={titleId} className="font-medium">
          {REFUND_METHODS[option.value]}
        </span>
        <div id={detailsId} className="grid gap-2">
          <p className="text-sm text-muted-foreground">{option.description}</p>
          <Badge variant={option.timingVariant} className="text-[13px]">
            {option.timing}
          </Badge>
        </div>
      </div>
    </label>
  )
}
