import { clsx } from "clsx";
import { ArrowLeft, ArrowRight, Check, CheckCircle2, CreditCard, Gift, Package, Printer, Send } from "lucide-react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { useState } from "react";
import { Link, useParams, useSearchParams } from "react-router";
import { toast } from "sonner";
import { hasCode, isApiError, newIdempotencyKey } from "../../api/client";
import { useCreateReturn, useOrder } from "../../api/hooks/customer";
import type { OrderUnit, RefundMethod, ReturnDTO, ReturnReason } from "../../api/types";
import { ErrorState } from "../../components/ErrorState";
import { PageHeader } from "../../components/PageHeader";
import { Badge } from "../../components/ui/Badge";
import { Button, ButtonLink } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { SelectField, TextArea } from "../../components/ui/Field";
import { SkeletonCards } from "../../components/ui/Skeleton";
import { formatCalendarDate, formatPaise } from "../../lib/format";
import { IN_PROGRESS_MESSAGE, toastError } from "../../lib/notify";
import { REFUND_METHOD_LABEL, RETURN_REASONS, RETURN_REASON_LABEL } from "../../lib/labels";
import { isSelectable, unitKey } from "../../lib/units";

const STEPS = ["Items", "Reasons", "Refund", "Review"] as const;

interface ReasonDraft {
  reason: ReturnReason | "";
  note: string;
}

interface IneligibleItem {
  message?: string;
  order_item_id?: number;
  unit_index?: number;
}

function readIneligible(details: Record<string, unknown>): IneligibleItem[] {
  const items = details.items;
  return Array.isArray(items) ? (items.filter((x) => typeof x === "object" && x !== null) as IneligibleItem[]) : [];
}

function Stepper({ step }: { step: number }) {
  return (
    <ol className="mb-6 flex items-center gap-2" aria-label="Return steps">
      {STEPS.map((s, i) => {
        const done = i < step;
        const current = i === step;
        return (
          <li key={s} className="flex flex-1 items-center gap-2" aria-current={current ? "step" : undefined}>
            <span
              className={clsx(
                "grid size-7 shrink-0 place-items-center rounded-full text-xs font-semibold transition-colors",
                done && "bg-brand text-brand-fg",
                current && "bg-brand-soft text-brand-soft-fg ring-2 ring-brand",
                !done && !current && "bg-surface-2 text-fg-subtle",
              )}
            >
              {done ? <Check className="size-3.5" strokeWidth={3} /> : i + 1}
            </span>
            <span className={clsx("hidden text-sm font-medium sm:inline", current ? "text-fg" : "text-fg-muted")}>{s}</span>
            {i < STEPS.length - 1 ? (
              <span className="h-0.5 flex-1 overflow-hidden rounded-full bg-line" aria-hidden>
                <motion.span className="block h-full bg-brand" initial={false} animate={{ width: done ? "100%" : "0%" }} transition={{ duration: 0.35 }} />
              </span>
            ) : null}
          </li>
        );
      })}
    </ol>
  );
}

