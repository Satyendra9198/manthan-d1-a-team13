import { clsx } from "clsx";
import { Bell, BellOff } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useEffect, useId, useRef, useState } from "react";
import { Link } from "react-router";
import { useNotifications } from "../api/hooks/customer";
import type { NotificationDTO } from "../api/types";
import { relativeTime } from "../lib/format";

const SEEN_KEY = "rr.notifications.seenAt";

function readSeen(): number {
  try {
    return Number(window.localStorage.getItem(SEEN_KEY) ?? 0) || 0;
  } catch {
    return 0;
  }
}

function writeSeen(ts: number): void {
  try {
    window.localStorage.setItem(SEEN_KEY, String(ts));
  } catch {
    /* ignore */
  }
}

/**
 * Unread = `read_at` is null AND newer than the last time this browser opened the panel.
 * The contract has no "mark read" endpoint, so "seen" is a per-browser convenience.
 */
function isUnread(n: NotificationDTO, seenAt: number): boolean {
  return n.read_at === null && new Date(n.created_at).getTime() > seenAt;
}

export function NotificationsBell() {
  const { data } = useNotifications();
  const [open, setOpen] = useState(false);
  const [seenAt, setSeenAt] = useState(readSeen);
  const [now, setNow] = useState(() => Date.now());
  const panelId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const items = data?.items ?? [];
  const unread = items.filter((n) => isUnread(n, seenAt)).length;

  useEffect(() => {
    if (!open) return;
    function onDoc(e: MouseEvent) {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    document.addEventListener("mousedown", onDoc);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onDoc);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  function toggle() {
    const ts = Date.now();
    if (!open) {
      setNow(ts);
    } else {
      setSeenAt(ts);
      writeSeen(ts);
    }
    setOpen(!open);
  }

  function closeAndMark() {
    const ts = Date.now();
    setSeenAt(ts);
    writeSeen(ts);
    setOpen(false);
  }

  return (
    <div className="relative" ref={rootRef}>
      <button
        type="button"
        onClick={toggle}
        aria-expanded={open}
        aria-controls={panelId}
        aria-label={unread ? `Notifications, ${unread} unread` : "Notifications"}
        className="relative grid size-9 place-items-center rounded-xl text-fg-muted transition-colors hover:bg-surface-2 hover:text-fg"
        data-testid="notifications-bell"
      >
        <Bell className="size-[18px]" />
        <AnimatePresence>
          {unread > 0 ? (
            <motion.span
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              exit={{ scale: 0 }}
              className="absolute -top-0.5 -right-0.5 grid h-[18px] min-w-[18px] place-items-center rounded-full bg-rose-500 px-1 text-[10px] font-bold text-white ring-2 ring-surface"
              data-testid="notifications-unread"
            >
              {unread > 9 ? "9+" : unread}
            </motion.span>
          ) : null}
        </AnimatePresence>
      </button>
      <AnimatePresence>
        {open ? (
          <motion.div
            id={panelId}
            role="dialog"
            aria-label="Notifications"
            initial={{ opacity: 0, y: -6, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -6, scale: 0.98 }}
            transition={{ duration: 0.14 }}
            className="fixed inset-x-3 top-16 z-50 origin-top-right overflow-hidden rounded-2xl border border-line bg-elevated shadow-pop sm:absolute sm:inset-x-auto sm:top-11 sm:right-0 sm:w-96"
          >
            <div className="flex items-center justify-between border-b border-line px-4 py-3">
              <h2 className="text-sm font-semibold">Notifications</h2>
              <Link to="/portal/notifications" onClick={closeAndMark} className="text-xs font-medium text-brand hover:underline">
                View all
              </Link>
            </div>
            {items.length === 0 ? (
              <div className="flex flex-col items-center gap-2 px-4 py-10 text-center text-sm text-fg-muted">
                <BellOff className="size-6" aria-hidden />
                You're all caught up.
              </div>
            ) : (
              <ul className="max-h-[60dvh] divide-y divide-line overflow-y-auto">
                {items.slice(0, 8).map((n) => (
                  <li key={n.id}>
                    <NotificationRow n={n} unread={isUnread(n, seenAt)} now={now} onNavigate={closeAndMark} />
                  </li>
                ))}
              </ul>
            )}
          </motion.div>
        ) : null}
      </AnimatePresence>
    </div>
  );
}

export function NotificationRow({
  n,
  unread,
  now,
  onNavigate,
}: {
  n: NotificationDTO;
  unread: boolean;
  now: number;
  onNavigate?: () => void;
}) {
  const body = (
    <div className={clsx("flex gap-3 px-4 py-3 transition-colors", n.return_id && "hover:bg-surface-2")}>
      <span className={clsx("mt-1.5 size-2 shrink-0 rounded-full", unread ? "bg-brand" : "bg-transparent")} aria-hidden />
      <div className="min-w-0">
        <p className={clsx("text-sm", unread ? "font-semibold text-fg" : "font-medium text-fg")}>{n.title}</p>
        <p className="mt-0.5 text-sm text-fg-muted">{n.body}</p>
        <p className="mt-1 text-xs text-fg-subtle">{relativeTime(n.created_at, now)}</p>
      </div>
    </div>
  );
  return n.return_id ? (
    <Link to={`/portal/returns/${n.return_id}`} onClick={onNavigate} className="block">
      {body}
    </Link>
  ) : (
    body
  );
}
