import { clsx } from "clsx";
import { ArrowRight, CalendarCheck, CircleSlash, CreditCard, Package, Truck } from "lucide-react";
import { Link, useParams } from "react-router";
import { useOrder } from "../../api/hooks/customer";
import type { OrderUnit } from "../../api/types";
import { ErrorState } from "../../components/ErrorState";
import { PageHeader } from "../../components/PageHeader";
import { Badge } from "../../components/ui/Badge";
import { ButtonLink } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { SkeletonCards } from "../../components/ui/Skeleton";
import { formatCalendarDate, formatDate, formatPaise } from "../../lib/format";
import { humanize } from "../../lib/labels";
import { isSelectable, unitKey } from "../../lib/units";

function EligibilityLine({ unit }: { unit: OrderUnit }) {
  const e = unit.eligibility;
  if (e.eligible) {
    const last = e.details.last_eligible_date;
    const urgent = e.details.days_remaining !== null && e.details.days_remaining <= 2;
    return (
      <div className="mt-2 space-y-1">
        <Badge tone={urgent ? "warning" : "success"} dot data-testid="eligibility-ok" icon={<CalendarCheck className="size-3" aria-hidden />}>
          {last ? `Returnable until ${formatCalendarDate(last)}` : "Returnable"}
        </Badge>
        {/* Microcopy comes from the backend only (§9.4). */}
        <p className="text-xs text-fg-muted" data-testid="eligibility-message">
          {e.message}
        </p>
      </div>
    );
  }
  return (
    <div className="mt-2 flex gap-2 rounded-xl bg-surface-2 px-3 py-2 text-sm text-fg-muted" data-testid="eligibility-blocked">
      <CircleSlash className="mt-0.5 size-4 shrink-0" aria-hidden />
      <p data-testid="eligibility-message">
        {e.message}
        {unit.active_return_id ? (
          <>
            {" "}
            <Link to={`/portal/returns/${unit.active_return_id}`} className="font-medium text-brand hover:underline">
              Track return
            </Link>
          </>
        ) : null}
      </p>
    </div>
  );
}

export function OrderDetail() {
  const { orderNumber = "" } = useParams();
  const { data, isPending, error, refetch } = useOrder(orderNumber);

  if (isPending) return <SkeletonCards count={3} />;
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />;

  const selectable = data.units.filter(isSelectable);
  return (
    <>
      <PageHeader
        back={{ to: "/portal", label: "All orders" }}
        eyebrow="Order"
        title={<span className="font-mono">{data.order_number}</span>}
        description={
          <span className="flex flex-wrap items-center gap-x-4 gap-y-1">
            <span className="inline-flex items-center gap-1.5">
              <Truck className="size-4" aria-hidden />
              {data.delivered_at ? `Delivered ${formatDate(data.delivered_at)}` : humanize(data.status)}
            </span>
            <span className="inline-flex items-center gap-1.5">
              <CreditCard className="size-4" aria-hidden />
              {humanize(data.payment_method)} · {formatPaise(data.total_paise)}
            </span>
          </span>
        }
        actions={
          selectable.length > 0 ? (
            <ButtonLink to={`/portal/orders/${encodeURIComponent(data.order_number)}/return`} size="lg" data-testid="start-return">
              Start a return <ArrowRight className="size-4" aria-hidden />
            </ButtonLink>
          ) : undefined
        }
      />

      <Card>
        <div className="flex items-center justify-between border-b border-line px-5 py-3.5">
          <h2 className="text-sm font-semibold">Items in this order</h2>
          <span className="text-xs text-fg-muted">
            {selectable.length} of {data.units.length} unit{data.units.length === 1 ? "" : "s"} returnable
          </span>
        </div>
        <ul className="divide-y divide-line" data-testid="order-units">
          {data.units.map((u) => {
            const ok = isSelectable(u);
            return (
              <li key={unitKey(u)} className={clsx("flex gap-4 px-5 py-4", !ok && "opacity-90")} data-testid="order-unit" data-eligible={ok}>
                <div className={clsx("grid size-12 shrink-0 place-items-center rounded-xl", ok ? "bg-brand-soft text-brand-soft-fg" : "bg-surface-2 text-fg-subtle")}>
                  <Package className="size-5" aria-hidden />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="font-medium text-fg">
                        {u.product_name}
                        <span className="ml-1.5 text-xs font-normal text-fg-subtle">unit {u.unit_index}</span>
                      </p>
                      <p className="text-xs text-fg-muted">{u.category_name}</p>
                    </div>
                    <p className="font-semibold tabular">{formatPaise(u.price_paise)}</p>
                  </div>
                  <EligibilityLine unit={u} />
                  {ok ? (
                    <Link
                      to={`/portal/orders/${encodeURIComponent(data.order_number)}/return?unit=${encodeURIComponent(unitKey(u))}`}
                      className="mt-2 inline-flex items-center gap-1 text-sm font-medium text-brand hover:underline"
                    >
                      Return this item <ArrowRight className="size-3.5" aria-hidden />
                    </Link>
                  ) : null}
                </div>
              </li>
            );
          })}
        </ul>
      </Card>
    </>
  );
}
