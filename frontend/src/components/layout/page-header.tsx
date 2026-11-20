import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

type PageHeaderProps = {
  title: string
  description?: ReactNode
  /** Right-aligned slot: status badge, buttons, etc. */
  actions?: ReactNode
  className?: string
}

/**
 * Title block at the top of every screen.
 *
 *   <PageHeader
 *     title="Scan and inspect"
 *     description="Scan the parcel QR or type the code, then grade and decide."
 *     actions={<Badge>Today: 37 inspected</Badge>}
 *   />
 */
export function PageHeader({ title, description, actions, className }: PageHeaderProps) {
  return (
    <header className={cn('flex flex-wrap items-start justify-between gap-4', className)}>
      <div className="min-w-0">
        <h1 className="text-[26px] leading-tight font-medium tracking-tight">{title}</h1>
        {description && <p className="mt-1 text-[15px] text-muted-foreground">{description}</p>}
      </div>
      {actions && <div className="flex shrink-0 items-center gap-2 pt-1">{actions}</div>}
    </header>
  )
}
