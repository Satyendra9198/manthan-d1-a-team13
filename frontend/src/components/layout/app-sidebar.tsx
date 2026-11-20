import { NavLink, useLocation } from 'react-router'
import { NAV_ITEMS, personaForPath } from '@/config/navigation'
import { cn } from '@/lib/utils'

type SidebarContentProps = {
  /** Shown in the "Signed in as" footer. */
  userLabel?: string
  /** Called after a link is clicked (used to close the mobile menu). */
  onNavigate?: () => void
}

/** Brand, nav links and signed-in footer. Shared by the desktop sidebar and the mobile menu. */
export function SidebarContent({ userLabel, onNavigate }: SidebarContentProps) {
  const { pathname } = useLocation()
  const signedInAs = userLabel ?? personaForPath(pathname)
  return (
    <div className="flex h-full flex-col px-4 py-6">
      <div className="px-1 text-[17px] font-semibold tracking-tight">ReturnRight</div>

      <nav aria-label="Main" className="mt-6">
        <ul className="space-y-1">
          {NAV_ITEMS.map((item) => (
            <li key={item.path}>
              <NavLink
                to={item.path}
                onClick={onNavigate}
                className={({ isActive }) =>
                  cn(
                    'flex h-9 items-center rounded-md px-3 text-[15px] transition-colors',
                    isActive
                      ? 'bg-muted text-foreground'
                      : 'text-muted-foreground hover:bg-muted/60 hover:text-foreground',
                  )
                }
              >
                {item.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>

      <div className="mt-auto px-1">
        <p className="text-xs text-muted-foreground">Signed in as</p>
        <p className="mt-1 text-sm font-medium">{signedInAs}</p>
      </div>
    </div>
  )
}

export function AppSidebar() {
  return (
    <aside className="hidden w-[246px] shrink-0 border-r bg-background md:block">
      <SidebarContent />
    </aside>
  )
}
