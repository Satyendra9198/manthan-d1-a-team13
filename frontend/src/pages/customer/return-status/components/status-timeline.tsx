import { cn } from '@/lib/utils'
import type { StepState, TimelineStep } from '../tracking'
import { Card } from './card'

const DOT: Record<StepState, string> = {
  done: 'bg-success-foreground',
  current: 'bg-info-foreground ring-4 ring-info',
  upcoming: 'border border-muted-foreground/40 bg-background',
  failed: 'bg-destructive',
}

const STATE_TEXT: Record<StepState, string> = {
  done: 'Done',
  current: 'Current step',
  upcoming: 'Not yet',
  failed: 'Stopped',
}

export function StatusTimeline({ steps }: { steps: TimelineStep[] }) {
  return (
    <Card aria-label="Return progress">
      <ol>
        {steps.map((step, i) => {
          const next = steps[i + 1]
          const lineDone = step.state === 'done' && next && next.state !== 'upcoming'
          return (
            <li
              key={step.key}
              data-testid="timeline-step"
              data-state={step.state}
              aria-current={step.state === 'current' ? 'step' : undefined}
              className="relative pb-4 pl-6 last:pb-0"
            >
              {next && (
                <span
                  aria-hidden
                  className={cn(
                    'absolute top-3.5 bottom-0 left-[4.5px] w-px',
                    lineDone ? 'bg-success-foreground' : 'bg-border',
                  )}
                />
              )}
              <span
                aria-hidden
                className={cn('absolute top-1.5 left-0 size-2.5 rounded-full', DOT[step.state])}
              />
              <p
                className={cn(
                  'text-sm font-medium',
                  step.state === 'upcoming' && 'font-normal text-muted-foreground',
                  step.state === 'failed' && 'text-destructive',
                )}
              >
                {step.label}
                <span className="sr-only"> ({STATE_TEXT[step.state]})</span>
              </p>
              <p className="mt-0.5 text-xs text-muted-foreground">{step.caption}</p>
            </li>
          )
        })}
      </ol>
    </Card>
  )
}
