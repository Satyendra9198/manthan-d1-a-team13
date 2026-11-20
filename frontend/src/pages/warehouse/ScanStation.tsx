import { clsx } from "clsx";
import { AlertOctagon, AlertTriangle, ArrowRight, Camera, CheckCircle2, CornerDownLeft, History, ScanLine, SearchX } from "lucide-react";
import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { useRef, useState } from "react";
import type { FormEvent, ReactNode } from "react";
import { Link } from "react-router";
import { isApiError } from "../../api/client";
import { useScan } from "../../api/hooks/warehouse";
import type { ReturnDTO } from "../../api/types";
import { PageHeader } from "../../components/PageHeader";
import { ReturnStatusBadge } from "../../components/StatusBadge";
import { Button, ButtonLink } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { formatPaise } from "../../lib/format";
import { cameraScanSupported } from "../../lib/barcode";
import { CameraScanner } from "./CameraScanner";

type Kind = "success" | "warning" | "error";

interface ScanOutcome {
  id: number;
  kind: Kind;
  title: string;
  message: string;
  token: string;
  ret: ReturnDTO | null;
  code: string | null;
  correlationId: string | null;
  at: number;
}

const ERROR_COPY: Record<string, { title: string; kind: Kind }> = {
  RETURN_CANCELLED: { title: "Do not accept parcel", kind: "error" },
  LABEL_VOID: { title: "Label void — do not accept", kind: "error" },
  LABEL_EXPIRED: { title: "Label expired — do not accept", kind: "error" },
  NOT_FOUND: { title: "No return found", kind: "error" },
  LABEL_NOT_FOUND: { title: "No return found", kind: "error" },
  ILLEGAL_TRANSITION: { title: "Can't receive this return", kind: "error" },
};

function beep(kind: Kind) {
  try {
    const Ctx = window.AudioContext;
    const ctx = new Ctx();
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.frequency.value = kind === "success" ? 880 : kind === "warning" ? 620 : 220;
    gain.gain.value = 0.06;
    osc.connect(gain).connect(ctx.destination);
    osc.start();
    osc.stop(ctx.currentTime + (kind === "error" ? 0.35 : 0.12));
    osc.onended = () => void ctx.close();
  } catch {
    /* audio is a nicety */
  }
}

const KIND_STYLE: Record<Kind, { ring: string; bg: string; icon: ReactNode; iconBg: string }> = {
  success: {
    ring: "border-emerald-300 dark:border-emerald-800",
    bg: "bg-emerald-50 dark:bg-emerald-950/40",
    iconBg: "bg-emerald-500 text-white",
    icon: <CheckCircle2 className="size-8" />,
  },
  warning: {
    ring: "border-amber-300 dark:border-amber-800",
    bg: "bg-amber-50 dark:bg-amber-950/40",
    iconBg: "bg-amber-500 text-amber-950",
    icon: <AlertTriangle className="size-8" />,
  },
  error: {
    ring: "border-rose-300 dark:border-rose-800",
    bg: "bg-rose-50 dark:bg-rose-950/40",
    iconBg: "bg-rose-600 text-white",
    icon: <AlertOctagon className="size-8" />,
  },
};