export function ReturnWizard() {
  const { orderNumber = "" } = useParams();
  const [params] = useSearchParams();
  const order = useOrder(orderNumber);
  const create = useCreateReturn();
  const reduce = useReducedMotion();

  const [step, setStep] = useState(0);
  const [selected, setSelected] = useState<Set<string>>(() => {
    const u = params.get("unit");
    return new Set(u ? [u] : []);
  });
  const [reasons, setReasons] = useState<Record<string, ReasonDraft>>({});
  const [method, setMethod] = useState<RefundMethod>("ORIGINAL_PAYMENT");
  const [idemKey, setIdemKey] = useState("");
  const [result, setResult] = useState<ReturnDTO | null>(null);
  const [ineligible, setIneligible] = useState<IneligibleItem[]>([]);

  if (order.isPending) return <SkeletonCards count={3} />;
  if (order.error) return <ErrorState error={order.error} onRetry={() => void order.refetch()} />;

  const units = order.data.units;
  const selectable = units.filter(isSelectable);
  const chosen: OrderUnit[] = selectable.filter((u) => selected.has(unitKey(u)));
  const estimate = chosen.reduce((sum, u) => sum + u.price_paise, 0);
  const reasonsComplete = chosen.every((u) => (reasons[unitKey(u)]?.reason ?? "") !== "");

  function toggle(key: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  function setReason(key: string, patch: Partial<ReasonDraft>) {
    setReasons((prev) => ({ ...prev, [key]: { reason: prev[key]?.reason ?? "", note: prev[key]?.note ?? "", ...patch } }));
  }

  function goTo(n: number) {
    // One Idempotency-Key per submission: minted when the review step opens, reused for every resend from it.
    if (n === 3 && step !== 3) setIdemKey(newIdempotencyKey());
    setIneligible([]);
    setStep(n);
    window.scrollTo({ top: 0, behavior: reduce ? "auto" : "smooth" });
  }

  function submit() {
    if (create.isPending || !idemKey) return;
    create.mutate(
      {
        idempotencyKey: idemKey,
        body: {
          order_number: order.data?.order_number ?? orderNumber,
          refund_method: method,
          items: chosen.map((u) => {
            const r = reasons[unitKey(u)];
            const note = r?.note.trim();
            return {
              order_item_id: u.order_item_id,
              unit_index: u.unit_index,
              reason: (r?.reason || "OTHER"),
              ...(note ? { note } : {}),
            };
          }),
        },
      },
      {
        onSuccess: (ret) => {
          setResult(ret);
          toast.success(`Return ${ret.rma_number} created`);
        },
        onError: (err) => {
          if (hasCode(err, "REQUEST_IN_PROGRESS")) {
            toast.info(IN_PROGRESS_MESSAGE);
            return;
          }
          if (hasCode(err, "ITEM_NOT_ELIGIBLE")) {
            setIneligible(readIneligible(err.details));
            void order.refetch();
          }
          if (hasCode(err, "UNIT_ALREADY_IN_RETURN", "IDEMPOTENCY_KEY_REUSED")) {
            void order.refetch();
            setIdemKey(newIdempotencyKey());
          }
          toastError(err);
        },
      },
    );
  }

  if (result) return <Success ret={result} />;

  const slide = reduce
    ? {}
    : { initial: { opacity: 0, x: 24 }, animate: { opacity: 1, x: 0 }, exit: { opacity: 0, x: -24 }, transition: { duration: 0.2 } };

  return (
    <>
      <PageHeader
        back={{ to: `/portal/orders/${encodeURIComponent(orderNumber)}`, label: `Order ${orderNumber}` }}
        eyebrow="New return"
        title="Start a return"
      />
      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div className="min-w-0">
          <Stepper step={step} />
          <AnimatePresence mode="wait">
            <motion.div key={step} {...slide}>
              {step === 0 ? (
                <Card className="overflow-hidden">
                  <div className="border-b border-line px-5 py-4">
                    <h2 className="font-semibold">Which items are you returning?</h2>
                    <p className="text-sm text-fg-muted">Only items still inside their return window can be selected.</p>
                  </div>
                  <ul className="divide-y divide-line" data-testid="wizard-units">
                    {units.map((u) => {
                      const key = unitKey(u);
                      const ok = isSelectable(u);
                      const checked = selected.has(key) && ok;
                      return (
                        <li key={key}>
                          <label
                            className={clsx(
                              "flex gap-4 px-5 py-4 transition-colors",
                              ok ? "cursor-pointer hover:bg-surface-2/60" : "cursor-not-allowed",
                              checked && "bg-brand-soft/50",
                            )}
                          >
                            <input
                              type="checkbox"
                              className="mt-1 size-5 shrink-0 accent-[var(--rr-brand)]"
                              checked={checked}
                              disabled={!ok}
                              onChange={() => toggle(key)}
                              data-testid="wizard-unit-checkbox"
                              aria-describedby={`elig-${key}`}
                            />
                            <div className="min-w-0 flex-1">
                              <div className="flex flex-wrap items-start justify-between gap-2">
                                <p className={clsx("font-medium", ok ? "text-fg" : "text-fg-muted")}>
                                  {u.product_name} <span className="text-xs font-normal text-fg-subtle">unit {u.unit_index}</span>
                                </p>
                                <span className="font-semibold tabular">{formatPaise(u.price_paise)}</span>
                              </div>
                              <p className="text-xs text-fg-muted">{u.category_name}</p>
                              <p id={`elig-${key}`} className={clsx("mt-1.5 text-sm", ok ? "text-emerald-700 dark:text-emerald-400" : "text-fg-muted")}>
                                {ok && u.eligibility.details.last_eligible_date ? (
                                  <span className="font-medium">Returnable until {formatCalendarDate(u.eligibility.details.last_eligible_date)}. </span>
                                ) : null}
                                {u.eligibility.message}
                              </p>
                            </div>
                          </label>
                        </li>
                      );
                    })}
                  </ul>
                </Card>
              ) : null}

              {step === 1 ? (
                <div className="space-y-3">
                  {chosen.map((u) => {
                    const key = unitKey(u);
                    const r = reasons[key] ?? { reason: "", note: "" };
                    return (
                      <Card key={key} className="p-5" data-testid="wizard-reason-card">
                        <div className="mb-4 flex items-center gap-3">
                          <div className="grid size-10 place-items-center rounded-xl bg-surface-2 text-fg-muted">
                            <Package className="size-5" aria-hidden />
                          </div>
                          <div className="min-w-0">
                            <p className="truncate font-medium">{u.product_name}</p>
                            <p className="text-xs text-fg-muted">unit {u.unit_index} · {formatPaise(u.price_paise)}</p>
                          </div>
                        </div>
                        <div className="grid gap-4 sm:grid-cols-2">
                          <SelectField
                            label="Reason"
                            value={r.reason}
                            onChange={(e) => setReason(key, { reason: e.target.value as ReturnReason })}
                            required
                            data-testid="wizard-reason"
                          >
                            <option value="" disabled>
                              Choose a reason…
                            </option>
                            {RETURN_REASONS.map((rr) => (
                              <option key={rr} value={rr}>
                                {RETURN_REASON_LABEL[rr]}
                              </option>
                            ))}
                          </SelectField>
                          <TextArea
                            label="Anything we should know? (optional)"
                            value={r.note}
                            maxLength={500}
                            rows={2}
                            onChange={(e) => setReason(key, { note: e.target.value })}
                            placeholder="e.g. the zip is broken"
                          />
                        </div>
                      </Card>
                    );
                  })}
                </div>
              ) : null}

              {step === 2 ? (
                <Card className="p-5">
                  <fieldset>
                    <legend className="font-semibold">How would you like your refund?</legend>
                    <p className="text-sm text-fg-muted">You'll get the refund once we've inspected your items.</p>
                    <div className="mt-4 grid gap-3 sm:grid-cols-2">
                      {(
                        [
                          { v: "ORIGINAL_PAYMENT", icon: <CreditCard className="size-5" />, body: `Back to your ${order.data.payment_method.toLowerCase()} — usually 3–5 business days after approval.` },
                          { v: "STORE_CREDIT", icon: <Gift className="size-5" />, body: "Instant credit to your store wallet once approved." },
                        ] as const
                      ).map((o) => (
                        <label
                          key={o.v}
                          className={clsx(
                            "flex cursor-pointer gap-3 rounded-2xl border p-4 transition-colors has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-ring",
                            method === o.v ? "border-brand bg-brand-soft/60 ring-1 ring-brand" : "border-line hover:bg-surface-2",
                          )}
                        >
                          <input
                            type="radio"
                            name="refund_method"
                            value={o.v}
                            checked={method === o.v}
                            onChange={() => setMethod(o.v)}
                            className="sr-only"
                            data-testid={`method-${o.v}`}
                          />
                          <span className={clsx("grid size-10 shrink-0 place-items-center rounded-xl", method === o.v ? "bg-brand text-brand-fg" : "bg-surface-2 text-fg-muted")}>
                            {o.icon}
                          </span>
                          <span>
                            <span className="block font-medium">{REFUND_METHOD_LABEL[o.v]}</span>
                            <span className="block text-sm text-fg-muted">{o.body}</span>
                          </span>
                        </label>
                      ))}
                    </div>
                  </fieldset>
                </Card>
              ) : null}

              {step === 3 ? (
                <Card className="overflow-hidden" data-testid="wizard-review">
                  <div className="border-b border-line px-5 py-4">
                    <h2 className="font-semibold">Review your return</h2>
                    <p className="text-sm text-fg-muted">Check everything looks right, then submit.</p>
                  </div>
                  <ul className="divide-y divide-line">
                    {chosen.map((u) => {
                      const r = reasons[unitKey(u)];
                      return (
                        <li key={unitKey(u)} className="flex items-start justify-between gap-3 px-5 py-3.5">
                          <div className="min-w-0">
                            <p className="font-medium">{u.product_name}</p>
                            <p className="text-sm text-fg-muted">
                              {r?.reason ? RETURN_REASON_LABEL[r.reason] : "—"}
                              {r?.note.trim() ? ` · “${r.note.trim()}”` : ""}
                            </p>
                          </div>
                          <span className="font-semibold tabular">{formatPaise(u.price_paise)}</span>
                        </li>
                      );
                    })}
                  </ul>
                  <div className="space-y-2 border-t border-line bg-surface-2/50 px-5 py-4 text-sm">
                    <div className="flex justify-between">
                      <span className="text-fg-muted">Refund to</span>
                      <span className="font-medium">{REFUND_METHOD_LABEL[method]}</span>
                    </div>
                    <div className="flex justify-between text-base">
                      <span className="font-medium">Estimated refund</span>
                      <span className="font-semibold tabular" data-testid="wizard-estimate">
                        {formatPaise(estimate)}
                      </span>
                    </div>
                    <p className="text-xs text-fg-subtle">
                      Final amount is confirmed after inspection. Items graded C (heavy wear) refund 50%. Shipping isn't refunded.
                    </p>
                  </div>
                  {ineligible.length > 0 ? (
                    <div role="alert" className="border-t border-rose-200 bg-rose-50 px-5 py-4 text-sm text-rose-800 dark:border-rose-900 dark:bg-rose-950/40 dark:text-rose-200">
                      <p className="font-semibold">Some items can no longer be returned:</p>
                      <ul className="mt-1 list-disc pl-5">
                        {ineligible.map((i, idx) => (
                          <li key={idx}>{i.message ?? "Not eligible"}</li>
                        ))}
                      </ul>
                    </div>
                  ) : null}
                </Card>
              ) : null}
            </motion.div>
          </AnimatePresence>

          {/* Actions: sticky on phones, above the bottom nav */}
          <div className="sticky bottom-20 z-10 mt-6 flex gap-3 rounded-2xl border border-line bg-surface/90 p-3 shadow-pop backdrop-blur md:static md:border-0 md:bg-transparent md:p-0 md:shadow-none md:backdrop-blur-none">
            {step > 0 ? (
              <Button variant="outline" size="lg" onClick={() => goTo(step - 1)} disabled={create.isPending} icon={<ArrowLeft className="size-4" />}>
                Back
              </Button>
            ) : null}
            <div className="flex-1" />
            {step < 3 ? (
              <Button
                size="lg"
                onClick={() => goTo(step + 1)}
                disabled={(step === 0 && chosen.length === 0) || (step === 1 && !reasonsComplete)}
                data-testid="wizard-next"
              >
                Continue <ArrowRight className="size-4" />
              </Button>
            ) : (
              <Button
                size="lg"
                onClick={submit}
                loading={create.isPending}
                disabled={chosen.length === 0 || !reasonsComplete}
                data-testid="wizard-submit"
                icon={<Send className="size-4" />}
              >
                Submit return
              </Button>
            )}
          </div>
        </div>

        {/* Summary */}
        <aside className="hidden lg:block">
          <Card className="sticky top-24 p-5">
            <h2 className="text-sm font-semibold">Summary</h2>
            <dl className="mt-3 space-y-2 text-sm">
              <div className="flex justify-between">
                <dt className="text-fg-muted">Items selected</dt>
                <dd className="font-medium tabular">{chosen.length}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-fg-muted">Refund method</dt>
                <dd className="font-medium">{REFUND_METHOD_LABEL[method]}</dd>
              </div>
              <div className="flex justify-between border-t border-line pt-2 text-base">
                <dt className="font-medium">Estimate</dt>
                <dd className="font-semibold tabular">{formatPaise(estimate)}</dd>
              </div>
            </dl>
            {chosen.length === 0 && step === 0 ? <p className="mt-3 text-xs text-fg-subtle">Select at least one item to continue.</p> : null}
            {isApiError(create.error) && create.error.correlationId ? (
              <p className="mt-3 font-mono text-xs text-fg-subtle">Ref: {create.error.correlationId}</p>
            ) : null}
          </Card>
        </aside>
      </div>
    </>
  );
}

function Success({ ret }: { ret: ReturnDTO }) {
  const reduce = useReducedMotion();
  return (
    <div className="mx-auto max-w-lg py-6 text-center" data-testid="return-success">
      <motion.div
        initial={reduce ? false : { scale: 0.4, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ type: "spring", stiffness: 260, damping: 18 }}
        className="mx-auto grid size-20 place-items-center rounded-full bg-emerald-500/15 text-emerald-600 dark:text-emerald-400"
      >
        <CheckCircle2 className="size-11" aria-hidden />
      </motion.div>
      <h1 className="mt-6 text-2xl font-semibold tracking-tight">Your return is approved</h1>
      <p className="mt-2 text-fg-muted">{ret.status_label}</p>
      <div className="mt-6 rounded-2xl border border-line bg-surface p-5 text-left shadow-card">
        <div className="flex items-center justify-between">
          <span className="text-sm text-fg-muted">Return number (RMA)</span>
          <Badge tone="brand">
            <span className="font-mono" data-testid="success-rma">
              {ret.rma_number}
            </span>
          </Badge>
        </div>
        <div className="mt-3 flex items-center justify-between">
          <span className="text-sm text-fg-muted">Estimated refund</span>
          <span className="font-semibold tabular">{formatPaise(ret.requested_refund_paise)}</span>
        </div>
        {ret.label ? (
          <p className="mt-3 text-sm text-fg-muted">
            Print your label and hand the parcel to the courier. Tracking <span className="font-mono text-fg">{ret.label.tracking_number}</span>.
          </p>
        ) : null}
      </div>
      <div className="mt-6 flex flex-col gap-3 sm:flex-row sm:justify-center">
        <ButtonLink to={`/portal/returns/${ret.id}/label`} size="lg" icon={<Printer className="size-4" />} data-testid="success-label">
          View shipping label
        </ButtonLink>
        <ButtonLink to={`/portal/returns/${ret.id}`} variant="outline" size="lg">
          Track return
        </ButtonLink>
      </div>
      <p className="mt-6 text-sm">
        <Link to="/portal" className="text-fg-muted hover:text-fg">
          Back to orders
        </Link>
      </p>
    </div>
  );
}
