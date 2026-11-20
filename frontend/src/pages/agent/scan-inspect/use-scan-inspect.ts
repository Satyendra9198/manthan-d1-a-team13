import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '@/api/client'
import { inspectionApi } from '@/api/inspection'
import type {
  Decision,
  Grade,
  InspectionConfig,
  InspectionDecisionRequest,
  AgentRejectReason,
  ReturnDetails,
} from '@/api/types'
import { refundForGrade, requiresFinanceApproval } from '@/domain/returns'
import { decisionMessage } from './inspection'

type Lookup =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'loaded'; data: ReturnDetails }

type Result = { kind: 'success' | 'error'; message: string } | null

export type DialogKind = 'partial_accept' | 'reject'

export type PartialAcceptInput = { refundAmountPaise: number; reason: string; notes: string }
export type RejectInput = { reason: AgentRejectReason; notes: string }

export type ScanInspectInitial = {
  config?: InspectionConfig
  inspectedToday?: number
  ret?: ReturnDetails
  grade?: Grade
}

const errorMessage = (e: unknown) =>
  e instanceof ApiError ? e.message : 'Connection problem. Your decision was not lost. Try again.'

/** A definitive answer from the server: the next attempt is a new request and needs a new key. */
const isFinal = (e: unknown) => e instanceof ApiError && e.status < 500

/** All state + API calls for the Scan & inspect screen. `initial` pre-fills it (static data). */
export function useScanInspect(initial: ScanInspectInitial = {}) {
  const [config, setConfig] = useState<InspectionConfig | null>(initial.config ?? null)
  const [inspectedToday, setInspectedToday] = useState<number | null>(
    initial.inspectedToday ?? null,
  )
  const [lookup, setLookup] = useState<Lookup>(
    initial.ret ? { status: 'loaded', data: initial.ret } : { status: 'idle' },
  )
  const [grade, setGrade] = useState<Grade | null>(initial.grade ?? null)
  const [notes, setNotes] = useState('')
  const [dialog, setDialog] = useState<DialogKind | null>(null)
  const [submitting, setSubmitting] = useState<Decision | null>(null)
  const [result, setResult] = useState<Result>(null)
  /** Error from the server while a popup is open (shown inside the popup). */
  const [dialogError, setDialogError] = useState<string | null>(null)

  // Guards against double clicks / repeated key presses before React re-renders.
  const inFlight = useRef(false)
  // Same key for every retry of one decision, so the server can never pay twice.
  const idempotencyKey = useRef<string | null>(null)
  // The return currently open for a decision. Cleared the moment a decision is saved, so a
  // late key press handled with stale state can't send a second decision.
  const openReturnId = useRef<string | null>(initial.ret?.id ?? null)

  const refreshStats = useCallback(() => {
    inspectionApi
      .getTodayStats()
      .then((s) => setInspectedToday(s.inspectedToday))
      .catch(() => setInspectedToday(null))
  }, [])

  useEffect(() => {
    inspectionApi.getConfig().then(setConfig).catch(() => setConfig(null))
    refreshStats()
  }, [refreshStats])

  function resetForm() {
    setGrade(null)
    setNotes('')
    setDialog(null)
    setDialogError(null)
    idempotencyKey.current = null
  }

  async function findReturn(qrCode: string) {
    const code = qrCode.trim()
    if (!code) return
    setLookup({ status: 'loading' })
    setResult(null)
    resetForm()
    openReturnId.current = null
    try {
      const data = await inspectionApi.scanReturn({ qrCode: code })
      openReturnId.current = data.id
      setLookup({ status: 'loaded', data })
    } catch (e) {
      setLookup({ status: 'error', message: errorMessage(e) })
    }
  }

  const ret = lookup.status === 'loaded' ? lookup.data : null
  const canDecide = () => !!ret && openReturnId.current === ret.id && !inFlight.current

  /** Routing flag sent with accept / partial accept (server recomputes it). */
  const financeFlag = (refundPaise: number) =>
    requiresFinanceApproval(
      refundPaise,
      ret?.customer.flaggedForReview ?? false,
      config?.financeApprovalAbovePaise,
    )

  async function submit(body: InspectionDecisionRequest) {
    if (!ret || !canDecide()) return
    inFlight.current = true
    idempotencyKey.current ??= crypto.randomUUID()
    setSubmitting(body.decision)
    setResult(null)
    setDialogError(null)
    try {
      const res = await inspectionApi.submitDecision(ret.id, body, idempotencyKey.current)
      openReturnId.current = null
      setResult({ kind: 'success', message: decisionMessage(ret.rmaNumber, res) })
      setLookup({ status: 'idle' })
      resetForm()
      refreshStats()
    } catch (e) {
      // Keep the key after timeouts / 5xx so a retry is recognised as the same request.
      if (isFinal(e)) idempotencyKey.current = null
      if (dialog) setDialogError(errorMessage(e))
      else setResult({ kind: 'error', message: errorMessage(e) })
    } finally {
      inFlight.current = false
      setSubmitting(null)
    }
  }

  /** A: no popup. Sends requiresFinanceApproval true/false. */
  function accept() {
    if (!ret || !grade || !canDecide()) return
    const refund = refundForGrade(ret.item.pricePaise, grade)
    return submit({
      decision: 'accept',
      grade,
      notes: notes.trim(),
      requiresFinanceApproval: financeFlag(refund),
    })
  }

  function partialAccept(input: PartialAcceptInput) {
    if (!grade) return
    return submit({
      decision: 'partial_accept',
      grade,
      refundAmountPaise: input.refundAmountPaise,
      reason: input.reason.trim(),
      notes: input.notes.trim(),
      requiresFinanceApproval: financeFlag(input.refundAmountPaise),
    })
  }

  function reject(input: RejectInput) {
    return submit({ decision: 'reject', grade, reason: input.reason, notes: input.notes.trim() })
  }

  /** P / R: open the popup (partial accept needs a grade first). */
  function openDialog(kind: DialogKind) {
    if (!canDecide()) return
    if (kind === 'partial_accept' && !grade) return
    setDialogError(null)
    setDialog(kind)
  }

  function closeDialog() {
    if (inFlight.current) return
    setDialog(null)
    setDialogError(null)
  }

  return {
    config,
    inspectedToday,
    lookup,
    grade,
    setGrade,
    notes,
    setNotes,
    dialog,
    dialogError,
    openDialog,
    closeDialog,
    submitting,
    result,
    findReturn,
    accept,
    partialAccept,
    reject,
    financeFlag,
  }
}
