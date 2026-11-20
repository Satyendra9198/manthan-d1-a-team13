import { clearSession, getSession } from "../auth/session";
import type { ErrorEnvelope } from "./types";

export const API_BASE = "/api/v1";

/** Parsed error envelope: `{"error": {code, message, details, correlation_id}}` (contract §Conventions). */
export class ApiError extends Error {
  readonly code: string;
  readonly details: Record<string, unknown>;
  readonly correlationId: string | null;
  readonly status: number;

  constructor(opts: {
    code: string;
    message: string;
    status: number;
    details?: Record<string, unknown> | null;
    correlationId?: string | null;
  }) {
    super(opts.message);
    this.name = "ApiError";
    this.code = opts.code;
    this.status = opts.status;
    this.details = opts.details ?? {};
    this.correlationId = opts.correlationId ?? null;
  }
}

export function isApiError(e: unknown): e is ApiError {
  return e instanceof ApiError;
}

export function hasCode(e: unknown, ...codes: string[]): e is ApiError {
  return isApiError(e) && codes.includes(e.code);
}

/** UUID v4 for Idempotency-Key. Falls back to getRandomValues on non-secure origins (e.g. LAN IP on a phone). */
export function newIdempotencyKey(): string {
  const c = globalThis.crypto;
  if (typeof c.randomUUID === "function") {
    try {
      return c.randomUUID();
    } catch {
      /* not a secure context */
    }
  }
  const b = new Uint8Array(16);
  c.getRandomValues(b);
  b[6] = ((b[6] ?? 0) & 0x0f) | 0x40;
  b[8] = ((b[8] ?? 0) & 0x3f) | 0x80;
  const h = Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("");
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

export interface RequestOptions {
  body?: unknown;
  /** Sends `Idempotency-Key` (contract 🔑). One key per confirm dialog / form submission. */
  idempotencyKey?: string;
  /** Sends `If-Match: "<version>"` (contract 🔒). */
  ifMatch?: number;
  signal?: AbortSignal;
  /** Do not treat 401 as "session expired" (used by the login endpoints). */
  skipAuthRedirect?: boolean;
}

export interface ApiResult<T> {
  data: T;
  status: number;
  correlationId: string | null;
}

let unauthorizedHandler: ((loginPath: string) => void) | null = null;

/** The router registers a handler so 401s navigate without a full reload. */
export function onUnauthorized(handler: (loginPath: string) => void): void {
  unauthorizedHandler = handler;
}

function isEnvelope(v: unknown): v is ErrorEnvelope {
  if (typeof v !== "object" || v === null || !("error" in v)) return false;
  const err = (v).error;
  return typeof err === "object" && err !== null && "code" in err;
}

export async function apiRequest<T>(method: string, path: string, opts: RequestOptions = {}): Promise<ApiResult<T>> {
  const headers: Record<string, string> = { Accept: "application/json" };
  const session = getSession();
  if (session) headers.Authorization = `Bearer ${session.token}`;
  if (opts.body !== undefined) headers["Content-Type"] = "application/json";
  if (opts.idempotencyKey) headers["Idempotency-Key"] = opts.idempotencyKey;
  if (opts.ifMatch !== undefined) headers["If-Match"] = `"${opts.ifMatch}"`;

  let res: Response;
  try {
    res = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
      signal: opts.signal,
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") throw e;
    throw new ApiError({
      code: "NETWORK_ERROR",
      message: "We couldn't reach ReturnRight. Check your connection and try again.",
      status: 0,
    });
  }

  const correlationId = res.headers.get("X-Correlation-ID");
  const text = await res.text();
  let payload: unknown = null;
  if (text) {
    try {
      payload = JSON.parse(text);
    } catch {
      payload = null;
    }
  }

  if (!res.ok) {
    const env = isEnvelope(payload) ? payload.error : null;
    const err = new ApiError({
      code: env?.code ?? (res.status >= 500 ? "INTERNAL_ERROR" : `HTTP_${res.status}`),
      message: env?.message ?? `Request failed (${res.status}).`,
      status: res.status,
      details: env?.details ?? null,
      correlationId: env?.correlation_id ?? correlationId,
    });
    if (res.status === 401 && session && !opts.skipAuthRedirect) {
      const loginPath = session.principal.type === "customer" ? "/" : "/login";
      clearSession();
      if (unauthorizedHandler) unauthorizedHandler(loginPath);
      else window.location.assign(loginPath);
    }
    throw err;
  }

  return { data: payload as T, status: res.status, correlationId };
}

export const api = {
  get: async <T>(path: string, signal?: AbortSignal): Promise<T> => (await apiRequest<T>("GET", path, { signal })).data,
  post: <T>(path: string, opts?: RequestOptions) => apiRequest<T>("POST", path, opts),
  put: <T>(path: string, opts?: RequestOptions) => apiRequest<T>("PUT", path, opts),
};

export function qs(params: Record<string, string | number | undefined | null>): string {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== "") sp.set(k, String(v));
  }
  const s = sp.toString();
  return s ? `?${s}` : "";
}
