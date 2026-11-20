import { USE_MOCKS } from '@/api/client'

/**
 * Staff screens need a real sign-in when the app talks to the backend. With mock data the app keeps
 * its offline demo behaviour: every screen is open and the sidebar shows the persona.
 */
export const AUTH_REQUIRED = !USE_MOCKS
