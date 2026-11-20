import type { ComponentProps } from 'react'
import { cn } from '@/lib/utils'

/** White bordered card used for the two columns on this screen. */
export function Panel({ className, ...props }: ComponentProps<'section'>) {
  return <section className={cn('rounded-xl border bg-background p-5 sm:p-6', className)} {...props} />
}
