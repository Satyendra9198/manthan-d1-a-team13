import { clsx } from "clsx";
import { Check, CircleHelp, Flag, Loader2, RefreshCw, RotateCcw, ShieldAlert, User, X } from "lucide-react";
import { useState } from "react";
import { useParams } from "react-router";
import { toast } from "sonner";
import {
  useApproveRefund,
  useFinanceReturn,
  useRecheckAttempt,
  useRejectRefund,
  useRetryRefund,
} from "../../api/hooks/finance";
import type { RefundAttemptDTO, ReturnDTO } from "../../api/types";
import { shouldCloseDialogOnError } from "../../api/hooks/common";
import { ErrorState } from "../../components/ErrorState";
import { PageHeader } from "../../components/PageHeader";
import { ReturnItems } from "../../components/ReturnItems";
import { RiskBadges } from "../../components/RiskBadges";
import { AttemptStatusBadge, ReturnStatusBadge } from "../../components/StatusBadge";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card, CardHeader } from "../../components/ui/Card";
import { Dialog } from "../../components/ui/Dialog";
import { TextArea } from "../../components/ui/Field";
import { SkeletonCards } from "../../components/ui/Skeleton";
import { FINANCE_THRESHOLD_PAISE, formatDateTime, formatPaise } from "../../lib/format";
import { FINANCE_REASON_LABEL, REFUND_METHOD_LABEL } from "../../lib/labels";
import { useConfirm } from "../../lib/useConfirm";

function hasOpenAttempt(ret: ReturnDTO): boolean {
  return (ret.refund?.attempts ?? []).some((a) => a.status === "UNKNOWN" || a.status === "PENDING");
}

