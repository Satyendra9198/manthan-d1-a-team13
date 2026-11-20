import { useEffect, useId, useRef, useState } from 'react'
import type { FinanceQueueItem } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { inspectedLine, payoutLine } from '../finance'

type Props = {
  id: string
  item: FinanceQueueItem
  busy: 'approve' | 'decline' | null
  error: string | null
  onApprove: () => void
  onDecline: (note: string) => void
  onClose: () => void
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-[13px] text-muted-foreground">{label}</dt>
      <dd className="mt-0.5 text-[15px]">{value}</dd>
    </div>
  )
}

/** Shown below the queue after "Review": approve and pay, or decline with a reason. */
export function ReviewPanel({ id, item, busy, error, onApprove, onDecline, onClose }: Props) {
  const titleId = useId()
  const noteId = useId()
  const ref = useRef<HTMLElement>(null)
  const [declining, setDeclining] = useState(false)
  const [note, setNote] = useState('')
  const [noteError, setNoteError] = useState<string | null>(null)

  useEffect(() => {
    ref.current?.focus()
    ref.current?.scrollIntoView?.({ block: 'nearest' })
  }, [item.returnId])

  function confirmDecline() {
    if (!note.trim()) return setNoteError('Add a reason. The customer sees it on their status page.')
    onDecline(note)
  }

  return (
    <section
      ref={ref}
      id={id}
      tabIndex={-1}
      aria-labelledby={titleId}
      onKeyDown={(e) => e.key === 'Escape' && !busy && onClose()}
      className="w-full max-w-[480px] rounded-xl border bg-background p-6 shadow-lg outline-none"
    >
      <h2 id={titleId} className="text-lg font-semibold">
        {declining ? 'Decline refund' : 'Approve refund'} · {item.rmaNumber}
      </h2>

      <dl className="mt-4 grid gap-3">
        <Row label="Amount" value={payoutLine(item)} />
        <Row label="Inspected by" value={inspectedLine(item)} />
        <Row label="Idempotency key" value={item.refundKey} />
      </dl>

      {declining ? (
        <div className="mt-4 grid gap-2">
          <Label htmlFor={noteId}>Reason shown to the customer</Label>
          <Textarea
            id={noteId}
            autoFocus
            value={note}
            onChange={(e) => {
              setNote(e.target.value)
              setNoteError(null)
            }}
            placeholder="Serial number doesn't match the order."
            aria-invalid={noteError ? true : undefined}
            aria-describedby={noteError ? `${noteId}-error` : undefined}
          />
          {noteError && (
            <p id={`${noteId}-error`} className="text-sm text-destructive">
              {noteError}
            </p>
          )}
          <p className="text-[13px] text-muted-foreground">
            No refund will be paid and the return is closed as rejected.
          </p>
        </div>
      ) : (
        <p className="mt-3 text-[13px] text-muted-foreground">
          Clicking twice or retrying after a timeout will not pay twice. The same key is reused.
        </p>
      )}

      {error && (
        <p role="alert" className="mt-3 text-sm text-destructive">
          {error}
        </p>
      )}

      <div className="mt-5 flex flex-wrap justify-end gap-3">
        {declining ? (
          <>
            <Button variant="ghost" onClick={() => setDeclining(false)} disabled={!!busy}>
              Back
            </Button>
            <Button variant="destructive" onClick={confirmDecline} disabled={!!busy}>
              {busy === 'decline' ? 'Declining…' : 'Confirm decline'}
            </Button>
          </>
        ) : (
          <>
            <Button variant="ghost" onClick={onClose} disabled={!!busy}>
              Cancel
            </Button>
            <Button variant="destructive" onClick={() => setDeclining(true)} disabled={!!busy}>
              Decline
            </Button>
            <Button onClick={onApprove} disabled={!!busy}>
              {busy === 'approve' ? 'Paying…' : 'Approve and pay'}
            </Button>
          </>
        )}
      </div>
    </section>
  )
}
