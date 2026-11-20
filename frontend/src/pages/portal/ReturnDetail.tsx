import { Ban, Info, Printer, Truck } from "lucide-react";
import { useParams } from "react-router";
import { toast } from "sonner";
import { useCancelReturn, useMyReturn, useShipReturn } from "../../api/hooks/customer";
import type { ReturnDTO } from "../../api/types";
import { shouldCloseDialogOnError } from "../../api/hooks/common";
import { ErrorState } from "../../components/ErrorState";
import { PageHeader } from "../../components/PageHeader";
import { ReturnItems } from "../../components/ReturnItems";
import { ReturnStatusBadge } from "../../components/StatusBadge";
import { Timeline } from "../../components/Timeline";
import { Badge } from "../../components/ui/Badge";
import { Button, ButtonLink } from "../../components/ui/Button";
import { Card, CardHeader } from "../../components/ui/Card";
import { Dialog } from "../../components/ui/Dialog";
import { SkeletonCards } from "../../components/ui/Skeleton";
import { formatDate, formatDateTime, formatPaise } from "../../lib/format";
import { REFUND_METHOD_LABEL } from "../../lib/labels";
import { useConfirm } from "../../lib/useConfirm";

export function ReturnDetail() {
  const { id = "" } = useParams();
  const { data: ret, isPending, error, refetch } = useMyReturn(id);
  const ship = useShipReturn();
  const cancel = useCancelReturn();
  const shipDlg = useConfirm();
  const cancelDlg = useConfirm();

  if (isPending) return <SkeletonCards count={3} />;
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />;

  const can = (a: ReturnDTO["allowed_actions"][number]) => ret.allowed_actions.includes(a);
  const showLabel = ret.label && (ret.label.status === "ACTIVE" || ret.status === "APPROVED" || ret.status === "IN_TRANSIT");
  const refundAmount = ret.approved_refund_paise ?? ret.requested_refund_paise;

  return (
    <>
      <PageHeader
        back={{ to: "/portal/returns", label: "All returns" }}
        eyebrow={`Order ${ret.order_number}`}
        title={
          <span className="flex flex-wrap items-center gap-3">
            <span className="font-mono" data-testid="rma-number">
              {ret.rma_number}
            </span>
            <ReturnStatusBadge status={ret.status} />
          </span>
        }
        description={<span data-testid="status-label">{ret.status_label}</span>}
        actions={
          <>
            {showLabel ? (
              <ButtonLink to={`/portal/returns/${ret.id}/label`} variant="outline" icon={<Printer className="size-4" />} data-testid="view-label">
                Shipping label
              </ButtonLink>
            ) : null}
            {can("SHIP") ? (
              <Button onClick={() => shipDlg.open()} icon={<Truck className="size-4" />} data-testid="mark-shipped">
                Mark as shipped
              </Button>
            ) : null}
            {can("CANCEL") ? (
              <Button variant="ghost" onClick={() => cancelDlg.open()} icon={<Ban className="size-4" />} data-testid="cancel-return">
                Cancel return
              </Button>
            ) : null}
          </>
        }
      />

      <Card className="mb-6 p-5 sm:p-7">
        <Timeline entries={ret.timeline} />
        {ret.status === "APPROVED" && ret.label ? (
          <div className="mt-6 flex gap-3 rounded-xl bg-brand-soft px-4 py-3 text-sm text-brand-soft-fg">
            <Info className="mt-0.5 size-4 shrink-0" aria-hidden />
            <p>
              Print your label — it's valid until <strong>{formatDate(ret.label.expires_at)}</strong>. Once you've handed the parcel
              to the courier, tap “Mark as shipped”.
            </p>
          </div>
        ) : null}
      </Card>

      <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
        <Card>
          <CardHeader title="Items" description={`${ret.items.length} unit${ret.items.length === 1 ? "" : "s"} in this return`} />
          <div className="p-5">
            <ReturnItems ret={ret} />
          </div>
        </Card>

        <div className="space-y-6">
          <Card>
            <CardHeader title="Refund" />
            <dl className="space-y-2.5 p-5 text-sm">
              <div className="flex justify-between gap-3">
                <dt className="text-fg-muted">Method</dt>
                <dd className="font-medium">{REFUND_METHOD_LABEL[ret.refund?.method ?? ret.refund_method]}</dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="text-fg-muted">Item value</dt>
                <dd className="tabular">{formatPaise(ret.requested_refund_paise)}</dd>
              </div>
              {ret.approved_refund_paise !== null && ret.approved_refund_paise !== ret.requested_refund_paise ? (
                <div className="flex justify-between gap-3">
                  <dt className="text-fg-muted">Adjustments</dt>
                  <dd className="tabular text-rose-600 dark:text-rose-400">{formatPaise(ret.approved_refund_paise - ret.requested_refund_paise)}</dd>
                </div>
              ) : null}
              <div className="flex justify-between gap-3 border-t border-line pt-2.5 text-base">
                <dt className="font-medium">{ret.status === "REFUNDED" ? "Refunded" : ret.approved_refund_paise !== null ? "Refund" : "Estimated refund"}</dt>
                <dd className="font-semibold tabular" data-testid="refund-amount">
                  {formatPaise(refundAmount)}
                </dd>
              </div>
              {ret.outcome === "PARTIAL" ? (
                <Badge tone="warning" className="mt-1">
                  Partially accepted
                </Badge>
              ) : null}
            </dl>
          </Card>

          <Card>
            <CardHeader title="Details" />
            <dl className="space-y-2.5 p-5 text-sm">
              <div className="flex justify-between gap-3">
                <dt className="text-fg-muted">Started</dt>
                <dd>{formatDateTime(ret.created_at)}</dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="text-fg-muted">Last update</dt>
                <dd>{formatDateTime(ret.updated_at)}</dd>
              </div>
              {ret.label ? (
                <div className="flex justify-between gap-3">
                  <dt className="text-fg-muted">Tracking</dt>
                  <dd className="font-mono">{ret.label.tracking_number}</dd>
                </div>
              ) : null}
            </dl>
          </Card>
        </div>
      </div>

      <Dialog
        open={shipDlg.isOpen}
        onClose={shipDlg.close}
        busy={ship.isPending}
        title="Mark as shipped?"
        description="Confirm you've handed the parcel to the courier. You won't be able to cancel after this."
        testId="ship-dialog"
        footer={
          <>
            <Button variant="outline" onClick={shipDlg.close} disabled={ship.isPending}>
              Not yet
            </Button>
            <Button
              loading={ship.isPending}
              data-testid="confirm-ship"
              onClick={() =>
                ship.mutate(
                  { ret },
                  {
                    onSuccess: () => {
                      toast.success("Marked as shipped. We'll let you know when it arrives.");
                      shipDlg.close();
                    },
                    onError: (e) => {
                      if (shouldCloseDialogOnError(e)) shipDlg.close();
                    },
                  },
                )
              }
            >
              Yes, it's shipped
            </Button>
          </>
        }
      />

      <Dialog
        open={cancelDlg.isOpen}
        onClose={cancelDlg.close}
        busy={cancel.isPending}
        title="Cancel this return?"
        description="Your label will be voided and the items will become returnable again while they're still inside the return window."
        testId="cancel-dialog"
        footer={
          <>
            <Button variant="outline" onClick={cancelDlg.close} disabled={cancel.isPending}>
              Keep return
            </Button>
            <Button
              variant="danger"
              loading={cancel.isPending}
              data-testid="confirm-cancel"
              onClick={() =>
                cancel.mutate(
                  { ret },
                  {
                    onSuccess: () => {
                      toast.success("Return cancelled.");
                      cancelDlg.close();
                    },
                    onError: (e) => {
                      if (shouldCloseDialogOnError(e)) cancelDlg.close();
                    },
                  },
                )
              }
            >
              Cancel return
            </Button>
          </>
        }
      />
    </>
  );
}
