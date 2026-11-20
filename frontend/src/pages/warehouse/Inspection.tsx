import { clsx } from "clsx";
import { CheckCheck, ClipboardCheck, Flag, Info, Package, ScanLine, ThumbsDown, ThumbsUp } from "lucide-react";
import { motion, useReducedMotion } from "motion/react";
import { useState } from "react";
import { useNavigate, useParams } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { handleReturnMutationError, shouldCloseDialogOnError } from "../../api/hooks/common";
import { useCompleteInspection, useSaveInspection, useWarehouseReturn, warehouseKeys } from "../../api/hooks/warehouse";
import type { Decision, Grade, InspectionRequest, RejectReason, ReturnDTO, ReturnItemDTO } from "../../api/types";
import { ErrorState } from "../../components/ErrorState";
import { PageHeader } from "../../components/PageHeader";
import { ReturnItems } from "../../components/ReturnItems";
import { ReturnStatusBadge } from "../../components/StatusBadge";
import { Badge } from "../../components/ui/Badge";
import { Button, ButtonLink } from "../../components/ui/Button";
import { Card, CardHeader } from "../../components/ui/Card";
import { Dialog } from "../../components/ui/Dialog";
import { SelectField, TextArea } from "../../components/ui/Field";
import { Segmented } from "../../components/ui/Segmented";
import { SkeletonCards } from "../../components/ui/Skeleton";
import { CountUp } from "../../components/ui/CountUp";
import { FINANCE_THRESHOLD_PAISE, estimateItemRefund, formatPaise } from "../../lib/format";
import { REJECT_REASON_LABEL, RETURN_REASON_LABEL, WAREHOUSE_REJECT_REASONS } from "../../lib/labels";
import { useConfirm } from "../../lib/useConfirm";

interface Draft {
  decision: Decision | null;
  grade: Grade | null;
  rejectReason: RejectReason | null;
  notes: string;
  dirty: boolean;
}

function draftFrom(it: ReturnItemDTO): Draft {
  return {
    decision: it.decision,
    grade: it.grade,
    rejectReason: it.reject_reason,
    notes: "",
    dirty: false,
  };
}

function isDecided(d: Draft | undefined): boolean {
  if (!d) return false;
  if (d.decision === "ACCEPT") return d.grade !== null;
  if (d.decision === "REJECT") return d.rejectReason !== null;
  return false;
}

function toRequest(d: Draft): InspectionRequest {
  const notes = d.notes.trim() ? { notes: d.notes.trim() } : {};
  return d.decision === "ACCEPT"
    ? { decision: "ACCEPT", grade: d.grade ?? "A", ...notes }
    : { decision: "REJECT", reject_reason: d.rejectReason ?? "NOT_IN_PARCEL", ...notes };
}

export function Inspection() {
  const { id = "" } = useParams();
  const { data, isPending, error, refetch } = useWarehouseReturn(id);
  if (isPending) return <SkeletonCards count={3} />;
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />;
  const editable = data.status === "RECEIVED" && (data.allowed_actions.includes("INSPECT") || data.allowed_actions.includes("COMPLETE_INSPECTION"));
  return editable ? <InspectionForm key={data.id} ret={data} /> : <ReadOnly ret={data} />;
}

function Header({ ret }: { ret: ReturnDTO }) {
  return (
    <PageHeader
      back={{ to: "/warehouse/queue?tab=RECEIVED", label: "Returns queue" }}
      eyebrow="Inspection"
      title={
        <span className="flex flex-wrap items-center gap-3">
          <span className="font-mono">{ret.rma_number}</span>
          <ReturnStatusBadge status={ret.status} />
        </span>
      }
      description={
        <span className="flex flex-wrap items-center gap-2">
          {ret.customer?.name ?? "Customer"} · Order {ret.order_number}
          {ret.customer?.is_flagged ? (
            <Badge tone="danger" icon={<Flag className="size-3" aria-hidden />}>
              Flagged
            </Badge>
          ) : null}
        </span>
      }
    />
  );
}

