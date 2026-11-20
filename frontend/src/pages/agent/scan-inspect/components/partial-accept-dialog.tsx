import { useId, useState, type FormEvent } from 'react'
import type { Grade, ReturnDetails } from '@/api/types'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { TextField } from '@/components/ui/text-field'
import { maxPartialRefund, validatePartialRefund, type PartialRefundError } from '@/domain/returns'
import { formatPaise, parseRupeesToPaise } from '@/lib/format'
import { routingHint } from '../inspection'
import type { PartialAcceptInput } from '../use-scan-inspect'

type Props = {
  open: boolean
  data: ReturnDetails
  grade: Grade
  financeApprovalAbovePaise: number
  defaultNotes: string
  submitting: boolean
  serverError: string | null
  onSubmit: (input: PartialAcceptInput) => void
  onClose: () => void
}

export function PartialAcceptDialog({ open, onClose, ...rest }: Props) {
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent showCloseButton={false}>
        {open && <PartialAcceptForm onClose={onClose} {...rest} />}
      </DialogContent>
    </Dialog>
  )
}

function amountError(err: PartialRefundError, max: number, grade: Grade): string {
  switch (err) {
    case 'REQUIRED':
      return 'Enter the refund amount.'
    case 'NOT_POSITIVE':
      return 'Enter an amount above ₹0, with at most 2 decimals.'
    case 'ABOVE_MAX':
      return `Can't be more than ${formatPaise(max)} for grade ${grade}.`
  }
}

function PartialAcceptForm({
  data,
  grade,
  financeApprovalAbovePaise,
  defaultNotes,
  submitting,
  serverError,
  onSubmit,
  onClose,
}: Omit<Props, 'open'>) {
  const id = useId()
  const price = data.item.pricePaise
  const max = maxPartialRefund(price, grade)
  const [amountText, setAmountText] = useState('')
  const [reason, setReason] = useState('')
  const [notes, setNotes] = useState(defaultNotes)
  const [errors, setErrors] = useState<{ amount?: string; reason?: string }>({})

  const amount = parseRupeesToPaise(amountText)
  const amountValid = validatePartialRefund(amount, max) === null
  const percent = amountValid ? Math.round((amount! / price) * 100) : null

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const err = validatePartialRefund(amount, max)
    const next: typeof errors = {}
    if (err) next.amount = amountError(err, max, grade)
    if (!reason.trim()) next.reason = 'Add a reason. The customer sees it.'
    setErrors(next)
    if (!next.amount && !next.reason) onSubmit({ refundAmountPaise: amount!, reason, notes })
  }

  return (
    <form onSubmit={handleSubmit} className="grid gap-5" noValidate>
      <DialogHeader>
        <DialogTitle>Partially accept</DialogTitle>
        <DialogDescription>
          {data.rmaNumber} · {data.item.name} · Grade {grade} · {formatPaise(price)}
        </DialogDescription>
      </DialogHeader>

      <div className="grid gap-2">
        <Label htmlFor={`${id}-amount`}>Refund amount (max {formatPaise(max)})</Label>
        <div className="relative">
          <span
            aria-hidden
            className="pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-[15px] text-muted-foreground"
          >
            ₹
          </span>
          <Input
            id={`${id}-amount`}
            inputMode="decimal"
            autoComplete="off"
            autoFocus
            placeholder="3,000"
            value={amountText}
            onChange={(e) => {
              setAmountText(e.target.value)
              setErrors((er) => ({ ...er, amount: undefined }))
            }}
            aria-invalid={errors.amount ? true : undefined}
            aria-describedby={errors.amount ? `${id}-amount-error` : undefined}
            className="pl-7"
          />
        </div>
        {errors.amount && (
          <p id={`${id}-amount-error`} className="text-sm text-destructive">
            {errors.amount}
          </p>
        )}
      </div>

      <TextField
        label="Reason"
        placeholder="Missing parts or accessories"
        value={reason}
        onChange={(e) => {
          setReason(e.target.value)
          setErrors((er) => ({ ...er, reason: undefined }))
        }}
        error={errors.reason}
      />

      <div className="grid gap-2">
        <Label htmlFor={`${id}-notes`}>Note</Label>
        <Textarea
          id={`${id}-notes`}
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          placeholder="Original box and spare laces missing."
        />
      </div>

      {amountValid && (
        <>
          <div
            data-testid="partial-preview"
            className="flex items-center justify-between gap-4 rounded-md bg-warning px-4 py-3 text-warning-foreground"
          >
            <span>Customer gets {percent}% of the price</span>
            <span className="text-xl font-semibold">{formatPaise(amount!)}</span>
          </div>
          <p className="text-sm text-muted-foreground">
            {routingHint(amount!, financeApprovalAbovePaise, data.customer.flaggedForReview, 'dialog')}
          </p>
        </>
      )}

      {serverError && (
        <p role="alert" className="text-sm text-destructive">
          {serverError}
        </p>
      )}

      <DialogFooter>
        <Button variant="ghost" onClick={onClose} disabled={submitting}>
          Cancel
        </Button>
        <Button type="submit" disabled={submitting}>
          {submitting ? 'Saving…' : 'Confirm partial accept'}
        </Button>
      </DialogFooter>
    </form>
  )
}
