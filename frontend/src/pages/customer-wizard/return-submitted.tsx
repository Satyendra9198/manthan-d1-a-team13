import { CheckIcon } from "lucide-react"
import { currentStage, type TrackedReturn } from "@/api/returns"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { returnStatusHref } from "@/lib/hash-route"
import { rememberLoadedReturn } from "@/pages/return-status/recent-returns"
import { STAGE_BADGE_VARIANTS, STAGE_TITLES } from "@/pages/return-status/stages"

/** Shown after the return is submitted: its RMA number, the label, and a link to track it. */
export function ReturnSubmitted({ tracked }: { tracked: TrackedReturn }) {
  const stage = currentStage(tracked)
  const label = tracked.label

  return (
    <div className="grid justify-items-center gap-4 pt-6 text-center">
      <div className="grid size-16 place-items-center rounded-full bg-success text-success-foreground">
        <CheckIcon aria-hidden className="size-8" strokeWidth={2.5} />
      </div>

      <div className="grid gap-2">
        <h1 className="text-2xl font-semibold tracking-tight">Return requested</h1>
        <p className="text-[15px] text-muted-foreground">{tracked.rmaNumber}</p>
      </div>

      <Badge variant={STAGE_BADGE_VARIANTS[stage]}>
        {STAGE_TITLES[stage]}
        {label && " · label ready"}
      </Badge>

      <p className="text-sm text-muted-foreground">
        {label ? (
          <>
            We’ve emailed your label to <span className="wrap-anywhere">{tracked.email}</span>.
            Drop off the parcel within 14 days.
          </>
        ) : (
          <>
            We’ll email <span className="wrap-anywhere">{tracked.email}</span> once your return
            is approved.
          </>
        )}
      </p>

      <div className="grid w-full gap-3 pt-1">
        {label && (
          <Button asChild>
            <a href={label.downloadUrl} download>
              Download label
            </a>
          </Button>
        )}
        <Button variant={label ? "outline" : "default"} asChild>
          <a href={returnStatusHref(tracked.rmaNumber)} onClick={() => rememberLoadedReturn(tracked)}>
            Track my return
          </a>
        </Button>
      </div>
    </div>
  )
}
