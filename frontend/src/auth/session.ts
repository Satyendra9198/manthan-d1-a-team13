import { useSyncExternalStore } from "react";
import type { AuthResponse, Principal, Role } from "../api/types";

/** Session lives in sessionStorage (docs §11). All storage access is guarded: it can throw. */
const KEY = "rr.session";

export interface Session {
  token: string;
  principal: Principal;
  expiresAt: number; // epoch ms
}

type Listener = () => void;
const listeners = new Set<Listener>();

function read(): Session | null {
  try {
    const raw = window.sessionStorage.getItem(KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Session;
    if (typeof parsed.token !== "string" || typeof parsed.expiresAt !== "number") return null;
    if (parsed.expiresAt <= Date.now()) {
      window.sessionStorage.removeItem(KEY);
      return null;
    }
    return parsed;
  } catch {
    return null;
  }
}

let current: Session | null = read();

function emit(): void {
  for (const l of listeners) l();
}

export function getSession(): Session | null {
  if (current && current.expiresAt <= Date.now()) {
    clearSession();
  }
  return current;
}

export function setSession(auth: AuthResponse): Session {
  const session: Session = {
    token: auth.access_token,
    principal: auth.principal,
    expiresAt: Date.now() + auth.expires_in * 1000,
  };
  current = session;
  try {
    window.sessionStorage.setItem(KEY, JSON.stringify(session));
  } catch {
    /* storage unavailable: keep the in-memory session only */
  }
  emit();
  return session;
}

export function clearSession(): void {
  current = null;
  try {
    window.sessionStorage.removeItem(KEY);
  } catch {
    /* ignore */
  }
  emit();
}

function subscribe(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function useSession(): Session | null {
  return useSyncExternalStore(subscribe, () => current, () => null);
}

export const STAFF_ROLES: Role[] = ["WAREHOUSE_AGENT", "FINANCE_REVIEWER", "OPS_MANAGER", "ADMIN"];

export function hasRole(principal: Principal | null | undefined, roles: readonly Role[]): boolean {
  if (!principal) return false;
  if (roles.includes(principal.role)) return true;
  // ADMIN may access every staff area (never the customer portal, which is scoped to a customer).
  return principal.role === "ADMIN" && roles.some((r) => r !== "CUSTOMER");
}

export function homeFor(role: Role): string {
  switch (role) {
    case "CUSTOMER":
      return "/portal";
    case "WAREHOUSE_AGENT":
      return "/warehouse";
    case "FINANCE_REVIEWER":
      return "/finance";
    case "OPS_MANAGER":
    case "ADMIN":
      return "/manager";
  }
}

export function loginPathFor(roles: readonly Role[] | null): string {
  if (!roles || roles.includes("CUSTOMER")) return "/";
  return "/login";
}
