import { http, USE_MOCKS } from './client'
import { auditMock } from './mocks/inspection.mock'
import type { AuditLogEntry, AuditQuery } from './types'

const live = {
  getEntries: (q: AuditQuery = {}) => {
    const params = new URLSearchParams()
    for (const [k, v] of Object.entries(q)) if (v !== undefined && v !== '') params.set(k, String(v))
    const qs = params.toString()
    return http.get<AuditLogEntry[]>(`/audit${qs ? `?${qs}` : ''}`)
  },
}

export type AuditApi = typeof live

export const auditApi: AuditApi = USE_MOCKS ? auditMock : live
