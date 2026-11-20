import type { Grade, GradeOption } from '@/api/types'
import { cn } from '@/lib/utils'

type Props = {
  grades: GradeOption[]
  value: Grade | null
  onChange: (grade: Grade) => void
}

export function GradeSelector({ grades, value, onChange }: Props) {
  return (
    <div role="radiogroup" aria-labelledby="grade-label" className="grid gap-2 sm:grid-cols-3">
      {grades.map((g, i) => {
        const selected = g.grade === value
        return (
          <button
            key={g.grade}
            type="button"
            role="radio"
            aria-checked={selected}
            onClick={() => onChange(g.grade)}
            className={cn(
              'cursor-pointer rounded-md border px-3.5 py-2.5 text-left transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50',
              selected
                ? 'border-primary bg-primary text-primary-foreground'
                : 'bg-background hover:bg-muted',
            )}
          >
            <span className="block text-[15px] font-medium">
              {g.grade}
              <span className="mx-1.5">·</span>key {i + 1}
            </span>
            <span
              className={cn(
                'block text-[13px]',
                selected ? 'text-primary-foreground/80' : 'text-muted-foreground',
              )}
            >
              {g.label}
              {selected && ` · ${g.refundPercent}% refund`}
            </span>
          </button>
        )
      })}
    </div>
  )
}
