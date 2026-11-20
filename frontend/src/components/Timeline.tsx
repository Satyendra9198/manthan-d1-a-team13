import { clsx } from "clsx";
import { Ban, Check, CircleDollarSign, ClipboardCheck, PackageCheck, PackageOpen, Send, ThumbsUp, Truck, XCircle } from "lucide-react";
import { motion, useReducedMotion } from "motion/react";
import type { ReactNode } from "react";
import type { TimelineEntry, TimelineStep } from "../api/types";
import { formatDateTime, formatShortDate } from "../lib/format";

const ICONS: Record<TimelineStep, ReactNode> = {
  REQUESTED: <Send />,
  APPROVED: <ThumbsUp />,
  IN_TRANSIT: <Truck />,
  RECEIVED: <PackageOpen />,
  INSPECTED: <ClipboardCheck />,
  REFUNDED: <CircleDollarSign />,
  REJECTED: <XCircle />,
  CANCELLED: <Ban />,
};

function isNegative(step: TimelineStep) {
  return step === "REJECTED" || step === "CANCELLED";
}

/**
 * Customer timeline rendered from the backend `timeline[]` (Part 1 §4.4).
 * Horizontal on wide screens, vertical on phones. Progress animates in.
 */
export function Timeline({ entries }: { entries: TimelineEntry[] }) {
  const reduce = useReducedMotion();
  const lastDone = entries.reduce((acc, e, i) => (e.state !== "upcoming" ? i : acc), 0);
  const progress = entries.length > 1 ? lastDone / (entries.length - 1) : 1;

  return (
    <div data-testid="timeline">
      {/* Horizontal (sm and up) */}
      <div className="relative hidden sm:block">
        <div
          className="absolute top-[18px] h-1 rounded-full bg-line"
          style={{ left: `${50 / entries.length}%`, right: `${50 / entries.length}%` }}
          aria-hidden
        >
          <motion.div
            className="h-full origin-left rounded-full bg-gradient-to-r from-indigo-500 to-violet-500"
            initial={{ scaleX: reduce ? progress : 0 }}
            animate={{ scaleX: progress }}
            transition={{ duration: 0.9, ease: [0.16, 1, 0.3, 1], delay: 0.15 }}
          />
        </div>
        <ol className="relative grid grid-flow-col auto-cols-fr" aria-label="Return progress">
        {entries.map((e, i) => (
          <li key={e.step} className="relative flex flex-col items-center px-1 text-center" aria-current={e.state === "current" ? "step" : undefined}>
            <Dot entry={e} index={i} />
            <span className={clsx("mt-3 text-sm font-medium", e.state === "upcoming" ? "text-fg-subtle" : "text-fg")}>{e.label}</span>
            <span className="mt-0.5 text-xs text-fg-muted tabular">{e.at ? formatShortDate(e.at) : e.state === "upcoming" ? "" : "—"}</span>
          </li>
        ))}
        </ol>
      </div>

      {/* Vertical (phones) */}
      <div className="relative sm:hidden">
        <div className="absolute top-5 bottom-5 left-5 w-1 -translate-x-1/2 rounded-full bg-line" aria-hidden>
          <motion.div
            className="w-full origin-top rounded-full bg-gradient-to-b from-indigo-500 to-violet-500"
            style={{ height: "100%" }}
            initial={{ scaleY: reduce ? progress : 0 }}
            animate={{ scaleY: progress }}
            transition={{ duration: 0.9, ease: [0.16, 1, 0.3, 1], delay: 0.15 }}
          />
        </div>
        <ol className="relative space-y-5" aria-label="Return progress">
        {entries.map((e, i) => (
          <li key={e.step} className="relative flex items-center gap-4" aria-current={e.state === "current" ? "step" : undefined}>
            <Dot entry={e} index={i} />
            <div>
              <div className={clsx("text-sm font-medium", e.state === "upcoming" ? "text-fg-subtle" : "text-fg")}>{e.label}</div>
              {e.at ? <div className="text-xs text-fg-muted">{formatDateTime(e.at)}</div> : null}
            </div>
          </li>
        ))}
        </ol>
      </div>
    </div>
  );
}

function Dot({ entry, index }: { entry: TimelineEntry; index: number }) {
  const reduce = useReducedMotion();
  const negative = isNegative(entry.step);
  const done = entry.state === "done";
  const current = entry.state === "current";
  return (
    <motion.div
      initial={reduce ? false : { scale: 0.6, opacity: 0 }}
      animate={{ scale: 1, opacity: 1 }}
      transition={{ delay: 0.08 * index, type: "spring", stiffness: 380, damping: 24 }}
      className={clsx(
        "relative z-10 grid size-10 shrink-0 place-items-center rounded-full border-2 [&>svg]:size-[18px]",
        done && !negative && "border-transparent bg-gradient-to-br from-indigo-500 to-violet-500 text-white",
        current && !negative && "border-brand bg-surface text-brand ring-4 ring-brand/15",
        negative && entry.state !== "upcoming" && "border-transparent bg-rose-500 text-white ring-4 ring-rose-500/15",
        entry.state === "upcoming" && "border-line bg-surface text-fg-subtle",
      )}
    >
      {current && !negative ? <span className="absolute inset-0 animate-ping rounded-full bg-brand/20" aria-hidden /> : null}
      {done && !negative ? <Check strokeWidth={3} /> : entry.step === "REFUNDED" && current ? <PackageCheck /> : ICONS[entry.step]}
      <span className="sr-only">{entry.state}</span>
    </motion.div>
  );
}
