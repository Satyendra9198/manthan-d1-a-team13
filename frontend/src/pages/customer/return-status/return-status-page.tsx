import { useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { ApiError } from '@/api/client'
import { trackingApi } from '@/api/tracking'
import type { ReturnTracking } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { Card } from './components/card'
import { RefundSummary, RejectionNotice, ReviewNotice } from './components/outcome-cards'
import { ReturnLabelCard } from './components/return-label-card'
import { StatusTimeline } from './components/status-timeline'
import { buildTimeline, isUnderReview, showReturnLabel, statusBadge, subtitle } from './tracking'
import { forgetTrackEmail, recallTrackEmail, rememberTrackEmail } from '../track-lookup/track-email'
import { TrackLookupForm } from '../track-lookup/track-lookup-form'

type Loaded =
  | { rma: string; status: 'error'; message: string }
  | { rma: string; status: 'loaded'; data: ReturnTracking }

/**
 * Customer: track one return. Route: /track/:rmaNumber
 * With the real backend the customer also proves the return is theirs with the order email.
 */
export function ReturnStatusPage() {
  const { rmaNumber = '' } = useParams()
  const [email, setEmail] = useState<string | null>(() =>
    trackingApi.requiresEmail ? recallTrackEmail(rmaNumber) : '',
  )
  const [loaded, setLoaded] = useState<Loaded | null>(null)
  // Loading until we have a result for the RMA in the URL.
  const state = loaded?.rma === rmaNumber ? loaded : ({ status: 'loading' } as const)

  useEffect(() => {
    if (email === null) return
    let cancelled = false
    trackingApi
      .getTracking(rmaNumber, email)
      .then((data) => !cancelled && setLoaded({ rma: rmaNumber, status: 'loaded', data }))
      .catch(
        (e) =>
          !cancelled &&
          setLoaded({
            rma: rmaNumber,
            status: 'error',
            message: e instanceof ApiError ? e.message : "We couldn't load your return. Try again.",
          }),
      )
    return () => {
      cancelled = true
    }
  }, [rmaNumber, email])

  if (email === null) {
    return (
      <TrackLookupForm
        rmaNumber={rmaNumber}
        onSubmit={(_, typed) => {
          rememberTrackEmail(rmaNumber, typed)
          setLoaded(null)
          setEmail(typed)
        }}
      />
    )
  }

  if (state.status === 'loading') {
    return <p className="py-10 text-center text-sm text-muted-foreground">Loading your return…</p>
  }
  if (state.status === 'error' && trackingApi.requiresEmail) {
    return (
      <TrackLookupForm
        rmaNumber={rmaNumber}
        error={state.message}
        onSubmit={(_, typed) => {
          forgetTrackEmail(rmaNumber)
          rememberTrackEmail(rmaNumber, typed)
          setLoaded(null)
          setEmail(typed)
        }}
      />
    )
  }
  if (state.status === 'error') {
    return (
      <Card role="alert" className="text-center">
        <p className="font-medium">Return not found</p>
        <p className="mt-1 text-sm text-muted-foreground">{state.message}</p>
      </Card>
    )
  }

  const t = state.data
  const badge = statusBadge(t)

  return (
    <div className="space-y-3">
      <header className="pb-1">
        <div className="flex items-center justify-between gap-3">
          <h1 className="text-lg font-semibold">{t.rmaNumber}</h1>
          <Badge variant={badge.variant} className="h-5 px-2 text-xs" data-testid="status-badge">
            {badge.label}
          </Badge>
        </div>
        <p className="mt-0.5 text-xs text-muted-foreground">{subtitle(t)}</p>
      </header>

      {t.status === 'refunded' && <RefundSummary tracking={t} />}
      {t.status === 'rejected' && <RejectionNotice tracking={t} />}
      {isUnderReview(t) && <ReviewNotice tracking={t} />}

      <StatusTimeline steps={buildTimeline(t)} />

      {showReturnLabel(t) && <ReturnLabelCard tracking={t} />}
    </div>
  )
}
