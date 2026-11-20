import { clsx } from "clsx";
import { CheckCircle2, Scale, TriangleAlert } from "lucide-react";
import { Link } from "react-router";
import { useReconciliation } from "../api/hooks/manager";
import { formatDateTime } from "../lib/format";
import { Badge } from "./ui/Badge";
import { Skeleton } from "./ui/Skeleton";

export function ReconciliationCard() {
  const { data, isPending, error } = useReconciliation();
  if (isPending) return <Skeleton className="h-28 rounded-2xl" />;
  if (error || !data) return null;
  const bad = data.checks.filter((c) => c.mismatches > 0);
  return (
    <Link
      to="/manager/reconciliation"
      className={clsx(
        "flex flex-col gap-4 rounded-2xl border p-5 transition-shadow hover:shadow-pop sm:flex-row sm:items-center",
        data.ok
          ? "border-emerald-200 bg-emerald-50/70 dark:border-emerald-900 dark:bg-emerald-950/30"
          : "border-rose-200 bg-rose-50/70 dark:border-rose-900 dark:bg-rose-950/30",
      )}
      data-testid="reconciliation-card"
      data-ok={data.ok}
    >
      <div className={clsx("grid size-12 shrink-0 place-items-center rounded-2xl text-white", data.ok ? "bg-emerald-500" : "bg-rose-500")}>
        {data.ok ? <Scale className="size-6" aria-hidden /> : <TriangleAlert className="size-6" aria-hidden />}
      </div>
      <div className="flex-1">
        <p className="font-semibold">{data.ok ? "Books reconcile" : `${bad.length} reconciliation check${bad.length === 1 ? "" : "s"} failing`}</p>
        <p className="text-sm text-fg-muted">
          Refunds ↔ ledger ↔ gateway · last run {formatDateTime(data.run_at)}
        </p>
      </div>
      <div className="flex flex-wrap gap-1.5">
        {data.checks.map((c) => (
          <Badge key={c.check} tone={c.mismatches === 0 ? "success" : "danger"} icon={c.mismatches === 0 ? <CheckCircle2 className="size-3" aria-hidden /> : <TriangleAlert className="size-3" aria-hidden />}>
            {c.check}
            {c.mismatches ? ` · ${c.mismatches}` : ""}
          </Badge>
        ))}
      </div>
    </Link>
  );
}
