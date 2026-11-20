/** Thin fetch wrapper shared by all API modules. */
import { clearSession, getSession } from '@/auth/session'

/** Backend base URL. The Vite dev server proxies /api to http://localhost:8080. */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

/** Mocks are on unless VITE_USE_MOCKS is explicitly "false". */
export const USE_MOCKS = import.meta.env.VITE_USE_MOCKS !== 'false'

export class ApiError extends Error {
  readonly status: number
  readonly code?: string

  constructor(status: number, message: string, code?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

/** Error body the backend returns on non-2xx responses. */
export type ApiErrorBody = { message: string; code?: string; details?: string[] }

/** Backend details look like "<item id>: Choose a condition grade." — the id means nothing to users. */
const withoutItemId = (detail: string) => detail.replace(/^[0-9a-f-]{36}: /i, '')

/** The most useful message for the user: the specific problems when the backend lists them. */
export function errorMessageFrom(body: Partial<ApiErrorBody>, fallback: string): string {
  if (body.details?.length) return body.details.map(withoutItemId).join(' ')
  return body.message ?? fallback
}

async function send(path: string, init: RequestInit): Promise<Response> {
  const session = getSession()
  const res = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(typeof init.body === 'string' ? { 'Content-Type': 'application/json' } : {}),
      ...(session ? { Authorization: `Bearer ${session.token}` } : {}),
      ...init.headers,
    },
  })

  if (!res.ok) {
    let body: Partial<ApiErrorBody> = {}
    try {
      body = await res.json()
    } catch {
      // non-JSON error body
    }
    // An expired or revoked token: sign out so the login screen shows.
    if (res.status === 401 && session) clearSession()
    throw new ApiError(res.status, errorMessageFrom(body, res.statusText), body.code)
  }
  return res
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await send(path, init)
  return res.status === 204 ? (undefined as T) : ((await res.json()) as T)
}

export const http = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown, headers?: HeadersInit) =>
    request<T>(path, { method: 'POST', body: JSON.stringify(body), headers }),
  /** Binary download (e.g. a customer photo) with the staff token attached. */
  blob: async (path: string) => (await send(path, {})).blob(),
}
