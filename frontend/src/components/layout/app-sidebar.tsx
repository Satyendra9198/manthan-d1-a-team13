import { NavLink, useLocation, useNavigate } from 'react-router'
import { clearSession, useSession } from '@/auth/session'
import { Button } from '@/components/ui/button'
import { NAV_ITEMS, navItemsFor, personaForPath, ROLE_LABELS } from '@/config/navigation'
import { cn } from '@/lib/utils'

type SidebarContentProps = {
  /** Shown in the "Signed in as" footer. */
  userLabel?: string
  /** Called after a link is clicked (used to close the mobile menu). */
  onNavigate?: () => void
}

/**
 * Brand, nav links and signed-in footer. Shared by the desktop sidebar and the mobile menu.
 * Signed in: only the role's screens, the staff member's name and a sign-out button.
 * Mock mode (no sign-in): every screen and the persona for the current page.
 */
export function SidebarContent({ userLabel, onNavigate }: SidebarContentProps) {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const session = useSession()
  const items = session ? navItemsFor(session.staff.role) : NAV_ITEMS
  const signedInAs = session?.staff.fullName ?? userLabel ?? personaForPath(pathname)

  function signOut() {
    clearSession()
    navigate('/login', { replace: true })
  }

  return (
    <div className="flex h-full flex-col px-4 py-6">
      <div className="px-1 text-[17px] font-semibold tracking-tight">ReturnRight</div>

      <nav aria-label="Main" className="mt-6">
        <ul className="space-y-1">
          {items.map((item) => (
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
        {session && (
          <>
            <p className="text-xs text-muted-foreground">{ROLE_LABELS[session.staff.role]}</p>
            <Button variant="outline" size="sm" className="mt-3 w-full" onClick={signOut}>
              Sign out
            </Button>
          </>
        )}
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
