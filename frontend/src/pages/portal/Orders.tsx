import { clsx } from "clsx";
import { ChevronRight, PackageOpen, ShoppingBag } from "lucide-react";
import { motion, useReducedMotion } from "motion/react";
import { Link } from "react-router";
import { useOrders } from "../../api/hooks/customer";
import { useSession } from "../../auth/session";
import { ErrorState } from "../../components/ErrorState";
import { PageHeader } from "../../components/PageHeader";
import { Badge } from "../../components/ui/Badge";
import { EmptyState } from "../../components/ui/EmptyState";
import { SkeletonCards } from "../../components/ui/Skeleton";
import { formatDate, formatPaise } from "../../lib/format";
import { humanize } from "../../lib/labels";

export function Orders() {
  const session = useSession();
  const { data, isPending, error, refetch } = useOrders();
  const reduce = useReducedMotion();
  const first = session?.principal.name.split(" ")[0];

  return (
    <>
      <PageHeader
        eyebrow="Your orders"
        title={first ? `Hi ${first}, what would you like to return?` : "Your orders"}
        description="Pick an order to see which items can be returned and until when."
      />
      {isPending ? (
        <SkeletonCards count={4} className="sm:grid-cols-2" />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : data.items.length === 0 ? (
        <EmptyState icon={<ShoppingBag />} title="No orders yet" description="Orders placed with this email will show up here." />
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2" data-testid="orders-list">
          {data.items.map((o, i) => (
            <motion.li
              key={o.order_number}
              initial={reduce ? false : { opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: Math.min(i, 8) * 0.04 }}
            >
              <Link
                to={`/portal/orders/${encodeURIComponent(o.order_number)}`}
                className="group flex h-full items-center gap-4 rounded-2xl border border-line bg-surface p-5 shadow-card transition-all hover:-translate-y-0.5 hover:border-brand/40 hover:shadow-pop"
                data-testid="order-card"
                data-order={o.order_number}
              >
                <div
                  className={clsx(
                    "grid size-12 shrink-0 place-items-center rounded-2xl",
                    o.returnable_units > 0 ? "bg-brand-soft text-brand-soft-fg" : "bg-surface-2 text-fg-subtle",
                  )}
                >
                  <PackageOpen className="size-6" aria-hidden />
                </div>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-mono text-sm font-semibold text-fg">{o.order_number}</p>
                    <Badge tone={o.status === "DELIVERED" ? "success" : o.status === "CANCELLED" ? "neutral" : "info"}>
                      {humanize(o.status)}
                    </Badge>
                  </div>
                  <p className="mt-1 text-sm text-fg-muted">
                    {o.item_count} item{o.item_count === 1 ? "" : "s"} · {formatPaise(o.total_paise)}
                  </p>
                  <p className="mt-0.5 text-xs text-fg-subtle">
                    {o.delivered_at ? `Delivered ${formatDate(o.delivered_at)}` : `Placed ${formatDate(o.placed_at)}`}
                  </p>
                </div>
                <div className="flex flex-col items-end gap-2">
                  {o.returnable_units > 0 ? (
                    <Badge tone="success" dot data-testid="returnable-units">
                      {o.returnable_units} returnable
                    </Badge>
                  ) : (
                    <Badge tone="neutral">Nothing to return</Badge>
                  )}
                  <ChevronRight className="size-5 text-fg-subtle transition-transform group-hover:translate-x-0.5" aria-hidden />
                </div>
              </Link>
            </motion.li>
          ))}
        </ul>
      )}
    </>
  );
}
