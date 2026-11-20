import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils"

/**
 * ReturnRight inline message: a tinted panel with a bold title and body text.
 * Uses the same tones as <Badge>.
 *
 *   <Alert variant="danger">
 *     <AlertTitle>We couldn’t find that order</AlertTitle>
 *     <AlertDescription>Check the order number…</AlertDescription>
 *   </Alert>
 *
 * Renders with role="alert" so screen readers announce it; pass role="status"
 * for messages that aren't urgent.
 */
const alertVariants = cva("grid gap-1 rounded-lg px-4 py-3 text-sm", {
  variants: {
    variant: {
      default: "bg-muted text-foreground",
      info: "bg-info text-info-foreground",
      success: "bg-success text-success-foreground",
      warning: "bg-warning text-warning-foreground",
      danger: "bg-danger text-danger-foreground",
    },
  },
  defaultVariants: {
    variant: "default",
  },
})

type AlertProps = React.ComponentProps<"div"> & VariantProps<typeof alertVariants>

function Alert({ className, variant = "default", role = "alert", ...props }: AlertProps) {
  return (
    <div
      data-slot="alert"
      data-variant={variant}
      role={role}
      className={cn(alertVariants({ variant }), className)}
      {...props}
    />
  )
}

function AlertTitle({ className, ...props }: React.ComponentProps<"p">) {
  return <p data-slot="alert-title" className={cn("font-semibold", className)} {...props} />
}

function AlertDescription({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="alert-description"
      className={cn("text-[13px] leading-5", className)}
      {...props}
    />
  )
}

export { Alert, AlertTitle, AlertDescription, alertVariants, type AlertProps }
