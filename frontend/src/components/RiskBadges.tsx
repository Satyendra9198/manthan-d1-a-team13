import { CalendarClock, Flag, IndianRupee } from "lucide-react";
import type { ReturnDTO } from "../api/types";
import { FINANCE_THRESHOLD_PAISE } from "../lib/format";
import { Badge } from "./ui/Badge";

/** Risk signals for finance: flagged customer, > ₹10,000 after grade [A-12], returns in 30 days [A-14]. */
export function RiskBadges({ ret }: { ret: ReturnDTO }) {
  const amount = ret.approved_refund_paise ?? ret.requested_refund_paise;
  const flagged = ret.customer?.is_flagged ?? false;
  const r30 = ret.customer?.returns_30d ?? null;
  return (
    <div className="flex flex-wrap gap-1.5" data-testid="risk-badges">
      {flagged ? (
        <Badge tone="danger" icon={<Flag className="size-3" aria-hidden />}>
          Flagged customer
        </Badge>
      ) : null}
      {amount > FINANCE_THRESHOLD_PAISE ? (
        <Badge tone="warning" icon={<IndianRupee className="size-3" aria-hidden />}>
          &gt; ₹10,000
        </Badge>
      ) : null}
      {r30 !== null ? (
        <Badge tone={r30 > 3 ? "danger" : r30 >= 3 ? "warning" : "neutral"} icon={<CalendarClock className="size-3" aria-hidden />}>
          {r30} return{r30 === 1 ? "" : "s"} / 30d
        </Badge>
      ) : null}
    </div>
  );
}
