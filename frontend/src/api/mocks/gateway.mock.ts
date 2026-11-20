/**
 * Mock payment gateway: stands in for the separate gateway service.
 * Idempotent by key: charging the same key twice returns the first charge and never
 * moves money again. Can be "broken" (mode = 'timeout') to test failures and alerts.
 */

/** A timed-out attempt is reported after this long. */
export const GATEWAY_TIMEOUT_MS = 5000

/** 1 attempt + 3 automatic retries. */
export const GATEWAY_MAX_ATTEMPTS = 4

export type GatewayMode = 'ok' | 'timeout'

export class GatewayTimeoutError extends Error {
  constructor() {
    super('Gateway timed out')
    this.name = 'GatewayTimeoutError'
  }
}

type Charge = { key: string; amountPaise: number; reference: string; at: string; latencyMs: number }

let mode: GatewayMode = 'ok'
let charges = new Map<string, Charge>()
let refSeq = 7700

export function setMockGatewayMode(next: GatewayMode) {
  mode = next
}

export function resetMockGateway() {
  mode = 'ok'
  charges = new Map()
  refSeq = 7700
}

/** Every charge the gateway has actually made (one per key). */
export const getGatewayCharges = () => [...charges.values()]

/** Charge once per key. Throws GatewayTimeoutError when the gateway is "down". */
export function charge(key: string, amountPaise: number): Charge {
  const existing = charges.get(key)
  if (existing) {
    if (existing.amountPaise !== amountPaise) throw new Error('Same key, different amount')
    return existing
  }
  if (mode === 'timeout') throw new GatewayTimeoutError()
  const c: Charge = {
    key,
    amountPaise,
    reference: `GW-${++refSeq}-2026`,
    at: new Date().toISOString(),
    // Simulated, deterministic latency (feeds the audit log and gateway metrics).
    latencyMs: 280 + ((refSeq * 53) % 260),
  }
  charges.set(key, c)
  return c
}
