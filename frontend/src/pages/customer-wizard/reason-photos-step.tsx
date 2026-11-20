import { useEffect, useId, useRef, useState, type ReactNode } from "react"
import type { OrderItem } from "@/api/orders"
import {
  EMPTY_ITEM_DETAILS,
  isItemDetailsComplete,
  NOTES_MAX_LENGTH,
  RETURN_REASONS,
  type ItemReturnDetails,
  type ReturnReasonCode,
} from "@/api/returns"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group"
import { Textarea } from "@/components/ui/textarea"
import { formatPaise } from "@/lib/format"
import { cn } from "@/lib/utils"
import { PhotoPicker } from "./photo-picker"
import { WizardFooter } from "./wizard-footer"

type ReasonPhotosStepProps = {
  /** The items picked in step 2, in order. */
  items: OrderItem[]
  details: Record<string, ItemReturnDetails>
  onDetailsChange: (itemId: string, details: ItemReturnDetails) => void
  /** Which item is on screen. */
  activeIndex: number
  onActiveIndexChange: (index: number) => void
  /** Back from the first item. */
  onBack: () => void
  /** Every item has a reason and photos. */
  onContinue: () => void
}

/** Wizard step 3: a reason, optional notes and photos for each item, one item at a time. */
export function ReasonPhotosStep({
  items,
  details,
  onDetailsChange,
  activeIndex,
  onActiveIndexChange,
  onBack,
  onContinue,
}: ReasonPhotosStepProps) {
  // Items the customer tried to continue from; only these show "missing" errors.
  const [checkedIds, setCheckedIds] = useState<string[]>([])
  const headingRef = useRef<HTMLHeadingElement>(null)
  const reasonGroupRef = useRef<HTMLDivElement>(null)
  const photoInputRef = useRef<HTMLInputElement>(null)
  const shownIndex = useRef(activeIndex)
  const reasonTitleId = useId()
  const reasonErrorId = useId()
  const notesTitleId = useId()
  const notesCountId = useId()

  const item = items[activeIndex]
  const current = details[item.id] ?? EMPTY_ITEM_DETAILS
  const nextItem = items[activeIndex + 1]
  const showErrors = checkedIds.includes(item.id)
  const reasonError = showErrors && !current.reason ? "Choose a reason" : undefined
  const photosError = showErrors && current.photos.length === 0 ? "Add at least one photo" : undefined

  // Switching items: start the customer at the top of the new item.
  useEffect(() => {
    if (shownIndex.current === activeIndex) return
    shownIndex.current = activeIndex
    document.documentElement.scrollTop = 0
    headingRef.current?.focus({ preventScroll: true })
  }, [activeIndex])

  const update = (patch: Partial<ItemReturnDetails>) =>
    onDetailsChange(item.id, { ...current, ...patch })

  const markChecked = (id: string) =>
    setCheckedIds((ids) => (ids.includes(id) ? ids : [...ids, id]))

  function handleContinue() {
    markChecked(item.id)
    if (!current.reason) {
      reasonGroupRef.current?.querySelector<HTMLElement>('[role="radio"]')?.focus()
      return
    }
    if (current.photos.length === 0) {
      photoInputRef.current?.focus()
      return
    }
    if (nextItem) {
      onActiveIndexChange(activeIndex + 1)
      return
    }
    // Last item: the tabs let customers skip around, so check every item.
    const firstIncomplete = items.findIndex(
      (candidate) => !isItemDetailsComplete(details[candidate.id] ?? EMPTY_ITEM_DETAILS),
    )
    if (firstIncomplete === -1) {
      onContinue()
    } else {
      markChecked(items[firstIncomplete].id)
      onActiveIndexChange(firstIncomplete)
    }
  }

  return (
    <div className="grid gap-4">
      {items.length > 1 && (
        <div className="grid gap-2.5">
          <p className="text-[13px] text-muted-foreground">
            Item {activeIndex + 1} of {items.length} · each item needs its own reason and photos
          </p>
          <nav aria-label="Items to return" className="-mx-4 overflow-x-auto px-4">
            <ol className="flex gap-2">
              {items.map((candidate, index) => (
                <li key={candidate.id}>
                  <button
                    type="button"
                    aria-current={index === activeIndex ? "step" : undefined}
                    onClick={() => onActiveIndexChange(index)}
                    className={cn(
                      "h-8 cursor-pointer rounded-full border px-3.5 text-sm font-medium whitespace-nowrap transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
                      index === activeIndex
                        ? "border-primary bg-primary text-primary-foreground"
                        : "bg-background hover:bg-muted",
                    )}
                  >
                    {index + 1} · {candidate.name}
                  </button>
                </li>
              ))}
            </ol>
          </nav>
        </div>
      )}

      <div className="grid gap-1">
        <h1 ref={headingRef} tabIndex={-1} className="text-xl font-semibold tracking-tight outline-none">
          Why are you returning it?
        </h1>
        <p className="text-sm text-muted-foreground">
          {item.name} · {item.category} · {formatPaise(item.pricePaise)}
        </p>
      </div>

      {/* key: each item gets fresh field state (e.g. photo warnings). */}
      <div key={item.id} className="grid gap-4">
        <Section title="Reason" titleId={reasonTitleId}>
          <RadioGroup
            ref={reasonGroupRef}
            value={current.reason ?? ""}
            onValueChange={(value) => update({ reason: value as ReturnReasonCode })}
            aria-labelledby={reasonTitleId}
            aria-describedby={reasonError ? reasonErrorId : undefined}
            aria-invalid={reasonError ? true : undefined}
          >
            {RETURN_REASONS.map((reason) => (
              <label
                key={reason.code}
                className={cn(
                  "flex min-h-10 cursor-pointer items-center gap-3 rounded-md border bg-background px-3 py-2 text-[15px] transition-colors hover:border-foreground/40",
                  current.reason === reason.code && "border-primary bg-muted hover:border-primary",
                  reasonError && "border-destructive/60",
                )}
              >
                <RadioGroupItem value={reason.code} />
                {reason.label}
              </label>
            ))}
          </RadioGroup>
          {reasonError && (
            <p id={reasonErrorId} className="text-sm text-destructive">
              {reasonError}
            </p>
          )}
        </Section>

        <Section title="Tell us more" titleId={notesTitleId} meta="Optional">
          <div className="grid gap-2">
            <Textarea
              aria-labelledby={notesTitleId}
              aria-describedby={notesCountId}
              maxLength={NOTES_MAX_LENGTH}
              rows={3}
              value={current.notes}
              onChange={(event) => update({ notes: event.target.value })}
            />
            <p id={notesCountId} className="text-[13px] text-muted-foreground">
              {current.notes.length} / {NOTES_MAX_LENGTH}
            </p>
          </div>
        </Section>

        <Section title="Photos" meta="Required · up to 3">
          <PhotoPicker
            inputRef={photoInputRef}
            photos={current.photos}
            onPhotosChange={(photos) => update({ photos })}
            error={photosError}
          />
        </Section>
      </div>

      <WizardFooter>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm">{item.name}</p>
          {(nextItem || items.length > 1) && (
            <p className="truncate text-[13px] text-muted-foreground">
              {nextItem ? `Next: ${nextItem.name}` : "Last item"}
            </p>
          )}
        </div>
        <Button
          variant="outline"
          onClick={() => (activeIndex > 0 ? onActiveIndexChange(activeIndex - 1) : onBack())}
        >
          Back
        </Button>
        <Button onClick={handleContinue}>Continue</Button>
      </WizardFooter>
    </div>
  )
}

type SectionProps = {
  title: string
  titleId?: string
  /** Small grey text on the right, e.g. "Optional". */
  meta?: string
  children: ReactNode
}

function Section({ title, titleId, meta, children }: SectionProps) {
  return (
    <Card className="grid gap-3">
      <div className="flex items-baseline justify-between gap-3">
        <h2 id={titleId} className="text-[15px] font-medium">
          {title}
        </h2>
        {meta && <span className="text-[13px] text-muted-foreground">{meta}</span>}
      </div>
      {children}
    </Card>
  )
}
