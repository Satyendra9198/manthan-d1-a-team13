import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '@/api/client'
import { refundsApi } from '@/api/refunds'
import type { LedgerResponse, RefundRecord } from '@/api/types'
import { formatPaise } from '@/lib/format'

type Data =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'loaded'; refunds: RefundRecord[]; ledger: LedgerResponse }

type Result = { kind: 'success' | 'error'; message: string } | null

export function useRefundsLedger() {
  const [data, setData] = useState<Data>({ status: 'loading' })
  const [retrying, setRetrying] = useState<string | null>(null)
  const [result, setResult] = useState<Result>(null)
  // One retry at a time, even with double clicks; the refund key protects the server side.
  const inFlight = useRef(false)

  const load = useCallback(
    () =>
      Promise.all([refundsApi.getRefunds(), refundsApi.getLedger()])
        .then(([refunds, ledger]) => setData({ status: 'loaded', refunds, ledger }))
        .catch((e) =>
          setData({
            status: 'error',
            message: e instanceof ApiError ? e.message : "Couldn't load refunds. Try again.",
          }),
        ),
    [],
  )

  useEffect(() => {
    load()
  }, [load])

  async function retry(refund: RefundRecord) {
    if (inFlight.current) return
    inFlight.current = true
    setRetrying(refund.refundId)
    setResult(null)
    try {
      const updated = await refundsApi.retryRefund(refund.refundId, refund.idempotencyKey)
      setResult(
        updated.status === 'succeeded'
          ? {
              kind: 'success',
              message: `${refund.rmaNumber}: ${formatPaise(updated.amountPaise)} paid on retry. Reference ${updated.reference}. Ledger rows posted.`,
            }
          : {
              kind: 'error',
              message: `${refund.rmaNumber}: still failing after ${updated.attempts} attempts. Nothing was charged; try again later.`,
            },
      )
    } catch (e) {
      setResult({
        kind: 'error',
        message:
          e instanceof ApiError
            ? e.message
            : 'Connection problem. Retrying again is safe; the same key is reused.',
      })
    } finally {
      // Release the guard only after the tables reflect the result, so the banner's
      // button can't be clicked again while it still shows the old "failed" state.
      await load()
      inFlight.current = false
      setRetrying(null)
    }
  }

  const refunds = data.status === 'loaded' ? data.refunds : []
  const failed = refunds.filter((r) => r.status === 'failed')

  return { data, refunds, failed, retrying, retry, result, reload: load }
}
