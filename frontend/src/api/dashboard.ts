import { http, USE_MOCKS } from './client'
import { dashboardMock } from './mocks/dashboard.mock'
import type { DashboardPeriodDays, DashboardResponse } from './types'

const live = {
  getDashboard: (days: DashboardPeriodDays) => http.get<DashboardResponse>(`/dashboard?days=${days}`),
}

export type DashboardApi = typeof live

export const dashboardApi: DashboardApi = USE_MOCKS ? dashboardMock : live
