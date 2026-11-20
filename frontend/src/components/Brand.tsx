import { clsx } from "clsx";
import { Monitor, Moon, Sun } from "lucide-react";
import { Link } from "react-router";
import { useTheme } from "../lib/theme";

export function Logo({ className, to = "/" }: { className?: string; to?: string }) {
  return (
    <Link to={to} className={clsx("inline-flex items-center gap-2.5 rounded-lg", className)} aria-label="ReturnRight home">
      <img src="/favicon.svg" alt="" className="size-8" width={32} height={32} />
      <span className="text-[17px] font-semibold tracking-tight text-fg">
        Return<span className="text-brand">Right</span>
      </span>
    </Link>
  );
}

export function ThemeToggle({ className }: { className?: string }) {
  const { theme, pref, setPref } = useTheme();
  const next = pref === "system" ? (theme === "dark" ? "light" : "dark") : pref === "dark" ? "light" : "system";
  const label = `Theme: ${pref}${pref === "system" ? ` (${theme})` : ""}. Switch to ${next}.`;
  return (
    <button
      type="button"
      onClick={() => setPref(next)}
      className={clsx(
        "grid size-9 place-items-center rounded-xl text-fg-muted transition-colors hover:bg-surface-2 hover:text-fg",
        className,
      )}
      aria-label={label}
      title={label}
      data-testid="theme-toggle"
    >
      {pref === "system" ? <Monitor className="size-[18px]" /> : theme === "dark" ? <Moon className="size-[18px]" /> : <Sun className="size-[18px]" />}
    </button>
  );
}