function ReadOnly({ ret }: { ret: ReturnDTO }) {
  const notYet = ret.status === "APPROVED" || ret.status === "IN_TRANSIT";
  return (
    <>
      <Header ret={ret} />
      {notYet ? (
        <div className="mb-6 flex flex-col gap-3 rounded-2xl border border-line bg-surface p-5 sm:flex-row sm:items-center">
          <Info className="size-5 shrink-0 text-brand" aria-hidden />
          <p className="flex-1 text-sm">This parcel hasn't been received yet. Scan its label at the scan station to start inspection.</p>
          <ButtonLink to="/warehouse" icon={<ScanLine className="size-4" />}>
            Scan station
          </ButtonLink>
        </div>
      ) : (
        <div className="mb-6 flex gap-3 rounded-2xl border border-line bg-surface p-5 text-sm">
          <ClipboardCheck className="size-5 shrink-0 text-emerald-500" aria-hidden />
          <p>
            Inspection is complete. <span className="text-fg-muted">{ret.status_label}</span>
          </p>
        </div>
      )}
      <Card>
        <CardHeader title="Items" />
        <div className="p-5">
          <ReturnItems ret={ret} showSku />
        </div>
      </Card>
    </>
  );
}

function routingFor(total: number, flagged: boolean): { label: string; tone: "danger" | "warning" | "success" } {
  if (total === 0) return { label: "Rejected — no refund", tone: "danger" };
  if (total > FINANCE_THRESHOLD_PAISE || flagged) return { label: "Finance review", tone: "warning" };
  return { label: "Automatic refund", tone: "success" };
}

