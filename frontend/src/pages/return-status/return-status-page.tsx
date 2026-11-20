import { useEffect, useState } from "react"
import { Link, useParams } from "react-router"
import {
  currentStage,
  getReturn,
  RETURN_STAGES,
  type ReturnStage,
  type TrackedReturn,
} from "@/api/returns"
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { TRACK_RETURN_PATH } from "@/config/customer-paths"
import { supportMailto } from "@/config/support"
import { formatDate, formatDateTime, formatPaise } from "@/lib/format"
import { CustomerHeader } from "@/pages/customer-wizard/customer-header"
import { recentlyLoadedReturn } from "./recent-returns"
import { ReturnTimeline, type TimelineStep } from "./return-timeline"
import { STAGE_BADGE_VARIANTS, STAGE_TITLES } from "./stages"

type LoadState =
  | { status: "loading" }
  | { status: "found"; tracked: TrackedReturn }
  | { status: "not_found" }
  | { status: "error" }

/** Route element for /returns/:rmaNumber. A fresh page per RMA number. */
export function ReturnStatusRoute() {
  const { rmaNumber = "" } = useParams()
  return <ReturnStatusPage key={rmaNumber} rmaNumber={rmaNumber} />
}

/** Return status page: where a return is, plus the label, refund or rejection details. */
export function ReturnStatusPage({ rmaNumber }: { rmaNumber: string }) {
  const [state, setState] = useState<LoadState>(() => {
    const recent = recentlyLoadedReturn(rmaNumber)
    return recent ? { status: "found", tracked: recent } : { status: "loading" }
  })

  useEffect(() => {
    document.documentElement.scrollTop = 0
    // Just loaded by the previous page: nothing to fetch.
    if (recentlyLoadedReturn(rmaNumber)) return
    let cancelled = false
    getReturn(rmaNumber).then(
      (tracked) => {
        if (!cancelled) setState(tracked ? { status: "found", tracked } : { status: "not_found" })
      },
      () => {
        if (!cancelled) setState({ status: "error" })
      },
    )
    return () => {
      cancelled = true
    }
  }, [rmaNumber])

  return (
    <div className="min-h-svh bg-muted/50">
      <CustomerHeader current="track-return" />
      <main className="mx-auto grid w-full max-w-md gap-4 px-4 pt-6 pb-10 sm:pt-10">
        {state.status === "loading" && (
          <p role="status" className="text-sm text-muted-foreground">
            Loading your return…
          </p>
        )}
        {state.status === "not_found" && (
          <>
            <Alert variant="danger">
              <AlertTitle>We couldn’t find return {rmaNumber}</AlertTitle>
              <AlertDescription>Check the RMA number in your confirmation email.</AlertDescription>
            </Alert>
            <Button variant="outline" className="w-full" asChild>
              <Link to={TRACK_RETURN_PATH}>Track a different return</Link>
            </Button>
          </>
        )}
        {state.status === "error" && (
          <Alert variant="danger">
            <AlertTitle>Something went wrong</AlertTitle>
            <AlertDescription>We couldn’t load your return just now. Please try again.</AlertDescription>
          </Alert>
        )}
        {state.status === "found" && <ReturnDetails tracked={state.tracked} />}
      </main>
    </div>
  )
}

function ReturnDetails({ tracked }: { tracked: TrackedReturn }) {
  const stage = currentStage(tracked)
  const total = tracked.items.reduce((sum, item) => sum + item.pricePaise, 0)
  const names = tracked.items.map((item) => item.name).join(", ")
  const subtitle =
    stage === "rejected"
      ? `${names} · ${formatPaise(total)}`
      : `${names} · refund ${tracked.refundMethod === "original_payment" ? "to original payment" : "as store credit"}`
  // The label is only useful until the parcel reaches the warehouse.
  const label = stage === "approved" || stage === "in_transit" ? tracked.label : undefined

  return (
    <>
      <div className="grid gap-1">
        <div className="flex items-center justify-between gap-3">
          <h1 className="text-[22px] font-semibold tracking-tight">{tracked.rmaNumber}</h1>
          <Badge variant={STAGE_BADGE_VARIANTS[stage]}>{STAGE_TITLES[stage]}</Badge>
        </div>
        <p className="text-sm text-muted-foreground">{subtitle}</p>
      </div>

      {stage === "refunded" && tracked.refund && (
        <RefundSummary tracked={tracked} refund={tracked.refund} total={total} />
      )}
      {stage === "rejected" && tracked.rejection && (
        <RejectionSummary rmaNumber={tracked.rmaNumber} rejection={tracked.rejection} />
      )}

      <Card className="p-5">
        <ReturnTimeline steps={buildTimeline(tracked, total)} />
      </Card>

      {label && <ReturnLabelCard label={label} />}
    </>
  )
}

