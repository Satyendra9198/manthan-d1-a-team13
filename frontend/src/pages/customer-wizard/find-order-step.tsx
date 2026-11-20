import { useRef, useState, type FormEvent } from "react"
import { LoaderCircle } from "lucide-react"
import {
  findOrder,
  OrderLookupError,
  type Order,
  type OrderLookupErrorCode,
} from "@/api/orders"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { TextField } from "@/components/ui/text-field"
import { Link } from "react-router"
import { TRACK_RETURN_PATH } from "@/config/customer-paths"

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const LOOKUP_ERRORS: Record<OrderLookupErrorCode, { title: string; description: string }> = {
  not_found: {
    title: "We couldn’t find that order",
    description:
      "Check the order number and use the same email as on the order. After 5 tries, please wait a few minutes.",
  },
  rate_limited: {
    title: "Too many tries",
    description: "Please wait a few minutes, then try again.",
  },
  unknown: {
    title: "Something went wrong",
    description: "We couldn’t look up your order just now. Please try again.",
  },
}

type FieldErrors = { orderNumber?: string; email?: string }

function validate(orderNumber: string, email: string): FieldErrors {
  const errors: FieldErrors = {}
  if (!orderNumber) errors.orderNumber = "Enter your order number"
  if (!email) errors.email = "Enter your email"
  else if (!EMAIL_PATTERN.test(email)) errors.email = "Enter a valid email, like name@example.com"
  return errors
}

type FindOrderStepProps = {
  /** Called with the order once the lookup succeeds. */
  onFound: (order: Order) => void
  /** Pre-fills the form, e.g. when the customer comes back from step 2. */
  defaultValues?: { orderNumber: string; email: string }
}

/** Wizard step 1: find the order by order number + email. */
export function FindOrderStep({ onFound, defaultValues }: FindOrderStepProps) {
  const [orderNumber, setOrderNumber] = useState(defaultValues?.orderNumber ?? "")
  const [email, setEmail] = useState(defaultValues?.email ?? "")
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [lookupError, setLookupError] = useState<OrderLookupErrorCode | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const orderNumberRef = useRef<HTMLInputElement>(null)
  const emailRef = useRef<HTMLInputElement>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) return

    const input = { orderNumber: orderNumber.trim(), email: email.trim() }
    const errors = validate(input.orderNumber, input.email)
    setFieldErrors(errors)
    if (errors.orderNumber) return orderNumberRef.current?.focus()
    if (errors.email) return emailRef.current?.focus()

    setSubmitting(true)
    setLookupError(null)
    let order: Order
    try {
      order = await findOrder(input)
    } catch (error) {
      setLookupError(error instanceof OrderLookupError ? error.code : "unknown")
      setSubmitting(false)
      return
    }
    onFound(order)
  }

  const lookupMessage = lookupError && LOOKUP_ERRORS[lookupError]

  return (
    <div className="grid gap-4">
      <div className="grid gap-1">
        <h1 className="text-xl font-semibold tracking-tight">Start a return</h1>
        <p className="text-sm text-muted-foreground">
          Enter the order number and the email you used to place the order. No account needed.
        </p>
      </div>

      <Card>
        <form noValidate onSubmit={handleSubmit} aria-busy={submitting} className="grid gap-4">
          <div className="grid gap-5">
            <TextField
              ref={orderNumberRef}
              label="Order number"
              name="orderNumber"
              placeholder="RR-10482"
              autoComplete="off"
              autoCapitalize="characters"
              spellCheck={false}
              value={orderNumber}
              onChange={(event) => {
                setOrderNumber(event.target.value)
                setFieldErrors((current) => ({ ...current, orderNumber: undefined }))
              }}
              error={fieldErrors.orderNumber}
            />
            <TextField
              ref={emailRef}
              label="Email"
              name="email"
              type="email"
              placeholder="ananya.rao@example.com"
              autoComplete="email"
              value={email}
              onChange={(event) => {
                setEmail(event.target.value)
                setFieldErrors((current) => ({ ...current, email: undefined }))
              }}
              error={fieldErrors.email}
            />
          </div>

          <p className="text-[13px] text-muted-foreground">
            You’ll find the order number in your order confirmation email.
          </p>

          <Button type="submit" className="w-full" disabled={submitting}>
            {submitting && <LoaderCircle aria-hidden className="animate-spin" />}
            {submitting ? "Finding your order…" : "Find my order"}
          </Button>
        </form>
      </Card>

      {lookupMessage && (
        <Alert variant="danger">
          <AlertTitle>{lookupMessage.title}</AlertTitle>
          <AlertDescription>{lookupMessage.description}</AlertDescription>
        </Alert>
      )}

      <p className="text-sm text-muted-foreground">
        Already started a return?{" "}
        <Link
          to={TRACK_RETURN_PATH}
          className="rounded-sm font-semibold text-foreground underline-offset-4 outline-none hover:underline focus-visible:ring-3 focus-visible:ring-ring/50"
        >
          Track it
        </Link>
      </p>
    </div>
  )
}
