import { clsx } from "clsx";
import {
  ClipboardList,
  FileSearch,
  Flag,
  LayoutDashboard,
  Menu,
  Receipt,
  Scale,
  ScanLine,
  X,
} from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useEffect, useState } from "react";
import type { ReactNode } from "react";
import { NavLink, useLocation } from "react-router";
import type { Role } from "../api/types";
import { hasRole, useSession } from "../auth/session";
import { Logo, ThemeToggle } from "../components/Brand";
import { UserChip } from "../components/UserMenu";
import { AnimatedOutlet } from "./PageTransition";

interface NavItem {
  to: string;
  label: string;
  icon: ReactNode;
  end?: boolean;
}
interface NavSection {
  title: string;
  roles: Role[];
  items: NavItem[];
}

const SECTIONS: NavSection[] = [
  {
    title: "Warehouse",
    roles: ["WAREHOUSE_AGENT"],
    items: [
      { to: "/warehouse", label: "Scan station", icon: <ScanLine className="size-[18px]" />, end: true },
      { to: "/warehouse/queue", label: "Returns queue", icon: <ClipboardList className="size-[18px]" /> },
    ],
  },
  {
    title: "Finance",
    roles: ["FINANCE_REVIEWER"],
    items: [{ to: "/finance", label: "Review queue", icon: <Receipt className="size-[18px]" /> }],
  },
  {
    title: "Operations",
    roles: ["OPS_MANAGER"],
    items: [
      { to: "/manager", label: "Dashboard", icon: <LayoutDashboard className="size-[18px]" />, end: true },
      { to: "/manager/flagged", label: "Flagged customers", icon: <Flag className="size-[18px]" /> },
      { to: "/manager/audit", label: "Audit log", icon: <FileSearch className="size-[18px]" /> },
      { to: "/manager/reconciliation", label: "Reconciliation", icon: <Scale className="size-[18px]" /> },
    ],
  },
];

function NavList({ sections, onNavigate }: { sections: NavSection[]; onNavigate?: () => void }) {
  return (
    <nav aria-label="Staff" className="space-y-6">
      {sections.map((s) => (
        <div key={s.title}>
          <p className="mb-2 px-3 text-[11px] font-semibold tracking-wider text-fg-subtle uppercase">{s.title}</p>
          <ul className="space-y-0.5">
            {s.items.map((i) => (
              <li key={i.to}>
                <NavLink
                  to={i.to}
                  end={i.end}
                  onClick={onNavigate}
                  className={({ isActive }) =>
                    clsx(
                      "group relative flex items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium transition-colors",
                      isActive ? "bg-brand-soft text-brand-soft-fg" : "text-fg-muted hover:bg-surface-2 hover:text-fg",
                    )
                  }
                >
                  {i.icon}
                  {i.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  );
}

export function StaffLayout() {
  const session = useSession();
  const { pathname } = useLocation();
  const [drawer, setDrawer] = useState(false);
  const sections = SECTIONS.filter((s) => hasRole(session?.principal, s.roles));
  const flat = sections.flatMap((s) => s.items);
  const showBottomNav = flat.length > 1 && flat.length <= 4;

  useEffect(() => {
    if (!drawer) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setDrawer(false);
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [drawer]);

  return (
    <div className={clsx("min-h-dvh lg:pl-64", showBottomNav && "pb-20 lg:pb-0")}>
      <a href="#main" className="sr-only z-50 rounded-lg bg-brand px-3 py-2 text-brand-fg focus:not-sr-only focus:fixed focus:top-3 focus:left-3">
        Skip to content
      </a>
      {/* Desktop sidebar */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 flex-col border-r border-line bg-surface lg:flex print:hidden">
        <div className="flex h-16 items-center px-5">
          <Logo to="/" />
        </div>
        <div className="flex-1 overflow-y-auto px-3 py-4">
          <NavList sections={sections} />
        </div>
        <div className="flex items-center justify-between gap-2 border-t border-line p-3">
          <UserChip />
          <ThemeToggle />
        </div>
      </aside>

      {/* Mobile top bar */}
      <header className="sticky top-0 z-30 flex h-14 items-center gap-2 border-b border-line bg-surface/85 px-3 backdrop-blur-xl lg:hidden print:hidden">
        <button
          type="button"
          onClick={() => setDrawer(true)}
          className="grid size-10 place-items-center rounded-xl text-fg-muted hover:bg-surface-2 hover:text-fg"
          aria-label="Open navigation"
          aria-expanded={drawer}
          data-testid="open-nav"
        >
          <Menu className="size-5" />
        </button>
        <Logo to="/" />
        <div className="ml-auto flex items-center gap-1">
          <ThemeToggle />
          <UserChip compact />
        </div>
      </header>

      <AnimatePresence>
        {drawer ? (
          <div className="fixed inset-0 z-50 lg:hidden" role="dialog" aria-modal="true" aria-label="Navigation">
            <motion.div
              className="absolute inset-0 bg-slate-950/50 backdrop-blur-[2px]"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={() => setDrawer(false)}
            />
            <motion.div
              className="absolute inset-y-0 left-0 flex w-72 max-w-[85vw] flex-col border-r border-line bg-surface shadow-pop"
              initial={{ x: "-100%" }}
              animate={{ x: 0 }}
              exit={{ x: "-100%" }}
              transition={{ type: "spring", stiffness: 420, damping: 40 }}
            >
              <div className="flex h-14 items-center justify-between px-4">
                <Logo to="/" />
                <button
                  type="button"
                  onClick={() => setDrawer(false)}
                  className="grid size-10 place-items-center rounded-xl text-fg-muted hover:bg-surface-2"
                  aria-label="Close navigation"
                  autoFocus
                >
                  <X className="size-5" />
                </button>
              </div>
              <div className="flex-1 overflow-y-auto px-3 py-4">
                <NavList sections={sections} onNavigate={() => setDrawer(false)} />
              </div>
              <div className="border-t border-line p-3">
                <UserChip />
              </div>
            </motion.div>
          </div>
        ) : null}
      </AnimatePresence>

      <main id="main" key={pathname.split("/")[1]} className="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-10 lg:py-10">
        <AnimatedOutlet />
      </main>

      {showBottomNav ? (
        <nav
          aria-label="Quick"
          className="fixed inset-x-0 bottom-0 z-30 border-t border-line bg-surface/90 pb-[env(safe-area-inset-bottom)] backdrop-blur-xl lg:hidden print:hidden"
        >
          <div className="grid" style={{ gridTemplateColumns: `repeat(${flat.length}, minmax(0, 1fr))` }}>
            {flat.map((n) => (
              <NavLink
                key={n.to}
                to={n.to}
                end={n.end}
                className={({ isActive }) =>
                  clsx(
                    "flex flex-col items-center gap-0.5 px-1 py-2.5 text-center text-[11px] font-medium transition-colors",
                    isActive ? "text-brand" : "text-fg-muted",
                  )
                }
              >
                {n.icon}
                <span className="truncate">{n.label}</span>
              </NavLink>
            ))}
          </div>
        </nav>
      ) : null}
    </div>
  );
}
