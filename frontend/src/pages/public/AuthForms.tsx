import { ArrowRight, KeyRound, Lock, Mail, Receipt, User } from "lucide-react";
import { useState } from "react";
import type { FormEvent, ReactNode } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { useCustomerLookup, useStaffLogin } from "../../api/hooks/auth";
import { hasCode, isApiError } from "../../api/client";
import type { Role } from "../../api/types";
import { homeFor } from "../../auth/session";
import { Button } from "../../components/ui/Button";
import { TextField } from "../../components/ui/Field";

function safeNext(next: string | null, role: Role): string {
  if (!next || !next.startsWith("/") || next.startsWith("//")) return homeFor(role);
  const area = next.split("/")[1];
  const ok =
    (role === "CUSTOMER" && area === "portal") ||
    (role !== "CUSTOMER" &&
      ((area === "warehouse" && (role === "WAREHOUSE_AGENT" || role === "ADMIN")) ||
        (area === "finance" && (role === "FINANCE_REVIEWER" || role === "ADMIN")) ||
        (area === "manager" && (role === "OPS_MANAGER" || role === "ADMIN"))));
  return ok ? next : homeFor(role);
}

function FormError({ children }: { children: ReactNode }) {
  return (
    <div
      role="alert"
      className="rounded-xl border border-rose-200 bg-rose-50 px-3.5 py-3 text-sm text-rose-800 dark:border-rose-900 dark:bg-rose-950/50 dark:text-rose-200"
    >
      {children}
    </div>
  );
}

export function CustomerLookupForm() {
  const [orderNumber, setOrderNumber] = useState("");
  const [email, setEmail] = useState("");
  const lookup = useCustomerLookup();
  const navigate = useNavigate();
  const [params] = useSearchParams();

  function submit(e: FormEvent) {
    e.preventDefault();
    if (lookup.isPending) return;
    lookup.mutate(
      { order_number: orderNumber.trim(), email: email.trim() },
      { onSuccess: (auth) => void navigate(safeNext(params.get("next"), auth.principal.role), { replace: true }) },
    );
  }

  const err = lookup.error;
  return (
    <form onSubmit={submit} className="space-y-4" data-testid="customer-lookup-form" noValidate>
      <TextField
        label="Order number"
        name="order_number"
        placeholder="RR-2026-000001"
        autoComplete="off"
        autoCapitalize="characters"
        required
        value={orderNumber}
        onChange={(e) => setOrderNumber(e.target.value)}
        data-testid="lookup-order"
      />
      <TextField
        label="Email used for the order"
        name="email"
        type="email"
        inputMode="email"
        placeholder="you@example.com"
        autoComplete="email"
        required
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        data-testid="lookup-email"
      />
      {err ? (
        <FormError>
          {isApiError(err) ? err.message : "Something went wrong."}
          {isApiError(err) && err.correlationId ? <span className="mt-1 block font-mono text-xs opacity-70">Ref: {err.correlationId}</span> : null}
        </FormError>
      ) : null}
      <Button
        type="submit"
        size="lg"
        className="w-full"
        loading={lookup.isPending}
        disabled={!orderNumber.trim() || !email.trim()}
        data-testid="lookup-submit"
      >
        Find my order <ArrowRight className="size-4" aria-hidden />
      </Button>
      <p className="flex items-center gap-1.5 text-xs text-fg-subtle">
        <Receipt className="size-3.5" aria-hidden /> No account needed — we'll show every order on this email.
      </p>
    </form>
  );
}

