import type { ComponentProps, ReactNode } from 'react'
import { cn } from '@/lib/utils'

export function Panel({
  title,
  aside,
  className,
  children,
  ...props
}: ComponentProps<'section'> & { title?: string; aside?: ReactNode }) {
  return (
    <section aria-label={title} className={cn('rounded-xl border bg-background p-5', className)} {...props}>
      {title && (
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 className="text-[15px] font-medium">{title}</h2>
          {aside}
        </div>
      )}
      {children}
    </section>
  )
}

export function StatTile({ label, value, caption }: { label: string; value: string; caption: string }) {
  return (
    <div role="group" aria-label={label} className="rounded-xl border bg-background p-5">
      <h2 className="text-[13px] text-muted-foreground">{label}</h2>
      <p className="mt-1.5 text-[28px] leading-tight font-semibold tracking-tight" data-testid="stat-value">
        {value}
      </p>
      <p className="mt-1 text-xs text-muted-foreground">{caption}</p>
    </div>
  )
}

/** Collapsible accessible table version of a chart. */
export function TableView({ caption, head, rows }: { caption: string; head: string[]; rows: string[][] }) {
  return (
    <details className="mt-4 text-sm">
      <summary className="cursor-pointer text-xs text-muted-foreground hover:text-foreground">Show as table</summary>
      <table className="mt-2 w-full text-left">
        <caption className="sr-only">{caption}</caption>
        <thead>
          <tr className="border-b">
            {head.map((h) => (
              <th key={h} scope="col" className="py-1.5 pr-3 text-xs font-normal text-muted-foreground">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r[0]} className="border-b last:border-b-0">
              {r.map((c, i) => (
                <td key={i} className="py-1.5 pr-3">
                  {c}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </details>
  )
}
