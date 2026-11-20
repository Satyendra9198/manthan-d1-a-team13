import { LogOut } from "lucide-react";
import { useNavigate } from "react-router";
import { useLogout } from "../api/hooks/auth";
import { useSession } from "../auth/session";
import { initials } from "../lib/format";
import { humanize } from "../lib/labels";

export function UserChip({ compact = false }: { compact?: boolean }) {
  const session = useSession();
  const logout = useLogout();
  const navigate = useNavigate();
  if (!session) return null;
  const p = session.principal;
  const home = p.type === "customer" ? "/" : "/login";

  return (
    <div className="flex items-center gap-2">
      <div className="flex min-w-0 items-center gap-2.5">
        <div
          className="grid size-9 shrink-0 place-items-center rounded-full bg-gradient-to-br from-indigo-500 to-violet-500 text-xs font-semibold text-white"
          aria-hidden
        >
          {initials(p.name) || "?"}
        </div>
        {!compact ? (
          <div className="min-w-0 leading-tight">
            <p className="truncate text-sm font-medium text-fg" data-testid="user-name">
              {p.name}
            </p>
            <p className="truncate text-xs text-fg-muted">{p.type === "customer" ? (p.email ?? "Customer") : humanize(p.role)}</p>
          </div>
        ) : null}
      </div>
      <button
        type="button"
        onClick={() => logout.mutate(undefined, { onSettled: () => void navigate(home, { replace: true }) })}
        className="grid size-9 shrink-0 place-items-center rounded-xl text-fg-muted transition-colors hover:bg-surface-2 hover:text-fg"
        aria-label="Sign out"
        title="Sign out"
        data-testid="sign-out"
      >
        <LogOut className="size-[18px]" />
      </button>
    </div>
  );
}
