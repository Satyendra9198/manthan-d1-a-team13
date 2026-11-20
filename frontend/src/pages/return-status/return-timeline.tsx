import { cn } from "@/lib/utils"

export type TimelineStep = {
  key: string
  title: string
  description?: string
  /** done = green, current = blue, upcoming = hollow grey, failed = red. */
  state: "done" | "current" | "upcoming" | "failed"
}

const DOT_CLASSES: Record<TimelineStep["state"], string> = {
  done: "bg-success-foreground",
  current: "bg-info-foreground ring-4 ring-info",
  upcoming: "border-2 border-border bg-background",
  failed: "bg-destructive",
}

const TITLE_CLASSES: Record<TimelineStep["state"], string> = {
  done: "font-semibold text-foreground",
  current: "font-semibold text-foreground",
  upcoming: "font-medium text-muted-foreground",
  failed: "font-semibold text-destructive",
}

const SCREEN_READER_STATE: Record<TimelineStep["state"], string> = {
  done: ", done",
  current: ", current step",
  upcoming: ", not yet",
  failed: "",
}

/** Vertical list of a return's stages, with a dot per stage joined by a line. */
export function ReturnTimeline({ steps }: { steps: TimelineStep[] }) {
  return (
    <ol aria-label="Return progress" className="grid">
      {steps.map((step, index) => {
        const next = steps[index + 1]
        return (
          <li
            key={step.key}
            aria-current={step.state === "current" ? "step" : undefined}
            className="relative grid grid-cols-[14px_1fr] gap-x-3 pb-5 last:pb-0"
          >
            {next && (
              <span
                aria-hidden
                className={cn(
                  "absolute top-[21px] -bottom-[1px] left-1.5 w-0.5 rounded-full",
                  next.state === "upcoming" ? "bg-border" : "bg-success-foreground",
                )}
              />
            )}
            <span aria-hidden className={cn("mt-1 size-3.5 rounded-full", DOT_CLASSES[step.state])} />
            <div className="grid gap-0.5">
              <p className={cn("text-[15px]", TITLE_CLASSES[step.state])}>
                {step.title}
                <span className="sr-only">{SCREEN_READER_STATE[step.state]}</span>
              </p>
              {step.description && (
                <p className="text-[13px] text-muted-foreground">{step.description}</p>
              )}
            </div>
          </li>
        )
      })}
    </ol>
  )
}
