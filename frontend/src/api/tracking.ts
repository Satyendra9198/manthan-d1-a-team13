import { http, USE_MOCKS } from './client'
import { trackingMock } from './mocks/inspection.mock'
import type { ReturnTracking } from './types'

const live = {
  getTracking: (rmaNumber: string) =>
    http.get<ReturnTracking>(`/returns/${encodeURIComponent(rmaNumber)}/tracking`),
}

export type TrackingApi = typeof live

export const trackingApi: TrackingApi = USE_MOCKS ? trackingMock : live
