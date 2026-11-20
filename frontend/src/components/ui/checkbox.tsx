import * as React from "react"
import { CheckIcon } from "lucide-react"
import { Checkbox as CheckboxPrimitive } from "radix-ui"
import { cn } from "@/lib/utils"

/**
 * ReturnRight checkbox: square box that fills black with a white tick when checked.
 *
 *   <Checkbox checked={on} onCheckedChange={(value) => setOn(value === true)} />
 */
function Checkbox({ className, ...props }: React.ComponentProps<typeof CheckboxPrimitive.Root>) {
  return (
    <CheckboxPrimitive.Root
      data-slot="checkbox"
      className={cn(
        "peer grid size-[18px] shrink-0 cursor-pointer place-items-center rounded-[4px] border border-muted-foreground/60 bg-background text-primary-foreground transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:border-border disabled:bg-muted aria-invalid:border-destructive data-[state=checked]:border-primary data-[state=checked]:bg-primary",
        className
      )}
      {...props}
    >
      <CheckboxPrimitive.Indicator data-slot="checkbox-indicator" className="grid place-items-center">
        <CheckIcon className="size-3.5" strokeWidth={3} />
      </CheckboxPrimitive.Indicator>
    </CheckboxPrimitive.Root>
  )
}

export { Checkbox }