export function ScanStation() {
  const scan = useScan();
  const reduce = useReducedMotion();
  const inputRef = useRef<HTMLInputElement>(null);
  const seq = useRef(0);
  const [value, setValue] = useState("");
  const [result, setResult] = useState<ScanOutcome | null>(null);
  const [history, setHistory] = useState<ScanOutcome[]>([]);
  const [camera, setCamera] = useState(false);
  const [cameraOk] = useState(cameraScanSupported);

  function record(o: Omit<ScanOutcome, "id" | "at">) {
    seq.current += 1;
    const full: ScanOutcome = { ...o, id: seq.current, at: Date.now() };
    setResult(full);
    setHistory((h) => [full, ...h].slice(0, 12));
    beep(full.kind);
  }

  function submitToken(raw: string) {
    const token = raw.trim();
    if (!token || scan.isPending) return;
    setValue("");
    scan.mutate(token, {
      onSuccess: (res) =>
        record({
          kind: res.already_received ? "warning" : "success",
          title: res.already_received ? "Already received" : "Parcel received",
          message: res.message,
          token,
          ret: res.return,
          code: null,
          correlationId: null,
        }),
      onError: (err) => {
        const code = isApiError(err) ? err.code : "NETWORK_ERROR";
        const copy = ERROR_COPY[code] ?? { title: "Scan failed", kind: "error" as const };
        record({
          kind: copy.kind,
          title: copy.title,
          message: isApiError(err) ? err.message : "Scan failed. Try again.",
          token,
          ret: null,
          code,
          correlationId: isApiError(err) ? err.correlationId : null,
        });
      },
      onSettled: () => inputRef.current?.focus(),
    });
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    submitToken(value);
  }

  const style = result ? KIND_STYLE[result.kind] : null;

  return (
    <>
      <PageHeader
        eyebrow="Warehouse"
        title="Scan station"
        description="Scan the QR on the parcel label, or type the RMA number and press Enter."
        actions={
          <ButtonLink to="/warehouse/queue" variant="outline">
            Returns queue
          </ButtonLink>
        }
      />

      <div className="grid gap-6 xl:grid-cols-[1fr_340px]">
        <div className="space-y-6">
          <Card className="p-5 sm:p-6">
            <form onSubmit={onSubmit} className="flex flex-col gap-3 sm:flex-row" role="search" aria-label="Scan parcel">
              <label htmlFor="scan-input" className="sr-only">
                QR token or RMA number
              </label>
              <div className="relative flex-1">
                <ScanLine className="pointer-events-none absolute top-1/2 left-4 size-6 -translate-y-1/2 text-fg-subtle" aria-hidden />
                <input
                  ref={inputRef}
                  id="scan-input"
                  autoFocus
                  autoComplete="off"
                  autoCorrect="off"
                  autoCapitalize="characters"
                  spellCheck={false}
                  enterKeyHint="go"
                  value={value}
                  onChange={(e) => setValue(e.target.value)}
                  placeholder="Scan or type RMA…"
                  className="h-16 w-full rounded-2xl border-2 border-line-strong bg-surface pr-4 pl-14 font-mono text-xl tracking-wide text-fg placeholder:font-sans placeholder:text-base placeholder:tracking-normal placeholder:text-fg-subtle focus:border-brand focus:ring-4 focus:ring-brand/15 focus:outline-none sm:text-2xl"
                  data-testid="scan-input"
                />
              </div>
              <div className="flex gap-2">
                <Button type="submit" size="xl" loading={scan.isPending} disabled={!value.trim()} className="flex-1 sm:flex-none" data-testid="scan-submit">
                  <CornerDownLeft className="size-5" aria-hidden /> Receive
                </Button>
                {cameraOk ? (
                  <Button type="button" size="xl" variant="outline" onClick={() => setCamera(true)} aria-label="Scan with camera">
                    <Camera className="size-5" aria-hidden />
                  </Button>
                ) : null}
              </div>
            </form>
          </Card>

          <div aria-live="assertive" aria-atomic="true" data-testid="scan-result-region">
            <AnimatePresence mode="popLayout">
              {result && style ? (
                <motion.div
                  key={result.id}
                  initial={reduce ? false : { opacity: 0, scale: 0.96, y: 12 }}
                  animate={{ opacity: 1, scale: 1, y: 0 }}
                  exit={{ opacity: 0, scale: 0.98 }}
                  transition={{ type: "spring", stiffness: 420, damping: 30 }}
                  className={clsx("rounded-3xl border-2 p-5 sm:p-7", style.ring, style.bg)}
                  data-testid="scan-result"
                  data-kind={result.kind}
                  data-code={result.code ?? ""}
                >
                  <div className="flex flex-col gap-5 sm:flex-row sm:items-start">
                    <motion.div
                      initial={reduce ? false : { rotate: -20, scale: 0.5 }}
                      animate={{ rotate: 0, scale: 1 }}
                      transition={{ type: "spring", stiffness: 300, damping: 14, delay: 0.05 }}
                      className={clsx("grid size-16 shrink-0 place-items-center rounded-2xl shadow-lg", style.iconBg)}
                      aria-hidden
                    >
                      {style.icon}
                    </motion.div>
                    <div className="min-w-0 flex-1">
                      <h2 className="text-2xl font-bold tracking-tight sm:text-3xl" data-testid="scan-title">
                        {result.title}
                      </h2>
                      <p className="mt-1 text-base text-fg" data-testid="scan-message">
                        {result.message}
                      </p>
                      <p className="mt-2 font-mono text-xs text-fg-muted">
                        Scanned: {result.token}
                        {result.correlationId ? ` · Ref: ${result.correlationId}` : ""}
                      </p>
                      {result.ret ? (
                        <div className="mt-5 rounded-2xl border border-line bg-surface p-4">
                          <div className="flex flex-wrap items-center justify-between gap-3">
                            <div>
                              <p className="font-mono text-lg font-semibold" data-testid="scan-rma">
                                {result.ret.rma_number}
                              </p>
                              <p className="text-sm text-fg-muted">
                                {result.ret.items.length} unit{result.ret.items.length === 1 ? "" : "s"} · {formatPaise(result.ret.requested_refund_paise)}
                                {result.ret.customer ? ` · ${result.ret.customer.name}` : ""}
                              </p>
                            </div>
                            <ReturnStatusBadge status={result.ret.status} />
                          </div>
                          <ul className="mt-3 space-y-1 text-sm">
                            {result.ret.items.map((it) => (
                              <li key={it.id} className="flex justify-between gap-3">
                                <span className="truncate">{it.product_name}</span>
                                <span className="text-fg-muted">unit {it.unit_index}</span>
                              </li>
                            ))}
                          </ul>
                          {result.ret.status === "RECEIVED" ? (
                            <ButtonLink
                              to={`/warehouse/returns/${result.ret.id}`}
                              size="lg"
                              className="mt-4 w-full sm:w-auto"
                              data-testid="start-inspection"
                            >
                              Start inspection <ArrowRight className="size-4" />
                            </ButtonLink>
                          ) : (
                            <ButtonLink to={`/warehouse/returns/${result.ret.id}`} variant="outline" className="mt-4">
                              Open return
                            </ButtonLink>
                          )}
                        </div>
                      ) : null}
                    </div>
                  </div>
                </motion.div>
              ) : (
                <motion.div
                  key="idle"
                  initial={false}
                  className="grid place-items-center rounded-3xl border-2 border-dashed border-line px-6 py-16 text-center"
                >
                  <div className="relative grid size-20 place-items-center rounded-3xl bg-brand-soft text-brand-soft-fg">
                    <ScanLine className="size-10" aria-hidden />
                    <motion.span
                      aria-hidden
                      className="absolute inset-x-3 h-0.5 rounded-full bg-brand"
                      animate={reduce ? undefined : { top: ["20%", "80%", "20%"] }}
                      transition={{ duration: 2.4, repeat: Infinity, ease: "easeInOut" }}
                    />
                  </div>
                  <p className="mt-4 font-medium">Ready to scan</p>
                  <p className="text-sm text-fg-muted">The input is focused — scan a label any time.</p>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </div>

        <Card className="h-fit">
          <div className="flex items-center gap-2 border-b border-line px-5 py-3.5">
            <History className="size-4 text-fg-muted" aria-hidden />
            <h2 className="text-sm font-semibold">This session</h2>
            <span className="ml-auto text-xs text-fg-subtle">{history.length} scans</span>
          </div>
          {history.length === 0 ? (
            <div className="flex flex-col items-center gap-2 px-5 py-10 text-center text-sm text-fg-muted">
              <SearchX className="size-6" aria-hidden />
              No scans yet
            </div>
          ) : (
            <ul className="divide-y divide-line" data-testid="scan-history">
              {history.map((h) => (
                <li key={h.id} className="flex items-center gap-3 px-5 py-3">
                  <span
                    className={clsx(
                      "size-2.5 shrink-0 rounded-full",
                      h.kind === "success" ? "bg-emerald-500" : h.kind === "warning" ? "bg-amber-500" : "bg-rose-500",
                    )}
                    aria-hidden
                  />
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-mono text-sm">{h.ret?.rma_number ?? h.token}</p>
                    <p className="truncate text-xs text-fg-muted">{h.title}</p>
                  </div>
                  {h.ret ? (
                    <Link to={`/warehouse/returns/${h.ret.id}`} className="text-xs font-medium text-brand hover:underline">
                      Open
                    </Link>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      {cameraOk ? (
        <CameraScanner
          open={camera}
          onClose={() => {
            setCamera(false);
            inputRef.current?.focus();
          }}
          onDetected={(v) => {
            setCamera(false);
            submitToken(v);
          }}
        />
      ) : null}
    </>
  );
}
