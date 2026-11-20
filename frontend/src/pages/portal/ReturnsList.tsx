import { ChevronRight, RotateCcw } from "lucide-react";
import { motion, useReducedMotion } from "motion/react";
import { Link } from "react-router";
import { useMyReturns } from "../../api/hooks/customer";
import { ErrorState } from "../../components/ErrorState";
import { PageHeader } from "../../components/PageHeader";
import { ReturnStatusBadge } from "../../components/StatusBadge";
import { ButtonLink } from "../../components/ui/Button";
import { EmptyState } from "../../components/ui/EmptyState";
import { SkeletonCards } from "../../components/ui/Skeleton";
import { formatDate, formatPaise } from "../../lib/format";

export function ReturnsList() {
  const { data, isPending, error, refetch } = useMyReturns();
  const reduce = useReducedMotion();

  return (
    <>
      <PageHeader eyebrow="Returns" title="Your returns" description="Track every return from pickup to refund." />
      {isPending ? (
        <SkeletonCards count={3} />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : data.items.length === 0 ? (
        <EmptyState
          icon={<RotateCcw />}
          title="No returns yet"
          description="When you start a return it'll show up here with live status."
          action={<ButtonLink to="/portal">Browse orders</ButtonLink>}
        />
      ) : (
        <ul className="space-y-3" data-testid="returns-list">
          {data.items.map((r, i) => {
            const amount = r.approved_refund_paise ?? r.requested_refund_paise;
            return (
              <motion.li
                key={r.id}
                initial={reduce ? false : { opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: Math.min(i, 8) * 0.04 }}
              >
                <Link
                  to={`/portal/returns/${r.id}`}
                  className="group flex items-center gap-4 rounded-2xl border border-line bg-surface p-4 shadow-card transition-all hover:border-brand/40 hover:shadow-pop sm:p-5"
                  data-testid="return-card"
                >
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-mono text-sm font-semibold">{r.rma_number}</span>
                      <ReturnStatusBadge status={r.status} />
                    </div>
                    <p className="mt-1 truncate text-sm text-fg-muted">
                      {r.items.map((it) => it.product_name).join(", ")}
                    </p>
                    <p className="mt-1 text-xs text-fg-subtle">
                      {r.status_label} · Started {formatDate(r.created_at)} · Order {r.order_number}
                    </p>
                  </div>
                  <div className="text-right">
                    <p className="font-semibold tabular">{formatPaise(amount)}</p>
                    <p className="text-xs text-fg-subtle">
                      {r.items.length} item{r.items.length === 1 ? "" : "s"}
                    </p>
                  </div>
                  <ChevronRight className="size-5 text-fg-subtle transition-transform group-hover:translate-x-0.5" aria-hidden />
                </Link>
              </motion.li>
            );
          })}
        </ul>
      )}
    </>
  );
}
