import { useEffect, useRef, useState } from 'react'
import { ApiError } from '@/api/client'
import { customersApi } from '@/api/customers'
import type { CustomerProfile, FlagReviewRequest } from '@/api/types'

const message = (e: unknown) => (e instanceof ApiError ? e.message : 'Something went wrong. Try again.')

export function useCustomerProfile(customerId: string) {
  const [loaded, setLoaded] = useState<
    { id: string; profile: CustomerProfile } | { id: string; error: string } | null
  >(null)
  const [saving, setSaving] = useState<FlagReviewRequest['action'] | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)
  const [saved, setSaved] = useState<string | null>(null)
  const inFlight = useRef(false)

  useEffect(() => {
    let cancelled = false
    customersApi
      .getProfile(customerId)
      .then((profile) => !cancelled && setLoaded({ id: customerId, profile }))
      .catch((e) => !cancelled && setLoaded({ id: customerId, error: message(e) }))
    return () => {
      cancelled = true
    }
  }, [customerId])

  const current = loaded?.id === customerId ? loaded : null

  async function review(body: FlagReviewRequest) {
    if (inFlight.current) return
    inFlight.current = true
    setSaving(body.action)
    setSaveError(null)
    setSaved(null)
    try {
      const profile = await customersApi.reviewFlag(customerId, body)
      setLoaded({ id: customerId, profile })
      setSaved(
        body.action === 'clear'
          ? 'Flag cleared. A FLAG_CHANGED row was written to the audit log.'
          : 'Flag kept. Your review was written to the audit log.',
      )
      return true
    } catch (e) {
      setSaveError(message(e))
      return false
    } finally {
      inFlight.current = false
      setSaving(null)
    }
  }

  return {
    profile: current && 'profile' in current ? current.profile : null,
    error: current && 'error' in current ? current.error : null,
    loading: !current,
    saving,
    saveError,
    saved,
    review,
  }
}
