import { useState, type FormEvent } from 'react'
import { Button } from '@/components/ui/button'
import { TextField } from '@/components/ui/text-field'

type TrackLookupFormProps = {
  /** Pre-filled and locked when the RMA is already known (from the URL). */
  rmaNumber?: string
  /** Shown above the button, e.g. "We couldn't find that return…". */
  error?: string | null
  onSubmit: (rmaNumber: string, email: string) => void
}

/** "Track my return": the return number from the label plus the email used for the order. */
export function TrackLookupForm({ rmaNumber, error, onSubmit }: TrackLookupFormProps) {
  const [rma, setRma] = useState(rmaNumber ?? '')
  const [email, setEmail] = useState('')
  const [missing, setMissing] = useState(false)

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!rma.trim() || !email.trim()) {
      setMissing(true)
      return
    }
    setMissing(false)
    onSubmit(rma.trim().toUpperCase(), email.trim())
  }

  return (
    <form className="grid gap-4 rounded-xl border bg-background p-4" onSubmit={submit} noValidate>
      <div>
        <h1 className="text-lg font-semibold">Track your return</h1>
        <p className="mt-0.5 text-sm text-muted-foreground">
          Enter the return number from your label and the email you used for the order.
        </p>
      </div>
      <TextField
        label="Return number"
        placeholder="RMA-2026-00731"
        value={rma}
        onChange={(e) => setRma(e.target.value)}
        readOnly={!!rmaNumber}
      />
      <TextField
        label="Email"
        type="email"
        autoComplete="email"
        placeholder="you@example.com"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        autoFocus={!!rmaNumber}
      />
      {(missing || error) && (
        <p role="alert" className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
          {missing ? 'Enter the return number and the email.' : error}
        </p>
      )}
      <Button type="submit">Track return</Button>
    </form>
  )
}
