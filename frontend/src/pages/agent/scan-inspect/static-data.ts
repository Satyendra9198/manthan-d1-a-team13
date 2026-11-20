import { getMockReturnDetails, MOCK_CONFIG } from '@/api/mocks/inspection.mock'
import type { ScanInspectInitial } from './use-scan-inspect'

/**
 * Static data the screen opens with, matching the design.
 * Delete this (and the `initial` default in scan-inspect-page.tsx) once the backend is live:
 * the screen will then start empty and load everything through the API.
 */
export const STATIC_SCAN_INSPECT: ScanInspectInitial = {
  config: MOCK_CONFIG,
  inspectedToday: 37,
  ret: getMockReturnDetails('rr-qr-7f3a9c41'),
  grade: 'C',
}
