import { useId, useState } from 'react'
import { Link } from 'react-router'
import type { CustomerProfile, FlagReviewRequest } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { formatDateTime } from '@/lib/format'

type Props = {
  profile: CustomerProfile
  saving: FlagReviewRequest['action'] | null
  error: string | null
  onReview: (body: FlagReviewRequest) => Promise<boolean | undefined>
}

export function ReviewFlagPanel({ profile, saving, error, onReview }: Props) {
  const id = useId()
  const [note, setNote] = useState('')
  const [noteError, setNoteError] = useState<string | null>(null)
  const last = profile.flag.lastReview

  async function submit(action: FlagReviewRequest['action']) {
    if (!note.trim()) {
      setNoteError('Write why you are keeping or clearing the flag.')
      return
    }
    if (await onReview({ action, note })) setNote('')
  }

  return (
    <section aria-labelledby={`${id}-title`} className="rounded-xl border bg-background p-5">
      <h2 id={`${id}-title`} className="text-[15px] font-semibold">
        Review this flag
      </h2>

      {profile.flag.active ? (
        <>
          <p className="mt-2 text-[13px] text-muted-foreground">
            While flagged, all of this customer's refunds go to finance, whatever the amount.
          </p>
          <div className="mt-4 grid gap-2">
            <Label htmlFor={`${id}-note`}>Review note (required)</Label>
            <Textarea
              id={`${id}-note`}
              value={note}
              onChange={(e) => {
                setNote(e.target.value)
                setNoteError(null)
              }}
              placeholder="Two returns are size issues and one is a defect confirmed at inspection. No abuse pattern."
              aria-invalid={noteError ? true : undefined}
              aria-describedby={noteError ? `${id}-note-error` : undefined}
              className="min-h-24"
            />
            {noteError && (
              <p id={`${id}-note-error`} className="text-sm text-destructive">
                {noteError}
              </p>
            )}
          </div>
          {error && (
            <p role="alert" className="mt-3 text-sm text-destructive">
              {error}
            </p>
          )}
          <div className="mt-4 flex justify-end gap-3">
            <Button variant="outline" onClick={() => void submit('keep')} disabled={!!saving}>
              {saving === 'keep' ? 'Saving…' : 'Keep flag'}
            </Button>
            <Button onClick={() => void submit('clear')} disabled={!!saving}>
              {saving === 'clear' ? 'Clearing…' : 'Clear flag'}
            </Button>
          </div>
          <p className="mt-4 text-xs text-muted-foreground">
            Clearing writes a FLAG_CHANGED row to the audit log. A new return that takes the count over 3 raises the
            flag again.
          </p>
        </>
      ) : (
        <p className="mt-2 text-[13px] text-muted-foreground">
          {last?.action === 'clear'
            ? 'The flag is cleared. A new return that takes the count over 3 raises it again.'
            : "This customer isn't flagged. The flag is raised automatically after more than 3 returns in 30 days."}
        </p>
      )}

      {last && (
        <div className="mt-4 rounded-md bg-muted/60 px-3 py-2.5 text-xs" data-testid="last-review">
          <p className="font-medium">
            Last review: {last.action === 'clear' ? 'cleared' : 'kept'} by {last.by} · {formatDateTime(last.at)}
          </p>
          <p className="mt-1 text-muted-foreground">{last.note}</p>
        </div>
      )}
      <Link
        to={`/audit?customer=${encodeURIComponent(profile.customerId)}&days=all`}
        className="mt-3 inline-block text-xs font-medium hover:underline"
      >
        View this customer in the audit log
      </Link>
    </section>
  )
}
