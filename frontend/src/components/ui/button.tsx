import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"
import { cn } from "@/lib/utils"
import { Slot } from "radix-ui"

/**
 * ReturnRight button.
 *
 * Variants (from the design):
 *  - default      solid black, white text      → primary action
 *  - outline      white, light grey border     → secondary action
 *  - destructive  solid red, white text        → dangerous action
 *  - ghost        no background                → tertiary / inline action
 * Extra: `secondary` (soft grey) and `link`.
 */
const buttonVariants = cva(
  "inline-flex shrink-0 items-center justify-center gap-2 rounded-[6px] border border-transparent font-normal whitespace-nowrap transition-colors outline-none select-none cursor-pointer focus-visible:ring-3 focus-visible:ring-ring/50 disabled:pointer-events-none disabled:opacity-50 aria-invalid:ring-3 aria-invalid:ring-destructive/20 [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
  {
    variants: {
      variant: {
        default: "bg-primary text-primary-foreground hover:bg-primary/85",
        outline:
          "border-border bg-background text-foreground hover:bg-muted dark:border-input dark:bg-input/30 dark:hover:bg-input/50",
        destructive:
          "bg-destructive text-destructive-foreground hover:bg-destructive/90 focus-visible:ring-destructive/30",
        ghost: "text-foreground hover:bg-muted dark:hover:bg-muted/50",
        secondary: "bg-secondary text-secondary-foreground hover:bg-secondary/80",
        link: "text-foreground underline-offset-4 hover:underline",
      },
      size: {
        default: "h-10 px-5 text-[15px]",
        sm: "h-8 px-3 text-sm",
        lg: "h-11 px-6 text-base",
        icon: "size-10",
        "icon-sm": "size-8",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
)

type ButtonProps = React.ComponentProps<"button"> &
  VariantProps<typeof buttonVariants> & {
    /** Render as the child element (e.g. a router <Link>) instead of <button>. */
    asChild?: boolean
  }

function Button({
  className,
  variant = "default",
  size = "default",
  asChild = false,
  type,
  ...props
}: ButtonProps) {
  const Comp = asChild ? Slot.Root : "button"

  return (
    <Comp
      data-slot="button"
      data-variant={variant}
      data-size={size}
      // Default to type="button" so buttons never submit forms by accident.
      type={asChild ? undefined : (type ?? "button")}
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    />
  )
}

export { Button, buttonVariants, type ButtonProps }
