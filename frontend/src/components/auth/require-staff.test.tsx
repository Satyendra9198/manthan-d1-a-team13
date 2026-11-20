import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import { clearSession, getSession, setSession, type StaffRole } from '@/auth/session'
import { AppLayout } from '@/components/layout/app-layout'
import { RequireStaff } from './require-staff'

function signInAs(role: StaffRole, fullName = 'Riya Kapoor') {
  setSession({
    token: 'token',
    expiresAt: new Date(Date.now() + 60_000).toISOString(),
    staff: { id: '1', email: 'staff@returnright.example', fullName, role },
  })
}

/** Guarded staff shell with a placeholder page per path, guard forced on. */
function renderGuarded(path: string) {
  const page = (name: string) => <h1>{name}</h1>
  const router = createMemoryRouter(
    [
      { path: '/login', element: page('Login') },
      {
        path: '/',
        element: (
          <RequireStaff required>
            <AppLayout />
          </RequireStaff>
        ),
        children: [
          { path: 'scan', element: page('Scan') },
          { path: 'finance', element: page('Finance') },
          { path: 'dashboard', element: page('Dashboard') },
        ],
      },
    ],
    { initialEntries: [path] },
  )
  render(<RouterProvider router={router} />)
  return router
}

afterEach(() => clearSession())

describe('RequireStaff', () => {
  it('sends a signed-out visitor to sign in and remembers where they were going', () => {
    const router = renderGuarded('/finance')

    expect(router.state.location.pathname).toBe('/login')
    expect(router.state.location.search).toBe('?next=%2Ffinance')
  })

  it('opens the screen for the signed-in role', () => {
    signInAs('FINANCE_REVIEWER')
    renderGuarded('/finance')

    expect(screen.getByRole('heading', { name: 'Finance' })).toBeInTheDocument()
  })

  it('sends a role away from another role screen to its own', () => {
    signInAs('WAREHOUSE_AGENT')
    const router = renderGuarded('/finance')

    expect(router.state.location.pathname).toBe('/scan')
  })

  it('shows only the role screens with the name and a sign-out button', () => {
    signInAs('OPERATIONS_MANAGER', 'Manoj Desai')
    renderGuarded('/dashboard')

    expect(screen.getAllByRole('link', { name: 'Dashboard' })[0]).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Scan & inspect' })).not.toBeInTheDocument()
    expect(screen.getAllByText('Manoj Desai')[0]).toBeInTheDocument()
    expect(screen.getAllByText('Operations manager')[0]).toBeInTheDocument()
  })

  it('signing out ends the session and returns to sign in', async () => {
    signInAs('WAREHOUSE_AGENT')
    const router = renderGuarded('/scan')

    await userEvent.setup().click(screen.getAllByRole('button', { name: 'Sign out' })[0])

    expect(getSession()).toBeNull()
    expect(router.state.location.pathname).toBe('/login')
  })

  it('an expired session counts as signed out', () => {
    setSession({
      token: 'token',
      expiresAt: new Date(Date.now() - 1_000).toISOString(),
      staff: { id: '1', email: 'x@returnright.example', fullName: 'X', role: 'WAREHOUSE_AGENT' },
    })
    const router = renderGuarded('/scan')

    expect(router.state.location.pathname).toBe('/login')
  })
})