export function FinanceReview() {
  const { id = "" } = useParams();
  const { data: ret, isPending, error, refetch, isFetching } = useFinanceReturn(id);
  const approve = useApproveRefund();
  const reject = useRejectRefund();
  const retry = useRetryRefund();
  const recheck = useRecheckAttempt();
  const approveDlg = useConfirm();
  const rejectDlg = useConfirm();
  const retryDlg = useConfirm();
  const [note, setNote] = useState("");
  const [rejectNote, setRejectNote] = useState("");
  const [storeCredit, setStoreCredit] = useState(false);

  if (isPending) return <SkeletonCards count={3} />;
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />;

  const can = (a: ReturnDTO["allowed_actions"][number]) => ret.allowed_actions.includes(a);
  const open = hasOpenAttempt(ret);
  const unknownAttempts = (ret.refund?.attempts ?? []).filter((a) => a.status === "UNKNOWN");
  // Never offer Retry while an attempt is UNKNOWN/PENDING (ADR-001, A-22).
  const canRetry = can("RETRY_REFUND") && ret.status === "REFUND_FAILED" && !open;
  const amount = ret.approved_refund_paise ?? ret.requested_refund_paise;
  const deductions = ret.requested_refund_paise - amount;
  const checkingGateway = ret.status === "REFUND_PENDING";

  function after202(accepted: boolean, next: ReturnDTO, success: string) {
    if (accepted || next.status === "REFUND_PENDING") {
      toast.info("Checking with payment gateway…", { description: "We'll update this page as soon as the refund settles." });
    } else if (next.status === "REFUNDED") {
      toast.success(success);
    } else if (next.status === "REFUND_FAILED") {
      toast.error("Gateway declined the refund", { description: "Retry, or switch to store credit." });
    } else {
      toast.success(next.status_label);
    }
  }

  return (
    <>
      <PageHeader
        back={{ to: "/finance", label: "Review queue" }}
        eyebrow="Finance review"
        title={
          <span className="flex flex-wrap items-center gap-3">
            <span className="font-mono">{ret.rma_number}</span>
            <ReturnStatusBadge status={ret.status} />
          </span>
        }
        description={`Order ${ret.order_number} · ${REFUND_METHOD_LABEL[ret.refund?.method ?? ret.refund_method]}`}
        actions={
          <>
            {can("FINANCE_REJECT") ? (
              <Button
                variant="outline"
                onClick={() => {
                  setRejectNote("");
                  rejectDlg.open();
                }}
                icon={<X className="size-4" />}
                data-testid="finance-reject"
              >
                Reject
              </Button>
            ) : null}
            {can("FINANCE_APPROVE") ? (
              <Button
                variant="success"
                onClick={() => {
                  setNote("");
                  approveDlg.open();
                }}
                icon={<Check className="size-4" />}
                data-testid="finance-approve"
              >
                Approve refund
              </Button>
            ) : null}
            {canRetry ? (
              <Button
                onClick={() => {
                  setStoreCredit(false);
                  retryDlg.open();
                }}
                icon={<RotateCcw className="size-4" />}
                data-testid="finance-retry"
              >
                Retry refund
              </Button>
            ) : null}
          </>
        }
      />

      {checkingGateway ? (
        <div
          role="status"
          aria-live="polite"
          className="mb-6 flex items-center gap-3 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-100"
          data-testid="gateway-checking"
        >
          <Loader2 className="size-4 shrink-0 animate-spin" aria-hidden />
          <p className="flex-1">
            <span className="font-semibold">Checking with payment gateway…</span> The refund outcome isn't final yet. This page refreshes
            automatically{isFetching ? "" : " every few seconds"}.
          </p>
        </div>
      ) : null}

      <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
        <div className="space-y-6">
          <Card>
            <CardHeader title="Items" description="Grades and per-unit refunds from warehouse inspection." />
            <div className="p-5">
              <ReturnItems ret={ret} showSku />
            </div>
          </Card>

          <Card>
            <CardHeader
              title="Refund attempts"
              description="Each attempt uses its own gateway idempotency key. UNKNOWN attempts are resolved by recheck or the reconciler only."
            />
            <div className="p-5">
              <AttemptTable
                attempts={ret.refund?.attempts ?? []}
                recheckingId={recheck.isPending ? recheck.variables.attemptId : null}
                onRecheck={(a) =>
                  recheck.mutate(
                    { attemptId: a.id, returnId: ret.id },
                    {
                      onSuccess: (next) => {
                        const found = next.refund?.attempts.find((x) => x.id === a.id);
                        if (found?.status === "UNKNOWN") toast.info("Gateway still hasn't confirmed. We'll keep checking.");
                        else toast.success(`Attempt #${a.attempt_no}: ${found?.status.toLowerCase() ?? "updated"}`);
                      },
                    },
                  )
                }
                canRecheck={can("RECHECK_REFUND") || unknownAttempts.length > 0}
              />
            </div>
          </Card>
        </div>

        <div className="space-y-6">
          <Card data-testid="risk-panel">
            <CardHeader title="Customer risk" icon={<ShieldAlert className="size-4" />} />
            <div className="space-y-4 p-5">
              <div className="flex items-center gap-3">
                <div className="grid size-10 place-items-center rounded-full bg-surface-2">
                  <User className="size-5 text-fg-muted" aria-hidden />
                </div>
                <div className="min-w-0">
                  <p className="truncate font-medium">{ret.customer?.name ?? "—"}</p>
                  <p className="truncate text-sm text-fg-muted">{ret.customer?.email ?? ""}</p>
                </div>
              </div>
              <RiskBadges ret={ret} />
              <dl className="grid grid-cols-2 gap-3 text-sm">
                <div className="rounded-xl bg-surface-2 p-3">
                  <dt className="text-xs text-fg-muted">Returns (30 days)</dt>
                  <dd className={clsx("mt-0.5 text-xl font-semibold tabular", (ret.customer?.returns_30d ?? 0) > 3 && "text-rose-600 dark:text-rose-400")}>
                    {ret.customer?.returns_30d ?? "—"}
                  </dd>
                </div>
                <div className="rounded-xl bg-surface-2 p-3">
                  <dt className="text-xs text-fg-muted">Flag</dt>
                  <dd className="mt-1">
                    {ret.customer?.is_flagged ? (
                      <Badge tone="danger" icon={<Flag className="size-3" aria-hidden />}>
                        Flagged
                      </Badge>
                    ) : (
                      <Badge tone="success">Clear</Badge>
                    )}
                  </dd>
                </div>
              </dl>
              {ret.finance_review_reason ? (
                <p className="text-sm text-fg-muted">
                  Routed to review: <span className="font-medium text-fg">{FINANCE_REASON_LABEL[ret.finance_review_reason]}</span>
                </p>
              ) : null}
            </div>
          </Card>

          <Card>
            <CardHeader title="Amount" />
            <dl className="space-y-2.5 p-5 text-sm">
              <div className="flex justify-between">
                <dt className="text-fg-muted">Requested (unit prices)</dt>
                <dd className="tabular">{formatPaise(ret.requested_refund_paise)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-fg-muted">Grade / reject deductions</dt>
                <dd className={clsx("tabular", deductions > 0 && "text-rose-600 dark:text-rose-400")}>{deductions > 0 ? `−${formatPaise(deductions)}` : formatPaise(0)}</dd>
              </div>
              <div className="flex justify-between border-t border-line pt-2.5 text-base">
                <dt className="font-medium">Refund amount</dt>
                <dd className="font-semibold tabular" data-testid="finance-amount">
                  {formatPaise(amount)}
                </dd>
              </div>
              {amount > FINANCE_THRESHOLD_PAISE ? <p className="text-xs text-fg-subtle">Above the ₹10,000 auto-refund threshold.</p> : null}
            </dl>
          </Card>
        </div>
      </div>

      {/* Approve */}
      <Dialog
        open={approveDlg.isOpen}
        onClose={approveDlg.close}
        busy={approve.isPending}
        title={`Approve refund of ${formatPaise(amount)}?`}
        description={`The refund goes to ${REFUND_METHOD_LABEL[ret.refund?.method ?? ret.refund_method].toLowerCase()}. Clicking twice is safe — this dialog sends one idempotency key.`}
        testId="approve-dialog"
        footer={
          <>
            <Button variant="outline" onClick={approveDlg.close} disabled={approve.isPending}>
              Cancel
            </Button>
            <Button
              variant="success"
              loading={approve.isPending}
              data-testid="confirm-approve"
              onClick={() =>
                approve.mutate(
                  { ret, idempotencyKey: approveDlg.idempotencyKey, note },
                  {
                    onSuccess: ({ ret: next, accepted }) => {
                      approveDlg.close();
                      after202(accepted, next, `Refund of ${formatPaise(amount)} approved and paid`);
                    },
                    onError: (e) => {
                      if (shouldCloseDialogOnError(e)) approveDlg.close();
                    },
                  },
                )
              }
            >
              Approve
            </Button>
          </>
        }
      >
        <TextArea label="Note (optional)" value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} data-testid="approve-note" />
      </Dialog>

      {/* Reject */}
      <Dialog
        open={rejectDlg.isOpen}
        onClose={rejectDlg.close}
        busy={reject.isPending}
        title="Reject this refund?"
        description="Accepted units will be marked rejected and the customer is notified. A note is required for the audit trail."
        testId="reject-dialog"
        footer={
          <>
            <Button variant="outline" onClick={rejectDlg.close} disabled={reject.isPending}>
              Cancel
            </Button>
            <Button
              variant="danger"
              loading={reject.isPending}
              disabled={rejectNote.trim().length < 3}
              data-testid="confirm-reject"
              onClick={() =>
                reject.mutate(
                  { ret, idempotencyKey: rejectDlg.idempotencyKey, note: rejectNote },
                  {
                    onSuccess: () => {
                      rejectDlg.close();
                      toast.success("Refund rejected");
                    },
                    onError: (e) => {
                      if (shouldCloseDialogOnError(e)) rejectDlg.close();
                    },
                  },
                )
              }
            >
              Reject refund
            </Button>
          </>
        }
      >
        <TextArea
          label="Reason (required)"
          value={rejectNote}
          onChange={(e) => setRejectNote(e.target.value)}
          maxLength={500}
          required
          hint="Visible to staff only."
          data-testid="reject-note"
        />
      </Dialog>

      {/* Retry */}
      <Dialog
        open={retryDlg.isOpen}
        onClose={retryDlg.close}
        busy={retry.isPending}
        title="Retry refund"
        description="Creates a new refund attempt with a new gateway key. The previous attempt was a definitive decline."
        testId="retry-dialog"
        footer={
          <>
            <Button variant="outline" onClick={retryDlg.close} disabled={retry.isPending}>
              Cancel
            </Button>
            <Button
              loading={retry.isPending}
              data-testid="confirm-retry"
              onClick={() =>
                retry.mutate(
                  { ret, idempotencyKey: retryDlg.idempotencyKey, method: storeCredit ? "STORE_CREDIT" : null },
                  {
                    onSuccess: ({ ret: next, accepted }) => {
                      retryDlg.close();
                      after202(accepted, next, `Refund of ${formatPaise(amount)} paid`);
                    },
                    onError: (e) => {
                      if (shouldCloseDialogOnError(e)) retryDlg.close();
                    },
                  },
                )
              }
            >
              Retry {formatPaise(amount)}
            </Button>
          </>
        }
      >
        <label className="flex cursor-pointer items-start gap-3 rounded-xl border border-line p-4 has-[:checked]:border-brand has-[:checked]:bg-brand-soft/50">
          <input
            type="checkbox"
            className="mt-0.5 size-5 accent-[var(--rr-brand)]"
            checked={storeCredit}
            onChange={(e) => setStoreCredit(e.target.checked)}
            data-testid="retry-store-credit"
          />
          <span>
            <span className="block font-medium">Switch to store credit</span>
            <span className="block text-sm text-fg-muted">Completes instantly without the payment gateway [A-16, A-17].</span>
          </span>
        </label>
      </Dialog>
    </>
  );
}

