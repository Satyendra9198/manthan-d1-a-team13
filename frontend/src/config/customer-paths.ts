/** Customer-facing routes (registered in router.tsx). Staff screens live in navigation.ts. */
export const START_RETURN_PATH = '/returns/new'
export const TRACK_RETURN_PATH = '/returns/track'

/** Status page for one return, e.g. /returns/RMA-2026-00731. */
export const returnStatusPath = (rmaNumber: string) => `/returns/${encodeURIComponent(rmaNumber)}`
