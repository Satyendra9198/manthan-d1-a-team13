import { ChevronRight, Inbox } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router";
import type { ReturnDTO } from "../api/types";
import { formatDateTime, formatPaise } from "../lib/format";
import { ErrorState } from "./ErrorState";
import { ReturnStatusBadge } from "./StatusBadge";
import { EmptyState } from "./ui/EmptyState";
import { SkeletonTable } from "./ui/Skeleton";

/** Responsive staff queue: table on desktop, stacked cards on phones. */
export function ReturnQueueList({
  items,
  isPending,
  error,
  onRetry,
  href,
  extra,
  emptyTitle,
  emptyDescription,
  testId,
}: {
  items: ReturnDTO[] | undefined;
  isPending: boolean;
  error: unknown;
  onRetry: () => void;
  href: (r: ReturnDTO) => string;
  extra?: (r: ReturnDTO) => ReactNode;
  emptyTitle: string;
  emptyDescription?: string;
  testId?: string;
}) {
  if (isPending) return <SkeletonTable />;
  if (error) return <ErrorState error={error} onRetry={onRetry} />;
  if (!items || items.length === 0) return <EmptyState icon={<Inbox />} title={emptyTitle} description={emptyDescription} />;

  return (
    <>
      <table className="hidden w-full text-sm md:table" data-testid={testId}>
        <thead>
          <tr className="border-b border-line text-left text-xs font-semibold tracking-wide text-fg-subtle uppercase">
            <th className="px-5 py-3 font-semibold">Return</th>
            <th className="px-3 py-3 font-semibold">Customer</th>
            <th className="px-3 py-3 font-semibold">Status</th>
            {extra ? <th className="px-3 py-3 font-semibold">Signals</th> : null}
            <th className="px-3 py-3 text-right font-semibold">Amount</th>
            <th className="px-3 py-3 font-semibold">Updated</th>
            <th className="w-10 px-3 py-3">
              <span className="sr-only">Open</span>
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {items.map((r) => (
            <tr key={r.id} className="group relative transition-colors hover:bg-surface-2/60" data-testid="queue-row" data-rma={r.rma_number}>
              <td className="px-5 py-3.5">
                <Link to={href(r)} className="font-mono font-semibold text-fg after:absolute after:inset-0 focus-visible:outline-none">
                  {r.rma_number}
                </Link>
                <p className="text-xs text-fg-muted">
                  {r.items.length} unit{r.items.length === 1 ? "" : "s"} · {r.order_number}
                </p>
              </td>
              <td className="px-3 py-3.5">
                <p className="font-medium">{r.customer?.name ?? "—"}</p>
                <p className="text-xs text-fg-muted">{r.customer?.email ?? ""}</p>
              </td>
              <td className="px-3 py-3.5">
                <ReturnStatusBadge status={r.status} />
              </td>
              {extra ? <td className="relative z-10 px-3 py-3.5">{extra(r)}</td> : null}
              <td className="px-3 py-3.5 text-right font-semibold tabular">{formatPaise(r.approved_refund_paise ?? r.requested_refund_paise)}</td>
              <td className="px-3 py-3.5 text-xs whitespace-nowrap text-fg-muted">{formatDateTime(r.updated_at)}</td>
              <td className="px-3 py-3.5">
                <ChevronRight className="size-4 text-fg-subtle transition-transform group-hover:translate-x-0.5" aria-hidden />
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <ul className="divide-y divide-line md:hidden" data-testid={testId ? `${testId}-cards` : undefined}>
        {items.map((r) => (
          <li key={r.id}>
            <Link to={href(r)} className="flex items-start gap-3 px-4 py-4 transition-colors active:bg-surface-2" data-testid="queue-card">
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-mono text-sm font-semibold">{r.rma_number}</span>
                  <ReturnStatusBadge status={r.status} />
                </div>
                <p className="mt-1 truncate text-sm text-fg-muted">
                  {r.customer?.name ?? r.order_number} · {r.items.length} unit{r.items.length === 1 ? "" : "s"}
                </p>
                {extra ? <div className="mt-2">{extra(r)}</div> : null}
              </div>
              <div className="text-right">
                <p className="font-semibold tabular">{formatPaise(r.approved_refund_paise ?? r.requested_refund_paise)}</p>
                <ChevronRight className="ml-auto mt-1 size-4 text-fg-subtle" aria-hidden />
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
}