function AttemptTable({
  attempts,
  onRecheck,
  recheckingId,
  canRecheck,
}: {
  attempts: RefundAttemptDTO[];
  onRecheck: (a: RefundAttemptDTO) => void;
  recheckingId: string | null;
  canRecheck: boolean;
}) {
  if (attempts.length === 0) {
    return (
      <div className="flex items-center gap-2 rounded-xl bg-surface-2 px-4 py-3 text-sm text-fg-muted">
        <CircleHelp className="size-4" aria-hidden /> No refund attempts yet.
      </div>
    );
  }
  return (
    <div className="-mx-5 overflow-x-auto">
      <table className="w-full min-w-[560px] text-sm" data-testid="attempts-table">
        <thead>
          <tr className="border-b border-line text-left text-xs font-semibold tracking-wide text-fg-subtle uppercase">
            <th className="px-5 py-2.5">#</th>
            <th className="px-3 py-2.5">Status</th>
            <th className="px-3 py-2.5">Method</th>
            <th className="px-3 py-2.5">Gateway key</th>
            <th className="px-3 py-2.5">Created</th>
            <th className="px-5 py-2.5 text-right">
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {attempts.map((a) => (
            <tr key={a.id} data-testid="attempt-row" data-status={a.status}>
              <td className="px-5 py-3 font-semibold tabular">{a.attempt_no}</td>
              <td className="px-3 py-3">
                <div className="flex flex-col items-start gap-1">
                  <AttemptStatusBadge status={a.status} />
                  {a.decline_code ? <span className="font-mono text-xs text-rose-600 dark:text-rose-400">{a.decline_code}</span> : null}
                </div>
              </td>
              <td className="px-3 py-3">{REFUND_METHOD_LABEL[a.method]}</td>
              <td className="px-3 py-3 font-mono text-xs text-fg-muted">{a.gateway_idempotency_key}</td>
              <td className="px-3 py-3 text-xs whitespace-nowrap text-fg-muted">
                {formatDateTime(a.created_at)}
                {a.completed_at ? <span className="block">done {formatDateTime(a.completed_at)}</span> : null}
              </td>
              <td className="px-5 py-3 text-right">
                {a.status === "UNKNOWN" && canRecheck ? (
                  <Button
                    size="sm"
                    variant="outline"
                    loading={recheckingId === a.id}
                    onClick={() => onRecheck(a)}
                    icon={<RefreshCw className="size-3.5" />}
                    data-testid="recheck-attempt"
                  >
                    Recheck with gateway
                  </Button>
                ) : null}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
