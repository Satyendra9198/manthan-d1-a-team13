/** Thin fetch wrapper shared by all API modules. */

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

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

/** Error body the backend is expected to return on non-2xx responses. */
export type ApiErrorBody = { message: string; code?: string }

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init.body ? { 'Content-Type': 'application/json' } : {}),
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
    throw new ApiError(res.status, body.message ?? res.statusText, body.code)
  }

  return res.status === 204 ? (undefined as T) : ((await res.json()) as T)
}

export const http = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown, headers?: HeadersInit) =>
    request<T>(path, { method: 'POST', body: JSON.stringify(body), headers }),
}
