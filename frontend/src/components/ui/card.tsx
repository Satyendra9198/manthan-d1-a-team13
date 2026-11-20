import * as React from "react"
import { cn } from "@/lib/utils"

/**
 * ReturnRight card: white panel with a light border, used to group a form or summary.
 *
 *   <Card>…</Card>
 */
function Card({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="card"
      className={cn("rounded-lg border bg-card p-4 text-card-foreground", className)}
      {...props}
    />
  )
}

export { Card }
