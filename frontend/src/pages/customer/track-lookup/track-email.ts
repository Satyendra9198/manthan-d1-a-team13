/**
 * The order email the customer typed for a return, kept for this browser tab only so refreshing the
 * status page doesn't ask again. Never put it in the URL.
 */
const key = (rmaNumber: string) => `rr-track-email:${rmaNumber.trim().toUpperCase()}`

export function rememberTrackEmail(rmaNumber: string, email: string) {
  try {
    sessionStorage.setItem(key(rmaNumber), email.trim())
  } catch {
    // Storage unavailable: the customer types it again after a refresh.
  }
}

export function recallTrackEmail(rmaNumber: string): string | null {
  try {
    return sessionStorage.getItem(key(rmaNumber))
  } catch {
    return null
  }
}

export function forgetTrackEmail(rmaNumber: string) {
  try {
    sessionStorage.removeItem(key(rmaNumber))
  } catch {
    // ignore
  }
}
