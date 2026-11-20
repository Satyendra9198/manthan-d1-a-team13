import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { auditApi } from '@/api/audit'
import { ApiError } from '@/api/client'
import type { AuditAction, AuditActor, AuditLogEntry, AuditQuery } from '@/api/types'

export type Filters = { rma: string; actor: string; action: string; days: string; customer: string }

/** Filters live in the URL (?rma=&actor=&action=&days=&customer=) so views can be linked and shared. */
export function useAuditLog() {
  const [params, setParams] = useSearchParams()
  const filters: Filters = {
    rma: params.get('rma') ?? '',
    actor: params.get('actor') ?? '',
    action: params.get('action') ?? '',
    days: params.get('days') ?? '30',
    customer: params.get('customer') ?? '',
  }
  // Typing in the RMA box updates the URL right away but only queries after a short pause.
  const [debouncedRma, setDebouncedRma] = useState(filters.rma)
  useEffect(() => {
    const t = setTimeout(() => setDebouncedRma(filters.rma), 250)
    return () => clearTimeout(t)
  }, [filters.rma])

  const query: AuditQuery = {
    rma: debouncedRma || undefined,
    actor: (filters.actor || undefined) as AuditActor | undefined,
    action: (filters.action || undefined) as AuditAction | undefined,
    days: filters.days === 'all' ? undefined : Number(filters.days),
    customer: filters.customer || undefined,
  }
  const key = JSON.stringify(query)

  const [result, setResult] = useState<{ key: string; entries: AuditLogEntry[] } | { key: string; error: string } | null>(
    null,
  )

  useEffect(() => {
    let cancelled = false
    auditApi
      .getEntries(JSON.parse(key) as AuditQuery)
      .then((entries) => !cancelled && setResult({ key, entries }))
      .catch(
        (e) => !cancelled && setResult({ key, error: e instanceof ApiError ? e.message : "Couldn't load the audit log." }),
      )
    return () => {
      cancelled = true
    }
  }, [key])

  function setFilter(name: keyof Filters, value: string) {
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (value && !(name === 'days' && value === '30')) next.set(name, value)
        else next.delete(name)
        return next
      },
      { replace: true },
    )
  }

  return {
    filters,
    setFilter,
    entries: result && 'entries' in result ? result.entries : null,
    error: result && 'error' in result ? result.error : null,
    refreshing: result?.key !== key,
  }
}
