import { Outlet } from 'react-router'
import { supportMailto } from '@/config/support'

/** Shell for customer screens: phone-width card with brand + "Need help?". */
export function CustomerLayout() {
  return (
    <div className="min-h-svh bg-neutral-200/70 sm:py-8 dark:bg-neutral-950">
      <div className="mx-auto flex min-h-svh max-w-md flex-col overflow-hidden bg-muted/40 sm:min-h-0 sm:rounded-2xl sm:border sm:shadow-sm">
        <header className="flex items-center justify-between border-b bg-background px-4 py-3">
          <span className="text-[15px] font-semibold tracking-tight">ReturnRight</span>
          <a href={supportMailto()} className="text-sm text-muted-foreground hover:text-foreground">
            Need help?
          </a>
        </header>
        <main className="flex-1 px-4 py-5">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
