import { http, USE_MOCKS } from './client'
import { customersMock } from './mocks/dashboard.mock'
import type { CustomerProfile, FlagReviewRequest } from './types'

const live = {
  getProfile: (customerId: string) =>
    http.get<CustomerProfile>(`/customers/${encodeURIComponent(customerId)}/returns-profile`),

  reviewFlag: (customerId: string, body: FlagReviewRequest) =>
    http.post<CustomerProfile>(`/customers/${encodeURIComponent(customerId)}/flag-review`, body),
}

export type CustomersApi = typeof live

export const customersApi: CustomersApi = USE_MOCKS ? customersMock : live
