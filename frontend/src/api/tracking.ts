import { http, USE_MOCKS } from './client'
import type { BackendTracking } from './live/backend-types'
import { toReturnTracking } from './live/mappers'
import { trackingMock } from './mocks/inspection.mock'
import type { ReturnTracking } from './types'

export type TrackingApi = {
  /** The backend only shows a return to someone who also knows the order email. */
  requiresEmail: boolean
  getTracking: (rmaNumber: string, email?: string) => Promise<ReturnTracking>
}

const live: TrackingApi = {
  requiresEmail: true,
  async getTracking(rmaNumber, email = '') {
    // POST so the email stays out of URLs, history and logs.
    const tracking = await http.post<BackendTracking>('/customer/returns/track', { rmaNumber, email })
    return toReturnTracking(tracking)
  },
}

const mock: TrackingApi = {
  requiresEmail: false,
  getTracking: (rmaNumber) => trackingMock.getTracking(rmaNumber),
}

export const trackingApi: TrackingApi = USE_MOCKS ? mock : live
