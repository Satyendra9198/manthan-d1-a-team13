import type { StaffRole, StaffSession } from '@/auth/session'
import { ApiError, http, USE_MOCKS } from './client'
import type { BackendLoginResponse } from './live/backend-types'

export type LoginRequest = { email: string; password: string }

const toSession = (res: BackendLoginResponse): StaffSession => ({
  token: res.accessToken,
  expiresAt: res.expiresAt,
  staff: res.staff,
})

const live = {
  async login(body: LoginRequest): Promise<StaffSession> {
    return toSession(await http.post<BackendLoginResponse>('/auth/login', body))
  },
}

/** The same demo accounts as the backend's dev seed (password Password@123). */
const DEMO_STAFF: { email: string; fullName: string; role: StaffRole }[] = [
  { email: 'riya.warehouse@returnright.example', fullName: 'Riya Kapoor', role: 'WAREHOUSE_AGENT' },
  { email: 'arun.warehouse@returnright.example', fullName: 'Arun Pillai', role: 'WAREHOUSE_AGENT' },
  { email: 'kavya.finance@returnright.example', fullName: 'Kavya Reddy', role: 'FINANCE_REVIEWER' },
  { email: 'manoj.ops@returnright.example', fullName: 'Manoj Desai', role: 'OPERATIONS_MANAGER' },
]

const mock = {
  async login({ email, password }: LoginRequest): Promise<StaffSession> {
    const staff = DEMO_STAFF.find((s) => s.email === email.trim().toLowerCase())
    if (!staff || password !== 'Password@123') {
      throw new ApiError(401, 'Email or password is incorrect.', 'INVALID_CREDENTIALS')
    }
    return {
      token: `mock-token-${staff.role}`,
      expiresAt: new Date(Date.now() + 8 * 60 * 60 * 1000).toISOString(),
      staff: { id: staff.email, ...staff },
    }
  },
}

export type AuthApi = typeof live

export const authApi: AuthApi = USE_MOCKS ? mock : live
