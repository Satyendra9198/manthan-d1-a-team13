/** Single source of truth for the sidebar and the routes. Add a screen here + in router.tsx. */
export type NavItem = {
  label: string
  path: string
}

export const NAV_ITEMS: NavItem[] = [
  { label: 'Scan & inspect', path: '/scan' },
  { label: 'Finance queue', path: '/finance' },
  { label: 'Refunds & ledger', path: '/refunds' },
  { label: 'Dashboard', path: '/dashboard' },
  { label: 'Audit log', path: '/audit' },
]

export const DEFAULT_PATH = NAV_ITEMS[0].path