function InspectionForm({ ret }: { ret: ReturnDTO }) {
  const reduce = useReducedMotion();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const save = useSaveInspection();
  const complete = useCompleteInspection();
  const confirm = useConfirm();
  const units = ret.items.filter((it) => it.status !== "CANCELLED");
  const [drafts, setDrafts] = useState<Record<string, Draft>>(() => Object.fromEntries(units.map((it) => [it.id, draftFrom(it)])));
  const [busy, setBusy] = useState(false);

  const decidedCount = units.filter((u) => isDecided(drafts[u.id])).length;
  const allDecided = decidedCount === units.length && units.length > 0;
  const total = units.reduce((s, u) => {
    const d = drafts[u.id];
    return s + estimateItemRefund(u.unit_price_paise, d?.decision ?? null, d?.grade ?? null);
  }, 0);
  const accepted = units.filter((u) => drafts[u.id]?.decision === "ACCEPT").length;
  const rejected = units.filter((u) => drafts[u.id]?.decision === "REJECT").length;
  const routing = routingFor(total, ret.customer?.is_flagged ?? false);

  function patch(itemId: string, p: Partial<Draft>) {
    setDrafts((prev) => {
      const cur = prev[itemId] ?? { decision: null, grade: null, rejectReason: null, notes: "", dirty: false };
      return { ...prev, [itemId]: { ...cur, ...p, dirty: true } };
    });
  }

  function acceptAll(grade: Grade) {
    setDrafts((prev) => {
      const next = { ...prev };
      for (const u of units) {
        const cur = next[u.id];
        if (!cur || cur.decision === null) {
          next[u.id] = { decision: "ACCEPT", grade, rejectReason: null, notes: cur?.notes ?? "", dirty: true };
        }
      }
      return next;
    });
  }

  async function run() {
    if (busy) return;
    setBusy(true);
    let current: ReturnDTO = ret;
    try {
      // Persist each unit decision (T7/T8), chaining the version returned by each call (If-Match).
      for (const u of units) {
        const d = drafts[u.id];
        if (!d || !isDecided(d)) continue;
        const serverItem = current.items.find((i) => i.id === u.id);
        const needsSave = d.dirty || serverItem?.status === "RECEIVED";
        if (!needsSave) continue;
        current = await save.mutateAsync({ returnId: ret.id, itemId: u.id, version: current.version, body: toRequest(d) });
        setDrafts((prev) => {
          const cur = prev[u.id];
          return cur ? { ...prev, [u.id]: { ...cur, dirty: false } } : prev;
        });
      }
    } catch (err) {
      setBusy(false);
      if (shouldCloseDialogOnError(err)) confirm.close();
      handleReturnMutationError(err, qc, [warehouseKeys.ret(ret.id)]);
      return;
    }
    // Complete (T9) → ROUTE (T10–T12) in one transaction server-side. Same Idempotency-Key for every resend.
    complete.mutate(
      { ret: current, idempotencyKey: confirm.idempotencyKey },
      {
        onSuccess: (done) => {
          confirm.close();
          const amt = formatPaise(done.approved_refund_paise ?? 0);
          switch (done.status) {
            case "REFUNDED":
              toast.success(`Auto-refunded ${amt}`, { description: `${done.rma_number} is closed.` });
              break;
            case "REFUND_PENDING":
              toast.success(`Refund of ${amt} initiated`, { description: "Auto-refund is with the payment gateway." });
              break;
            case "PENDING_FINANCE":
              toast.info(`Sent to finance review (${amt})`, { description: "Over ₹10,000 or flagged customer." });
              break;
            case "REJECTED":
              toast.warning("Return rejected", { description: "All units were rejected; no refund." });
              break;
            default:
              toast.success(done.status_label);
          }
          void navigate("/warehouse/queue?tab=RECEIVED");
        },
        onError: (e) => {
          if (shouldCloseDialogOnError(e)) confirm.close();
        },
        onSettled: () => setBusy(false),
      },
    );
  }

  return (
    <>
      <Header ret={ret} />

      <div className="grid gap-6 lg:grid-cols-[1fr_340px]">
        <div className="space-y-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p className="text-sm text-fg-muted">
              <span className="font-semibold text-fg tabular">{decidedCount}</span> of {units.length} units decided
            </p>
            <div className="flex gap-2">
              <Button size="sm" variant="outline" onClick={() => acceptAll("A")} icon={<CheckCheck className="size-4" />}>
                Accept rest as A
              </Button>
            </div>
          </div>

          {units.map((u, i) => {
            const d = drafts[u.id];
            const decided = isDecided(d);
            const est = estimateItemRefund(u.unit_price_paise, d?.decision ?? null, d?.grade ?? null);
            return (
              <motion.div
                key={u.id}
                initial={reduce ? false : { opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: i * 0.05 }}
              >
                <Card
                  className={clsx(
                    "p-5 transition-colors",
                    decided && d?.decision === "ACCEPT" && "border-emerald-300 dark:border-emerald-800",
                    decided && d?.decision === "REJECT" && "border-rose-300 dark:border-rose-800",
                  )}
                  data-testid="inspection-unit"
                >
                  <div className="flex items-start gap-3">
                    <div className="grid size-11 shrink-0 place-items-center rounded-xl bg-surface-2 text-fg-muted">
                      <Package className="size-5" aria-hidden />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-start justify-between gap-2">
                        <div className="min-w-0">
                          <p className="font-semibold">
                            {u.product_name} <span className="text-xs font-normal text-fg-subtle">unit {u.unit_index}</span>
                          </p>
                          <p className="text-xs text-fg-muted">
                            {u.category_name} · <span className="font-mono">{u.sku}</span>
                          </p>
                        </div>
                        <p className="font-semibold tabular">{formatPaise(u.unit_price_paise)}</p>
                      </div>
                      <p className="mt-2 text-sm text-fg-muted">
                        Customer says: <span className="text-fg">{RETURN_REASON_LABEL[u.reason]}</span>
                        {u.reason_note ? <span className="italic"> — “{u.reason_note}”</span> : null}
                      </p>
                    </div>
                  </div>

                  <div className="mt-4 grid gap-4 md:grid-cols-2">
                    <div>
                      <p className="mb-1.5 text-sm font-medium">Decision</p>
                      <Segmented<Decision>
                        name={`decision-${u.id}`}
                        label={`Decision for ${u.product_name}`}
                        size="lg"
                        value={d?.decision ?? null}
                        onChange={(v) => patch(u.id, v === "ACCEPT" ? { decision: v, rejectReason: null } : { decision: v, grade: null })}
                        options={[
                          { value: "ACCEPT", label: <><ThumbsUp className="size-4" aria-hidden /> Accept</>, tone: "success" },
                          { value: "REJECT", label: <><ThumbsDown className="size-4" aria-hidden /> Reject</>, tone: "danger" },
                        ]}
                        testId="decision"
                      />
                    </div>
                    {d?.decision === "ACCEPT" ? (
                      <div>
                        <p className="mb-1.5 text-sm font-medium">Condition grade</p>
                        <Segmented<Grade>
                          name={`grade-${u.id}`}
                          label={`Grade for ${u.product_name}`}
                          size="lg"
                          value={d.grade}
                          onChange={(v) => patch(u.id, { grade: v })}
                          options={[
                            { value: "A", label: "A", hint: "Like new — full refund" },
                            { value: "B", label: "B", hint: "Minor wear — full refund" },
                            { value: "C", label: "C", hint: "Heavy wear — 50% refund", tone: "warning" },
                          ]}
                          testId="grade"
                        />
                        <p className={clsx("mt-1.5 text-xs", d.grade === "C" ? "font-medium text-amber-700 dark:text-amber-400" : "text-fg-muted")}>
                          Grade C refunds 50%. A and B refund in full.
                        </p>
                      </div>
                    ) : d?.decision === "REJECT" ? (
                      <SelectField
                        label="Reject reason"
                        value={d.rejectReason ?? ""}
                        onChange={(e) => patch(u.id, { rejectReason: e.target.value as RejectReason })}
                        data-testid="reject-reason"
                      >
                        <option value="" disabled>
                          Choose a reason…
                        </option>
                        {WAREHOUSE_REJECT_REASONS.map((r) => (
                          <option key={r} value={r}>
                            {REJECT_REASON_LABEL[r]}
                          </option>
                        ))}
                      </SelectField>
                    ) : (
                      <div className="hidden items-center text-sm text-fg-subtle md:flex">Choose accept or reject.</div>
                    )}
                  </div>

                  {d?.decision ? (
                    <div className="mt-4 grid gap-4 md:grid-cols-[1fr_auto] md:items-end">
                      <TextArea
                        label="Notes (optional)"
                        rows={2}
                        maxLength={500}
                        value={d.notes}
                        onChange={(e) => patch(u.id, { notes: e.target.value })}
                        placeholder="Scuffs on the left side, tags attached…"
                      />
                      <div className="rounded-xl bg-surface-2 px-4 py-2.5 text-right">
                        <p className="text-xs text-fg-muted">Unit refund</p>
                        <p className={clsx("text-lg font-semibold tabular", est === 0 && "text-fg-muted")} data-testid="unit-estimate">
                          {formatPaise(est)}
                        </p>
                      </div>
                    </div>
                  ) : null}
                </Card>
              </motion.div>
            );
          })}
        </div>

        <aside>
          <Card className="sticky top-20 p-5 lg:top-8">
            <h2 className="text-sm font-semibold">Refund estimate</h2>
            <p className="mt-2 text-3xl font-semibold tracking-tight" data-testid="inspection-total">
              <CountUp value={total} format={(n) => formatPaise(Math.round(n))} duration={0.4} />
            </p>
            <p className="mt-1 text-xs text-fg-muted">of {formatPaise(units.reduce((s, u) => s + u.unit_price_paise, 0))} requested</p>
            <dl className="mt-4 space-y-2 border-t border-line pt-4 text-sm">
              <div className="flex justify-between">
                <dt className="text-fg-muted">Accepted</dt>
                <dd className="font-medium tabular">{accepted}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-fg-muted">Rejected</dt>
                <dd className="font-medium tabular">{rejected}</dd>
              </div>
              <div className="flex items-center justify-between gap-2">
                <dt className="text-fg-muted">Expected routing</dt>
                <dd>
                  <Badge tone={allDecided ? routing.tone : "neutral"}>{allDecided ? routing.label : "—"}</Badge>
                </dd>
              </div>
            </dl>
            <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-line" aria-hidden>
              <motion.div
                className="h-full rounded-full bg-brand"
                animate={{ width: `${units.length ? (decidedCount / units.length) * 100 : 0}%` }}
                transition={{ duration: 0.3 }}
              />
            </div>
            <Button
              size="lg"
              className="mt-5 w-full"
              disabled={!allDecided || busy}
              onClick={() => confirm.open()}
              icon={<ClipboardCheck className="size-4" />}
              data-testid="complete-inspection"
            >
              Complete inspection
            </Button>
            {!allDecided ? <p className="mt-2 text-center text-xs text-fg-subtle">Decide every unit to continue.</p> : null}
          </Card>
        </aside>
      </div>

      <Dialog
        open={confirm.isOpen}
        onClose={confirm.close}
        busy={busy}
        title="Complete inspection?"
        description="Decisions are saved and the return is routed. This can't be undone from the warehouse."
        testId="complete-dialog"
        footer={
          <>
            <Button variant="outline" onClick={confirm.close} disabled={busy}>
              Keep editing
            </Button>
            <Button onClick={() => void run()} loading={busy} data-testid="confirm-complete">
              Complete &amp; route
            </Button>
          </>
        }
      >
        <dl className="space-y-2 text-sm">
          <div className="flex justify-between">
            <dt className="text-fg-muted">Units accepted / rejected</dt>
            <dd className="font-medium">
              {accepted} / {rejected}
            </dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-fg-muted">Refund estimate</dt>
            <dd className="font-semibold tabular">{formatPaise(total)}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-fg-muted">Expected routing</dt>
            <dd>
              <Badge tone={routing.tone}>{routing.label}</Badge>
            </dd>
          </div>
        </dl>
      </Dialog>
    </>
  );
}
