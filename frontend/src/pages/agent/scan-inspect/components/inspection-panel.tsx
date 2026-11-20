import { useEffect, useRef } from 'react'
import type { Decision, Grade, InspectionConfig, ReturnDetails } from '@/api/types'
import { Button, type ButtonProps } from '@/components/ui/button'
import { TextField } from '@/components/ui/text-field'
import { refundForGrade } from '@/domain/returns'
import { formatPaise } from '@/lib/format'
import { DECISION_SHORTCUTS, routingHint } from '../inspection'
import { GradeSelector } from './grade-selector'
import { Panel } from './panel'

type Props = {
  data: ReturnDetails
  config: InspectionConfig
  grade: Grade | null
  onGradeChange: (grade: Grade) => void
  notes: string
  onNotesChange: (notes: string) => void
  submitting: Decision | null
  /** False while a popup is open, so its typing doesn't trigger shortcuts. */
  shortcutsEnabled: boolean
  onAccept: () => void
  onPartialAccept: () => void
  onReject: () => void
}

function isTyping(target: EventTarget | null) {
  return (
    target instanceof HTMLElement &&
    (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName))
  )
}

export function InspectionPanel({
  data,
  config,
  grade,
  onGradeChange,
  notes,
  onNotesChange,
  submitting,
  shortcutsEnabled,
  onAccept,
  onPartialAccept,
  onReject,
}: Props) {
  const panelRef = useRef<HTMLElement>(null)

  // Move focus here when a return loads so the keyboard shortcuts work right away.
  useEffect(() => {
    panelRef.current?.focus()
  }, [data.id])

  const selected = config.grades.find((g) => g.grade === grade)
  // Preview only: the server calculates the amount it actually pays.
  const refund = selected ? refundForGrade(data.item.pricePaise, selected.grade) : null

  const actions: Record<Decision, () => void> = {
    accept: onAccept,
    partial_accept: onPartialAccept,
    reject: onReject,
  }

  // Keyboard: 1/2/3 pick a grade; A accepts, P and R open their popups.
  useEffect(() => {
    if (!shortcutsEnabled) return
    function onKey(e: KeyboardEvent) {
      if (e.metaKey || e.ctrlKey || e.altKey || isTyping(e.target)) return
      const g = config.grades[Number(e.key) - 1]
      if (g) return onGradeChange(g.grade)
      const d = (Object.keys(DECISION_SHORTCUTS) as Decision[]).find(
        (k) => DECISION_SHORTCUTS[k].toLowerCase() === e.key.toLowerCase(),
      )
      if (d) {
        e.preventDefault() // don't type the letter into a popup that opens
        actions[d]()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  const buttons: { decision: Decision; label: string; variant: ButtonProps['variant'] }[] = [
    { decision: 'accept', label: 'Accept', variant: 'default' },
    { decision: 'partial_accept', label: 'Partially accept', variant: 'outline' },
    { decision: 'reject', label: 'Reject', variant: 'destructive' },
  ]

  return (
    <Panel ref={panelRef} tabIndex={-1} aria-label="Inspection" className="outline-none">
      <h2 id="grade-label" className="text-[15px] font-medium">
        Condition grade
      </h2>
      <div className="mt-3">
        <GradeSelector grades={config.grades} value={grade} onChange={onGradeChange} />
      </div>

      {selected && refund !== null && (
        <div
          data-testid="refund-preview"
          className="mt-4 flex items-center justify-between gap-4 rounded-md bg-warning px-4 py-3 text-warning-foreground"
        >
          <span className="text-sm">
            Grade {selected.grade} refund ({selected.refundPercent}% of{' '}
            {formatPaise(data.item.pricePaise)})
          </span>
          <span className="text-lg font-semibold">{formatPaise(refund)}</span>
        </div>
      )}

      <TextField
        containerClassName="mt-5"
        label="Inspection notes"
        placeholder="Seam torn at left sleeve, 3 cm"
        value={notes}
        onChange={(e) => onNotesChange(e.target.value)}
      />

      <div className="mt-5 flex flex-wrap gap-3">
        {buttons.map(({ decision, label, variant }) => (
          <Button
            key={decision}
            variant={variant}
            onClick={actions[decision]}
            disabled={submitting !== null || (decision !== 'reject' && !grade)}
            aria-keyshortcuts={DECISION_SHORTCUTS[decision]}
            aria-haspopup={decision === 'accept' ? undefined : 'dialog'}
          >
            {submitting === decision && decision === 'accept' ? 'Saving…' : label}
            <span aria-hidden className="opacity-80">
              ({DECISION_SHORTCUTS[decision]})
            </span>
          </Button>
        ))}
      </div>

      <p className="mt-4 text-[13px] text-muted-foreground">
        {refund !== null &&
          `${routingHint(refund, config.financeApprovalAbovePaise, data.customer.flaggedForReview)} `}
        Every decision is written to the audit log.
      </p>
    </Panel>
  )
}
