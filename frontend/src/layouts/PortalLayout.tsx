import { clsx } from "clsx";
import { Bell, PackageSearch, RotateCcw } from "lucide-react";
import type { ReactNode } from "react";
import { NavLink } from "react-router";
import { Logo, ThemeToggle } from "../components/Brand";
import { NotificationsBell } from "../components/NotificationsBell";
import { UserChip } from "../components/UserMenu";
import { AnimatedOutlet } from "./PageTransition";

const NAV: { to: string; label: string; icon: ReactNode; end?: boolean; testId: string }[] = [
  { to: "/portal", label: "Orders", icon: <PackageSearch className="size-5" />, end: true, testId: "nav-orders" },
  { to: "/portal/returns", label: "Returns", icon: <RotateCcw className="size-5" />, testId: "nav-returns" },
  { to: "/portal/notifications", label: "Updates", icon: <Bell className="size-5" />, testId: "nav-notifications" },
];

export function PortalLayout() {
  return (
    <div className="min-h-dvh pb-20 md:pb-0">
      <a href="#main" className="sr-only z-50 rounded-lg bg-brand px-3 py-2 text-brand-fg focus:not-sr-only focus:fixed focus:top-3 focus:left-3">
        Skip to content
      </a>
      <header className="sticky top-0 z-40 border-b border-line bg-surface/80 backdrop-blur-xl print:hidden">
        <div className="mx-auto flex h-16 max-w-6xl items-center gap-6 px-4 sm:px-6">
          <Logo to="/portal" />
          <nav className="hidden items-center gap-1 md:flex" aria-label="Main">
            {NAV.slice(0, 2).map((n) => (
              <NavLink
                key={n.to}
                to={n.to}
                end={n.end}
                className={({ isActive }) =>
                  clsx(
                    "rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                    isActive ? "bg-brand-soft text-brand-soft-fg" : "text-fg-muted hover:bg-surface-2 hover:text-fg",
                  )
                }
              >
                {n.label}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto flex items-center gap-1">
            <NotificationsBell />
            <ThemeToggle />
            <div className="ml-2 hidden border-l border-line pl-3 sm:block">
              <UserChip />
            </div>
            <div className="sm:hidden">
              <UserChip compact />
            </div>
          </div>
        </div>
      </header>

      <main id="main" className="mx-auto max-w-6xl px-4 py-6 sm:px-6 sm:py-10">
        <AnimatedOutlet />
      </main>

      <nav
        aria-label="Main"
        className="fixed inset-x-0 bottom-0 z-40 border-t border-line bg-surface/90 pb-[env(safe-area-inset-bottom)] backdrop-blur-xl md:hidden print:hidden"
      >
        <div className="grid grid-cols-3">
          {NAV.map((n) => (
            <NavLink
              key={n.to}
              to={n.to}
              end={n.end}
              data-testid={n.testId}
              className={({ isActive }) =>
                clsx(
                  "flex flex-col items-center gap-0.5 py-2.5 text-[11px] font-medium transition-colors",
                  isActive ? "text-brand" : "text-fg-muted",
                )
              }
            >
              {n.icon}
              {n.label}
            </NavLink>
          ))}
        </div>
      </nav>
    </div>
  );
}
