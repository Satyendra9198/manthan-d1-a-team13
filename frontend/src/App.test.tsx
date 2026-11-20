import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { NAV_ITEMS } from '@/config/navigation'
import { routes } from '@/router'

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  render(<RouterProvider router={router} />)
  return router
}

describe('App layout & navigation', () => {
  it('redirects / to Scan & inspect and marks it active', async () => {
    const router = renderAt('/')
    expect(await screen.findByRole('heading', { name: 'Scan and inspect' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/scan')
    expect(screen.getByRole('link', { name: 'Scan & inspect' })).toHaveAttribute('aria-current', 'page')
    expect(await screen.findByText(/Today: \d+ inspected/)).toBeInTheDocument()
  })

  it('renders every sidebar item as a link', () => {
    renderAt('/scan')
    for (const item of NAV_ITEMS) {
      expect(screen.getByRole('link', { name: item.label })).toHaveAttribute('href', item.path)
    }
    expect(screen.getByText('Warehouse agent')).toBeInTheDocument()
  })

  it.each([
    ['Finance queue', '/finance'],
    ['Refunds & ledger', '/refunds'],
    ['Dashboard', '/dashboard'],
    ['Audit log', '/audit'],
  ])('clicking %s opens %s', async (label, path) => {
    const user = userEvent.setup()
    const router = renderAt('/scan')
    await user.click(screen.getByRole('link', { name: label }))
    expect(router.state.location.pathname).toBe(path)
    expect(screen.getByRole('heading', { level: 1, name: label })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: label })).toHaveAttribute('aria-current', 'page')
  })

  it('redirects unknown URLs to the default screen', () => {
    const router = renderAt('/nope')
    expect(router.state.location.pathname).toBe('/scan')
  })
})
