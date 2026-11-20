import * as React from "react"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { cn } from "@/lib/utils"

type TextFieldProps = React.ComponentProps<"input"> & {
  /** Visible label rendered above the input. */
  label: string
  /** Helper text shown under the input. Hidden when `error` is set. */
  hint?: string
  /** Error message; also marks the input as invalid. */
  error?: string
  /** Class for the wrapper element. */
  containerClassName?: string
}

/**
 * Label + Input pair from the ReturnRight design.
 *
 *   <TextField label="Order number" placeholder="Value" />
 */
function TextField({
  label,
  hint,
  error,
  id,
  containerClassName,
  ...inputProps
}: TextFieldProps) {
  const generatedId = React.useId()
  const inputId = id ?? generatedId
  const messageId = `${inputId}-message`
  const message = error ?? hint

  return (
    <div data-slot="text-field" className={cn("grid gap-2", containerClassName)}>
      <Label htmlFor={inputId}>{label}</Label>
      <Input
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={message ? messageId : undefined}
        {...inputProps}
      />
      {message && (
        <p
          id={messageId}
          className={cn("text-sm", error ? "text-destructive" : "text-muted-foreground")}
        >
          {message}
        </p>
      )}
    </div>
  )
}

export { TextField, type TextFieldProps }
