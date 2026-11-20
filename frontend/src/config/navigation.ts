/** Single source of truth for the sidebar and the routes. Add a screen here + in router.tsx. */
export type NavItem = {
  label: string
  path: string
  /** Who uses this screen. Shown as "Signed in as" until real login exists. */
  persona: string
}

export const NAV_ITEMS: NavItem[] = [
  { label: 'Scan & inspect', path: '/scan', persona: 'Warehouse agent' },
  { label: 'Finance queue', path: '/finance', persona: 'Finance reviewer' },
  { label: 'Refunds & ledger', path: '/refunds', persona: 'Finance reviewer' },
  { label: 'Dashboard', path: '/dashboard', persona: 'Operations manager' },
  { label: 'Audit log', path: '/audit', persona: 'Operations manager' },
]

/** Mock "signed in" persona for the current screen. */
export function personaForPath(pathname: string): string {
  return NAV_ITEMS.find((i) => pathname.startsWith(i.path))?.persona ?? NAV_ITEMS[0].persona
}

export const DEFAULT_PATH = NAV_ITEMS[0].path
