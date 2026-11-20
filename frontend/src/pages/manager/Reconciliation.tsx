import { clsx } from "clsx";
import { CheckCircle2, RefreshCw, TriangleAlert } from "lucide-react";
import { Link } from "react-router";
import { useReconciliation } from "../../api/hooks/manager";
import { ErrorState } from "../../components/ErrorState";
import { PageHeader } from "../../components/PageHeader";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { SkeletonCards } from "../../components/ui/Skeleton";
import { formatDateTime } from "../../lib/format";
import { ReconciliationCard } from "../../components/ReconciliationCard";

export function Reconciliation() {
  const { data, isPending, error, refetch, isFetching } = useReconciliation();
  return (
    <>
      <PageHeader
        eyebrow="Operations"
        title="Reconciliation"
        description="Three-way checks between refunds, the ledger and the payment gateway. Every check should read zero."
        actions={
          <Button variant="outline" onClick={() => void refetch()} loading={isFetching && !isPending} icon={<RefreshCw className="size-4" />}>
            Re-run view
          </Button>
        }
      />
      {isPending ? (
        <SkeletonCards count={3} />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <div className="space-y-6">
          <ReconciliationCard />
          <div className="grid gap-4 md:grid-cols-2">
            {data.checks.map((c) => {
              const ok = c.mismatches === 0;
              return (
                <Card key={c.check} className="p-5" data-testid="recon-check" data-ok={ok}>
                  <div className="flex items-start gap-3">
                    <div
                      className={clsx(
                        "grid size-10 shrink-0 place-items-center rounded-xl",
                        ok ? "bg-emerald-500/15 text-emerald-600 dark:text-emerald-400" : "bg-rose-500/15 text-rose-600 dark:text-rose-400",
                      )}
                    >
                      {ok ? <CheckCircle2 className="size-5" aria-hidden /> : <TriangleAlert className="size-5" aria-hidden />}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex items-center justify-between gap-2">
                        <h2 className="font-mono font-semibold">{c.check}</h2>
                        <span className={clsx("text-2xl font-semibold tabular", ok ? "text-emerald-600 dark:text-emerald-400" : "text-rose-600 dark:text-rose-400")}>
                          {c.mismatches}
                        </span>
                      </div>
                      <p className="mt-1 text-sm text-fg-muted">{c.description}</p>
                      {c.sample_ids.length > 0 ? (
                        <div className="mt-3">
                          <p className="text-xs font-semibold text-fg-subtle uppercase">Samples</p>
                          <ul className="mt-1 flex flex-wrap gap-1.5">
                            {c.sample_ids.map((sid) => (
                              <li key={sid}>
                                <Link
                                  to={`/manager/audit?entity_id=${encodeURIComponent(sid)}`}
                                  className="rounded-md bg-surface-2 px-2 py-0.5 font-mono text-xs text-brand hover:underline"
                                >
                                  {sid}
                                </Link>
                              </li>
                            ))}
                          </ul>
                        </div>
                      ) : null}
                    </div>
                  </div>
                </Card>
              );
            })}
          </div>
          <p className="text-xs text-fg-subtle">Last run {formatDateTime(data.run_at)} (IST).</p>
        </div>
      )}
    </>
  );
}
