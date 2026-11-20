import { useState } from 'react'
import { Menu } from 'lucide-react'
import { Outlet } from 'react-router'
import { AppSidebar, SidebarContent } from '@/components/layout/app-sidebar'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetTitle, SheetTrigger } from '@/components/ui/sheet'

/** Shell shared by every screen: sidebar on the left, the current page on the right. */
export function AppLayout() {
  const [menuOpen, setMenuOpen] = useState(false)

  return (
    <div className="min-h-svh bg-neutral-200/70 md:p-3 dark:bg-neutral-950">
      <div className="flex min-h-svh flex-col overflow-hidden bg-background md:min-h-[calc(100svh-1.5rem)] md:flex-row md:rounded-2xl">
        {/* Mobile: top bar with a slide-out menu */}
        <div className="flex items-center justify-between border-b px-4 py-3 md:hidden">
          <span className="text-[17px] font-semibold tracking-tight">ReturnRight</span>
          <Sheet open={menuOpen} onOpenChange={setMenuOpen}>
            <SheetTrigger asChild>
              <Button variant="ghost" size="icon-sm" aria-label="Open menu">
                <Menu />
              </Button>
            </SheetTrigger>
            <SheetContent side="left" className="w-[260px] p-0">
              <SheetTitle className="sr-only">Navigation</SheetTitle>
              <SidebarContent onNavigate={() => setMenuOpen(false)} />
            </SheetContent>
          </Sheet>
        </div>

        <AppSidebar />

        <main className="min-w-0 flex-1 bg-muted/40 px-4 py-6 md:px-9 md:py-10">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
