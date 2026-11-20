import type { StaffRole } from '@/auth/session'

/** Single source of truth for the sidebar and the routes. Add a screen here + in router.tsx. */
export type NavItem = {
  label: string
  path: string
  /** Who uses this screen. Shown as "Signed in as" when no one is signed in (mock mode). */
  persona: string
  /** Roles that may open this screen once real sign-in is on. */
  roles: StaffRole[]
}

export const NAV_ITEMS: NavItem[] = [
  { label: 'Scan & inspect', path: '/scan', persona: 'Warehouse agent', roles: ['WAREHOUSE_AGENT'] },
  { label: 'Finance queue', path: '/finance', persona: 'Finance reviewer', roles: ['FINANCE_REVIEWER'] },
  { label: 'Refunds & ledger', path: '/refunds', persona: 'Finance reviewer', roles: ['FINANCE_REVIEWER'] },
  { label: 'Dashboard', path: '/dashboard', persona: 'Operations manager', roles: ['OPERATIONS_MANAGER'] },
  { label: 'Audit log', path: '/audit', persona: 'Operations manager', roles: ['OPERATIONS_MANAGER'] },
]

export const ROLE_LABELS: Record<StaffRole, string> = {
  WAREHOUSE_AGENT: 'Warehouse agent',
  FINANCE_REVIEWER: 'Finance reviewer',
  OPERATIONS_MANAGER: 'Operations manager',
}

/** Mock "signed in" persona for the current screen. */
export function personaForPath(pathname: string): string {
  return NAV_ITEMS.find((i) => pathname.startsWith(i.path))?.persona ?? NAV_ITEMS[0].persona
}

export const DEFAULT_PATH = NAV_ITEMS[0].path

/** Screens the role may open, in sidebar order. */
export function navItemsFor(role: StaffRole): NavItem[] {
  return NAV_ITEMS.filter((item) => item.roles.includes(role))
}

/** Where a role lands after signing in, or when it opens a screen it may not use. */
export function homePathFor(role: StaffRole): string {
  return navItemsFor(role)[0]?.path ?? DEFAULT_PATH
}

export function canOpen(role: StaffRole, pathname: string): boolean {
  return navItemsFor(role).some((item) => pathname.startsWith(item.path))
}
