import * as React from "react"
import { cn } from "@/lib/utils"

type StepProgressProps = React.ComponentProps<"div"> & {
  /** Current step, starting at 1. */
  current: number
  /** Total number of steps. */
  total: number
  /** Name of the current step, shown after "Step 1 of 5 ·". */
  label?: string
}

/**
 * Wizard progress from the ReturnRight design: a "Step 1 of 5 · Find your order"
 * caption over one bar per step, filled up to the current step.
 *
 *   <StepProgress current={1} total={5} label="Find your order" />
 */
function StepProgress({ current, total, label, className, ...props }: StepProgressProps) {
  return (
    <div data-slot="step-progress" className={cn("grid gap-2", className)} {...props}>
      <p className="text-[13px] text-muted-foreground">
        Step {current} of {total}
        {label && ` · ${label}`}
      </p>
      {/* The caption above already says where we are; the bars are visual only. */}
      <div aria-hidden className="flex gap-1.5">
        {Array.from({ length: total }, (_, index) => (
          <span
            key={index}
            data-slot="step-progress-segment"
            data-complete={index < current || undefined}
            className={cn("h-1 flex-1 rounded-full bg-border", index < current && "bg-primary")}
          />
        ))}
      </div>
    </div>
  )
}

export { StepProgress, type StepProgressProps }
