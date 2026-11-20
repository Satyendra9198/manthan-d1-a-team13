import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearSession, getSession, setSession } from '@/auth/session'
import { ApiError, errorMessageFrom, http } from './client'

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })

function signIn() {
  setSession({
    token: 'staff-token',
    expiresAt: new Date(Date.now() + 60_000).toISOString(),
    staff: { id: '1', email: 'riya@returnright.example', fullName: 'Riya', role: 'WAREHOUSE_AGENT' },
  })
}

afterEach(() => {
  vi.unstubAllGlobals()
  clearSession()
})

describe('errorMessageFrom', () => {
  it('prefers the listed problems and drops the item ids the user cannot use', () => {
    expect(
      errorMessageFrom(
        {
          message: 'The inspection result can not be saved.',
          details: ['3f2a1c3e-0000-0000-0000-000000000001: Choose a condition grade.'],
        },
        'Bad request',
      ),
    ).toBe('Choose a condition grade.')
  })

  it('falls back to the message, then the status text', () => {
    expect(errorMessageFrom({ message: 'No return with number RMA-1.' }, 'Not Found')).toBe('No return with number RMA-1.')
    expect(errorMessageFrom({}, 'Not Found')).toBe('Not Found')
  })
})

describe('http', () => {
  it('sends the staff token when signed in', async () => {
    signIn()
    const fetchMock = vi.fn().mockResolvedValue(json(200, { ok: true }))
    vi.stubGlobal('fetch', fetchMock)

    await http.get('/auth/me')

    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer staff-token')
  })

  it('turns an error body into an ApiError with its code', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(404, { code: 'RETURN_NOT_FOUND', message: 'Not found', details: [] })))

    await expect(http.get('/warehouse/returns/X')).rejects.toMatchObject({
      status: 404,
      code: 'RETURN_NOT_FOUND',
      message: 'Not found',
    })
  })

  it('an expired token (401) signs the staff member out', async () => {
    signIn()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(401, { code: 'UNAUTHORIZED', message: 'Sign in' })))

    await expect(http.get('/warehouse/returns/queue')).rejects.toBeInstanceOf(ApiError)
    expect(getSession()).toBeNull()
  })
})