export function StaffLoginForm() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const login = useStaffLogin();
  const navigate = useNavigate();
  const [params] = useSearchParams();

  function submit(e: FormEvent) {
    e.preventDefault();
    if (login.isPending) return;
    login.mutate(
      { username: username.trim(), password },
      { onSuccess: (auth) => void navigate(safeNext(params.get("next"), auth.principal.role), { replace: true }) },
    );
  }

  const err = login.error;
  const locked = hasCode(err, "ACCOUNT_LOCKED") || (isApiError(err) && err.status === 423);
  return (
    <form onSubmit={submit} className="space-y-4" data-testid="staff-login-form" noValidate>
      <TextField
        label="Username"
        name="username"
        autoComplete="username"
        autoCapitalize="none"
        spellCheck={false}
        required
        value={username}
        onChange={(e) => setUsername(e.target.value)}
        data-testid="staff-username"
      />
      <TextField
        label="Password"
        name="password"
        type="password"
        autoComplete="current-password"
        required
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        data-testid="staff-password"
      />
      {err ? (
        locked ? (
          <div
            role="alert"
            data-testid="account-locked"
            className="flex gap-3 rounded-xl border border-amber-200 bg-amber-50 px-3.5 py-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/50 dark:text-amber-100"
          >
            <Lock className="mt-0.5 size-4 shrink-0" aria-hidden />
            <div>
              <p className="font-semibold">Account temporarily locked</p>
              <p className="mt-0.5">
                {isApiError(err) ? err.message : ""} Too many failed sign-ins — wait 15 minutes, then try again, or ask an admin to
                unlock it.
              </p>
            </div>
          </div>
        ) : (
          <FormError>
            {isApiError(err) ? err.message : "Something went wrong."}
            {isApiError(err) && err.correlationId ? <span className="mt-1 block font-mono text-xs opacity-70">Ref: {err.correlationId}</span> : null}
          </FormError>
        )
      ) : null}
      <Button
        type="submit"
        size="lg"
        className="w-full"
        loading={login.isPending}
        disabled={!username.trim() || !password}
        data-testid="staff-submit"
        icon={<KeyRound className="size-4" aria-hidden />}
      >
        Sign in
      </Button>
    </form>
  );
}

export function DemoAccounts() {
  const staff = [
    { u: "warehouse1", r: "Warehouse agent" },
    { u: "finance1", r: "Finance reviewer" },
    { u: "finance2", r: "Finance reviewer" },
    { u: "manager1", r: "Ops manager" },
    { u: "admin", r: "Admin (all staff areas)" },
  ];
  return (
    <details className="group rounded-2xl border border-line bg-surface/70 backdrop-blur" data-testid="demo-accounts">
      <summary className="flex cursor-pointer list-none items-center justify-between gap-2 rounded-2xl px-4 py-3 text-sm font-medium text-fg-muted hover:text-fg">
        <span className="flex items-center gap-2">
          <KeyRound className="size-4" aria-hidden /> Demo accounts
        </span>
        <span className="text-xs transition-transform group-open:rotate-180" aria-hidden>
          ▾
        </span>
      </summary>
      <div className="grid gap-4 border-t border-line px-4 py-4 text-sm sm:grid-cols-2">
        <div>
          <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold tracking-wider text-fg-subtle uppercase">
            <User className="size-3.5" aria-hidden /> Staff
          </p>
          <ul className="space-y-1">
            {staff.map((s) => (
              <li key={s.u} className="flex justify-between gap-3">
                <code className="font-mono text-fg">{s.u}</code>
                <span className="text-right text-fg-muted">{s.r}</span>
              </li>
            ))}
          </ul>
          <p className="mt-2 text-fg-muted">
            Password: the demo host's <code className="rounded bg-surface-2 px-1.5 py-0.5 font-mono text-fg">RR_SEED_STAFF_PASSWORD</code> (not shipped in the app)
          </p>
        </div>
        <div>
          <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold tracking-wider text-fg-subtle uppercase">
            <Mail className="size-3.5" aria-hidden /> Customer
          </p>
          <p className="text-fg-muted">Email</p>
          <code className="font-mono text-fg">edge.customer@example.com</code>
          <p className="mt-2 text-fg-muted">Order</p>
          <code className="font-mono text-fg">RR-EDGE-001</code>
        </div>
      </div>
    </details>
  );
}
