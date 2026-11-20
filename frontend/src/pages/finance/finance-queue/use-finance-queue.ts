import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '@/api/client'
import { financeApi } from '@/api/finance'
import type { FinanceQueueItem } from '@/api/types'
import { formatPaise } from '@/lib/format'

type Queue =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'loaded'; items: FinanceQueueItem[] }

type Result = { kind: 'success' | 'error'; message: string } | null

const errorMessage = (e: unknown) =>
  e instanceof ApiError
    ? e.message
    : 'Connection problem. Nothing was paid twice. Try again; the same refund key is reused.'

export function useFinanceQueue() {
  const [queue, setQueue] = useState<Queue>({ status: 'loading' })
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [busy, setBusy] = useState<'approve' | 'decline' | null>(null)
  const [panelError, setPanelError] = useState<string | null>(null)
  const [result, setResult] = useState<Result>(null)
  // Blocks a second click before React re-renders; the refund key blocks it on the server too.
  const inFlight = useRef(false)

  const load = useCallback(
    () =>
      financeApi
        .getQueue()
        .then((items) => setQueue({ status: 'loaded', items }))
        .catch((e) => setQueue({ status: 'error', message: errorMessage(e) })),
    [],
  )

  useEffect(() => {
    load()
  }, [load])

  const items = queue.status === 'loaded' ? queue.items : []
  const selected = items.find((i) => i.returnId === selectedId) ?? null

  function select(id: string | null) {
    if (inFlight.current) return
    setSelectedId(id)
    setPanelError(null)
  }

  function removeFromQueue(returnId: string) {
    setQueue((q) =>
      q.status === 'loaded' ? { ...q, items: q.items.filter((i) => i.returnId !== returnId) } : q,
    )
    setSelectedId(null)
  }

  async function run(
    item: FinanceQueueItem,
    action: 'approve' | 'decline',
    call: () => Promise<{ outcome: string; refund: { reference: string } | null }>,
  ) {
    if (inFlight.current) return
    inFlight.current = true
    setBusy(action)
    setPanelError(null)
    setResult(null)
    try {
      const res = await call()
      removeFromQueue(item.returnId)
      const amount = formatPaise(item.refundAmountPaise)
      setResult(
        res.outcome === 'payment_failed'
          ? {
              kind: 'error',
              message: `${item.rmaNumber}: approved, but the gateway failed on every attempt. Nothing was charged. Retry it from Refunds & ledger.`,
            }
          : {
              kind: 'success',
              message:
                action === 'approve'
                  ? `${item.rmaNumber}: refund of ${amount} paid. Reference ${res.refund?.reference}.`
                  : `${item.rmaNumber}: refund declined. The customer will see your reason.`,
            },
      )
    } catch (e) {
      if (e instanceof ApiError && e.code === 'NOT_AWAITING_APPROVAL') {
        // Someone else already decided it: drop it and refresh.
        removeFromQueue(item.returnId)
        setResult({ kind: 'error', message: e.message })
        void load()
      } else {
        setPanelError(errorMessage(e))
      }
    } finally {
      inFlight.current = false
      setBusy(null)
    }
  }

  const approve = (item: FinanceQueueItem) =>
    run(item, 'approve', () => financeApi.approveRefund(item.returnId, item.refundKey))

  const decline = (item: FinanceQueueItem, note: string) =>
    run(item, 'decline', () =>
      financeApi.declineRefund(item.returnId, { note: note.trim() }, item.refundKey),
    )

  return { queue, items, selected, select, busy, panelError, result, approve, decline, reload: load }
}
