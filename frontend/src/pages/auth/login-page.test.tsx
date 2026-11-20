import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import { clearSession, getSession, setSession } from '@/auth/session'
import { routes } from '@/router'

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  render(<RouterProvider router={router} />)
  return router
}

async function signIn(email: string, password: string) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Work email'), email)
  await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByRole('button', { name: 'Sign in' }))
}

afterEach(() => clearSession())

describe('Staff sign in', () => {
  it('shows the Figma sign-in form', () => {
    renderAt('/login')
    expect(screen.getByRole('heading', { name: 'Staff sign in' })).toBeInTheDocument()
    expect(screen.getByText('For warehouse, finance and operations staff.')).toBeInTheDocument()
  })

  it('a wrong password shows the error and stays signed out', async () => {
    renderAt('/login')
    await signIn('riya.warehouse@returnright.example', 'nope')

    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is incorrect.')
    expect(getSession()).toBeNull()
  })

  it('empty fields are refused before calling the server', async () => {
    renderAt('/login')
    await userEvent.setup().click(screen.getByRole('button', { name: 'Sign in' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Enter your work email and password.')
  })

  it('a finance reviewer lands on the finance queue', async () => {
    const router = renderAt('/login')
    await signIn('kavya.finance@returnright.example', 'Password@123')

    expect(await screen.findByRole('heading', { level: 1, name: 'Refunds awaiting approval' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/finance')
    expect(getSession()?.staff.role).toBe('FINANCE_REVIEWER')
  })

  it('returns to the page the staff member asked for when their role may open it', async () => {
    const router = renderAt('/login?next=%2Frefunds')
    await signIn('kavya.finance@returnright.example', 'Password@123')

    await screen.findByRole('heading', { level: 1, name: 'Refunds and ledger' })
    expect(router.state.location.pathname).toBe('/refunds')
  })

  it('ignores a "next" page from another role or another site', async () => {
    const router = renderAt('/login?next=%2F%2Fevil.example')
    await signIn('riya.warehouse@returnright.example', 'Password@123')

    await screen.findByRole('heading', { name: 'Scan and inspect' })
    expect(router.state.location.pathname).toBe('/scan')
  })

  it('an already signed-in staff member skips the form', () => {
    setSession({
      token: 't',
      expiresAt: new Date(Date.now() + 60_000).toISOString(),
      staff: { id: '1', email: 'manoj.ops@returnright.example', fullName: 'Manoj Desai', role: 'OPERATIONS_MANAGER' },
    })
    const router = renderAt('/login')

    expect(router.state.location.pathname).toBe('/dashboard')
  })
})
