import { useSyncExternalStore } from 'react'

export type StaffRole = 'WAREHOUSE_AGENT' | 'FINANCE_REVIEWER' | 'OPERATIONS_MANAGER'

export type StaffProfile = {
  id: string
  email: string
  fullName: string
  role: StaffRole
}

/** A signed-in staff member and their bearer token. */
export type StaffSession = {
  token: string
  /** ISO timestamp; the session is dropped after this. */
  expiresAt: string
  staff: StaffProfile
}

const STORAGE_KEY = 'rr-staff-session'

const listeners = new Set<() => void>()

/**
 * Kept in sessionStorage (not localStorage), so closing the tab signs the staff member out and the
 * token never outlives the browser session.
 */
function read(): StaffSession | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as StaffSession) : null
  } catch {
    return null
  }
}

let current: StaffSession | null = read()

function notify() {
  listeners.forEach((listener) => listener())
}

/** The current session, or null when signed out or expired. */
export function getSession(): StaffSession | null {
  if (current && Date.parse(current.expiresAt) <= Date.now()) {
    clearSession()
  }
  return current
}

export function setSession(session: StaffSession) {
  current = session
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  } catch {
    // Storage unavailable (private mode): the session lives in memory for this page only.
  }
  notify()
}

export function clearSession() {
  if (!current) return
  current = null
  try {
    sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // ignore
  }
  notify()
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** Re-renders when the staff member signs in or out. */
export function useSession(): StaffSession | null {
  return useSyncExternalStore(subscribe, getSession, getSession)
}
