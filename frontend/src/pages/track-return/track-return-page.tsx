import { useEffect, useRef, useState, type FormEvent } from "react"
import { LoaderCircle } from "lucide-react"
import { getReturn, type TrackedReturn } from "@/api/returns"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { TextField } from "@/components/ui/text-field"
import { returnStatusHref, START_RETURN_HREF } from "@/lib/hash-route"
import { CustomerHeader } from "@/pages/customer-wizard/customer-header"
import { rememberLoadedReturn } from "@/pages/return-status/recent-returns"

const LOOKUP_ERRORS = {
  not_found: {
    title: "We couldn’t find that return",
    description: "Check the RMA number in your confirmation email. It looks like RMA-2026-00731.",
  },
  unknown: {
    title: "Something went wrong",
    description: "We couldn’t look up your return just now. Please try again.",
  },
}

/** Look up a return by its RMA number, then open its status page. */
export function TrackReturnPage() {
  const [rmaNumber, setRmaNumber] = useState("")
  const [fieldError, setFieldError] = useState<string>()
  const [lookupError, setLookupError] = useState<keyof typeof LOOKUP_ERRORS | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    document.documentElement.scrollTop = 0
  }, [])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting) return

    const wanted = rmaNumber.trim().toUpperCase()
    if (!wanted) {
      setFieldError("Enter your RMA number")
      inputRef.current?.focus()
      return
    }

    setSubmitting(true)
    setLookupError(null)
    let tracked: TrackedReturn | null
    try {
      tracked = await getReturn(wanted)
    } catch {
      tracked = null
      setLookupError("unknown")
    }
    if (!tracked) {
      setLookupError((current) => current ?? "not_found")
      setSubmitting(false)
      return
    }
    rememberLoadedReturn(tracked)
    window.location.hash = returnStatusHref(tracked.rmaNumber)
  }

  const message = lookupError && LOOKUP_ERRORS[lookupError]

  return (
    <div className="min-h-svh bg-muted/50">
      <CustomerHeader current="track-return" />
      <main className="mx-auto grid w-full max-w-md gap-4 px-4 pt-6 pb-10 sm:pt-10">
        <div className="grid gap-1">
          <h1 className="text-xl font-semibold tracking-tight">Track your return</h1>
          <p className="text-sm text-muted-foreground">
            Enter the RMA number from your confirmation email to see where your return is.
          </p>
        </div>

        <Card>
          <form noValidate onSubmit={handleSubmit} aria-busy={submitting} className="grid gap-4">
            <TextField
              ref={inputRef}
              label="RMA number"
              name="rmaNumber"
              placeholder="RMA-2026-00731"
              autoComplete="off"
              autoCapitalize="characters"
              spellCheck={false}
              value={rmaNumber}
              onChange={(event) => {
                setRmaNumber(event.target.value)
                setFieldError(undefined)
              }}
              error={fieldError}
            />
            <Button type="submit" className="w-full" disabled={submitting}>
              {submitting && <LoaderCircle aria-hidden className="animate-spin" />}
              {submitting ? "Finding your return…" : "Track return"}
            </Button>
          </form>
        </Card>

        {message && (
          <Alert variant="danger">
            <AlertTitle>{message.title}</AlertTitle>
            <AlertDescription>{message.description}</AlertDescription>
          </Alert>
        )}

        <p className="text-sm text-muted-foreground">
          Need to send something back?{" "}
          <a
            href={START_RETURN_HREF}
            className="rounded-sm font-semibold text-foreground underline-offset-4 outline-none hover:underline focus-visible:ring-3 focus-visible:ring-ring/50"
          >
            Start a return
          </a>
        </p>
      </main>
    </div>
  )
}
