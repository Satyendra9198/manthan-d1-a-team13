import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { AUTH_REQUIRED } from '@/auth/config'
import { useSession } from '@/auth/session'
import { canOpen, homePathFor } from '@/config/navigation'

type RequireStaffProps = {
  children: ReactNode
  /** Overrides AUTH_REQUIRED (tests). */
  required?: boolean
}

/**
 * Guards the staff screens: signed-out users go to /login (and come back afterwards), and a signed-in
 * user who opens another role's screen is sent to their own first screen.
 */
export function RequireStaff({ children, required = AUTH_REQUIRED }: RequireStaffProps) {
  const session = useSession()
  const { pathname, search } = useLocation()

  if (!required) return children
  if (!session) {
    return <Navigate to={`/login?next=${encodeURIComponent(pathname + search)}`} replace />
  }
  if (!canOpen(session.staff.role, pathname)) {
    return <Navigate to={homePathFor(session.staff.role)} replace />
  }
  return children
}
