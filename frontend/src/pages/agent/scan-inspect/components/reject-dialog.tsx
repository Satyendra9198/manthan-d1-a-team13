import { useId, useState, type FormEvent } from 'react'
import type { AgentRejectReason, ReturnDetails } from '@/api/types'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Textarea } from '@/components/ui/textarea'
import { REJECT_REASON_LABELS, REJECT_REASONS, rejectNoteRequired } from '@/domain/returns'
import { formatPaise } from '@/lib/format'
import type { RejectInput } from '../use-scan-inspect'

type Props = {
  open: boolean
  data: ReturnDetails
  /** Pre-fills the note from the inspection notes field. */
  defaultNotes: string
  submitting: boolean
  serverError: string | null
  onSubmit: (input: RejectInput) => void
  onClose: () => void
}

export function RejectDialog({ open, onClose, ...rest }: Props) {
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent showCloseButton={false}>
        {/* Remount the form each time the popup opens so it starts fresh. */}
        {open && <RejectForm onClose={onClose} {...rest} />}
      </DialogContent>
    </Dialog>
  )
}

function RejectForm({
  data,
  defaultNotes,
  submitting,
  serverError,
  onSubmit,
  onClose,
}: Omit<Props, 'open'>) {
  const id = useId()
  const [reason, setReason] = useState<AgentRejectReason | null>(null)
  const [notes, setNotes] = useState(defaultNotes)
  const [errors, setErrors] = useState<{ reason?: string; notes?: string }>({})

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const next: typeof errors = {}
    if (!reason) next.reason = 'Pick a reason. The customer sees it on their status page.'
    if (reason && rejectNoteRequired(reason) && !notes.trim()) {
      next.notes = 'Add a note explaining the reason.'
    }
    setErrors(next)
    if (reason && !next.reason && !next.notes) onSubmit({ reason, notes })
  }

  return (
    <form onSubmit={handleSubmit} className="grid gap-5" noValidate>
      <DialogHeader>
        <DialogTitle>Reject return</DialogTitle>
        <DialogDescription>
          {data.rmaNumber} · {data.item.name} · {formatPaise(data.item.pricePaise)}
        </DialogDescription>
      </DialogHeader>

      <fieldset className="grid gap-3">
        <legend id={`${id}-reason`} className="mb-3 text-[15px]">
          Reason (shown to the customer)
        </legend>
        <RadioGroup
          value={reason ?? ''}
          onValueChange={(v) => {
            setReason(v as AgentRejectReason)
            setErrors((er) => ({ ...er, reason: undefined }))
          }}
          aria-labelledby={`${id}-reason`}
          aria-invalid={errors.reason ? true : undefined}
          aria-describedby={errors.reason ? `${id}-reason-error` : undefined}
          className="gap-3"
        >
          {REJECT_REASONS.map((r) => (
            <div key={r} className="flex items-center gap-3">
              <RadioGroupItem value={r} id={`${id}-${r}`} />
              <Label htmlFor={`${id}-${r}`} className="cursor-pointer">
                {REJECT_REASON_LABELS[r]}
              </Label>
            </div>
          ))}
        </RadioGroup>
        {errors.reason && (
          <p id={`${id}-reason-error`} className="text-sm text-destructive">
            {errors.reason}
          </p>
        )}
      </fieldset>

      <div className="grid gap-2">
        <Label htmlFor={`${id}-notes`}>
          Note{reason && rejectNoteRequired(reason) ? '' : ' (optional)'}
        </Label>
        <Textarea
          id={`${id}-notes`}
          value={notes}
          onChange={(e) => {
            setNotes(e.target.value)
            setErrors((er) => ({ ...er, notes: undefined }))
          }}
          placeholder="Visible wear on the hem; tags removed."
          aria-invalid={errors.notes ? true : undefined}
          aria-describedby={errors.notes ? `${id}-notes-error` : undefined}
        />
        {errors.notes && (
          <p id={`${id}-notes-error`} className="text-sm text-destructive">
            {errors.notes}
          </p>
        )}
      </div>

      <p className="rounded-md bg-danger px-4 py-3 text-sm text-danger-foreground">
        No refund will be paid. The customer sees this reason on their status page.
      </p>

      {serverError && (
        <p role="alert" className="text-sm text-destructive">
          {serverError}
        </p>
      )}

      <DialogFooter>
        <Button variant="ghost" onClick={onClose} disabled={submitting}>
          Cancel
        </Button>
        <Button type="submit" variant="destructive" disabled={submitting}>
          {submitting ? 'Rejecting…' : 'Reject return'}
        </Button>
      </DialogFooter>
    </form>
  )
}
