import { clsx } from "clsx";
import { Package } from "lucide-react";
import type { ReturnDTO, ReturnItemDTO } from "../api/types";
import { formatPaise } from "../lib/format";
import { REJECT_REASON_LABEL, RETURN_REASON_LABEL } from "../lib/labels";
import { ItemStatusBadge } from "./StatusBadge";
import { Badge } from "./ui/Badge";

function GradeChip({ grade }: { grade: ReturnItemDTO["grade"] }) {
  if (!grade) return null;
  return (
    <Badge tone={grade === "C" ? "warning" : "success"} title={grade === "C" ? "Grade C refunds 50%" : "Full refund"}>
      Grade {grade}
    </Badge>
  );
}

/** Per-unit breakdown with grade and refund amount. Used by customer, finance and warehouse views. */
export function ReturnItems({ ret, showSku = false }: { ret: ReturnDTO; showSku?: boolean }) {
  return (
    <ul className="divide-y divide-line" data-testid="return-items">
      {ret.items.map((it) => {
        const refunded = it.refund_paise;
        const deducted = refunded !== null && refunded < it.unit_price_paise;
        return (
          <li key={it.id} className="flex gap-3 py-4 first:pt-0 last:pb-0" data-testid="return-item">
            <div className="grid size-11 shrink-0 place-items-center rounded-xl bg-surface-2 text-fg-muted">
              <Package className="size-5" aria-hidden />
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-start justify-between gap-x-3 gap-y-1">
                <div className="min-w-0">
                  <p className="font-medium text-fg">
                    {it.product_name}
                    <span className="ml-1.5 text-xs font-normal text-fg-subtle">unit {it.unit_index}</span>
                  </p>
                  <p className="text-xs text-fg-muted">
                    {it.category_name}
                    {showSku ? <span className="font-mono"> · {it.sku}</span> : null}
                  </p>
                </div>
                <div className="text-right">
                  {refunded !== null ? (
                    <>
                      <p className={clsx("font-semibold tabular", refunded === 0 ? "text-fg-muted" : "text-fg")}>{formatPaise(refunded)}</p>
                      {deducted || refunded === 0 ? (
                        <p className="text-xs text-fg-subtle line-through tabular">{formatPaise(it.unit_price_paise)}</p>
                      ) : null}
                    </>
                  ) : (
                    <p className="font-semibold text-fg tabular">{formatPaise(it.unit_price_paise)}</p>
                  )}
                </div>
              </div>
              <div className="mt-2 flex flex-wrap items-center gap-1.5">
                <ItemStatusBadge status={it.status} />
                <GradeChip grade={it.grade} />
                <Badge tone="neutral">{RETURN_REASON_LABEL[it.reason]}</Badge>
                {it.reject_reason ? <Badge tone="danger">{REJECT_REASON_LABEL[it.reject_reason]}</Badge> : null}
              </div>
              {it.reason_note ? <p className="mt-2 text-sm text-fg-muted">“{it.reason_note}”</p> : null}
            </div>
          </li>
        );
      })}
    </ul>
  );
}