/** What happened so far, then the stages still to come. */
function buildTimeline(tracked: TrackedReturn, total: number): TimelineStep[] {
  const { events } = tracked
  const last = events[events.length - 1]
  const finished = last.stage === "refunded" || last.stage === "rejected"

  const steps: TimelineStep[] = events.map((event, index) => ({
    key: `${event.stage}-${index}`,
    title: STAGE_TITLES[event.stage],
    description: [formatDateTime(event.at), event.note].filter(Boolean).join(" · "),
    state:
      event.stage === "rejected"
        ? "failed"
        : index === events.length - 1 && !finished
          ? "current"
          : "done",
  }))

  if (!finished) {
    const reached = (RETURN_STAGES as readonly ReturnStage[]).indexOf(last.stage)
    for (const stage of RETURN_STAGES.slice(reached + 1)) {
      steps.push({
        key: stage,
        title: STAGE_TITLES[stage],
        description: upcomingDescription(stage, tracked, total),
        state: "upcoming",
      })
    }
  }
  return steps
}

function upcomingDescription(stage: ReturnStage, tracked: TrackedReturn, total: number) {
  switch (stage) {
    case "approved":
      return "We review new returns within a day"
    case "in_transit":
      return "Drop off the parcel within 14 days"
    case "received":
      return "We’ll update you when it reaches our warehouse"
    case "inspected":
      return "Usually within 2 days of arrival"
    case "refunded":
      return `${formatPaise(total)} to ${
        tracked.refundMethod === "original_payment"
          ? "your original payment method"
          : "your ReturnRight wallet"
      }`
    default:
      return undefined
  }
}

function RefundSummary({
  tracked,
  refund,
  total,
}: {
  tracked: TrackedReturn
  refund: NonNullable<TrackedReturn["refund"]>
  total: number
}) {
  const amount = formatPaise(refund.amountPaise)
  const toOriginal = tracked.refundMethod === "original_payment"
  const destination = toOriginal
    ? `To ${tracked.payment.method} · ${tracked.payment.account}`
    : "To your ReturnRight wallet"
  const timing = toOriginal
    ? `Refunds can take 1–2 business days to show in your account. Reference: ${refund.reference}.`
    : `Reference: ${refund.reference}.`

  return (
    <>
      <div className="grid gap-1 rounded-lg bg-success p-4">
        <p className="text-2xl font-bold tracking-tight text-success-foreground">{amount} refunded</p>
        <p className="text-sm">
          {destination} · {formatDate(refund.paidAt)}
        </p>
      </div>
      {refund.adjustment ? (
        <Card className="grid gap-2">
          <h2 className="text-[15px] font-semibold">
            Why {amount} and not {formatPaise(total)}?
          </h2>
          <p className="text-sm text-muted-foreground">{refund.adjustment}</p>
          <p className="text-[13px] text-muted-foreground">{timing}</p>
        </Card>
      ) : (
        <p className="text-[13px] text-muted-foreground">{timing}</p>
      )}
    </>
  )
}

function RejectionSummary({
  rmaNumber,
  rejection,
}: {
  rmaNumber: string
  rejection: NonNullable<TrackedReturn["rejection"]>
}) {
  return (
    <>
      <Alert variant="danger">
        <AlertTitle>We couldn’t accept this return</AlertTitle>
        <AlertDescription className="grid gap-2 text-sm">
          <p>
            Reason: {rejection.reason}. {rejection.details}
          </p>
          <p className="text-foreground">No refund has been paid.</p>
        </AlertDescription>
      </Alert>
      <Card className="grid gap-2">
        <h2 className="text-[15px] font-semibold">Think this is a mistake?</h2>
        <p className="text-sm text-muted-foreground">
          Contact support within 7 days and quote your RMA number. We’ll review the inspection
          photos with you.
        </p>
        <Button variant="outline" className="mt-1 w-full" asChild>
          <a href={supportMailto(`Rejected return ${rmaNumber}`)}>Contact support</a>
        </Button>
      </Card>
    </>
  )
}

function ReturnLabelCard({ label }: { label: NonNullable<TrackedReturn["label"]> }) {
  return (
    <Card className="grid gap-3">
      <h2 className="text-[15px] font-semibold">Your return label</h2>
      <div className="grid grid-cols-[auto_1fr] items-start gap-4">
        <img src={label.qrCodeUrl} alt="Return label QR code" className="size-26" />
        <div className="grid justify-items-start gap-3">
          <p className="text-[13px] text-muted-foreground">
            Show this QR code at any drop-off point, or print the label and stick it on the parcel.
          </p>
          <Button variant="outline" asChild>
            <a href={label.downloadUrl} download>
              Download label
            </a>
          </Button>
        </div>
      </div>
    </Card>
  )
}
