import { Building2 } from "lucide-react";
import { Navigate } from "react-router";
import { homeFor, useSession } from "../../auth/session";
import { Logo, ThemeToggle } from "../../components/Brand";
import { DemoAccounts, StaffLoginForm } from "./AuthForms";

export function StaffLogin() {
  const session = useSession();
  if (session && session.principal.type === "staff") return <Navigate to={homeFor(session.principal.role)} replace />;

  return (
    <div className="relative grid min-h-dvh place-items-center px-4 py-10">
      <div aria-hidden className="pointer-events-none absolute inset-0 -z-10 overflow-hidden">
        <div className="absolute -top-32 left-1/2 h-96 w-[700px] -translate-x-1/2 rounded-full bg-gradient-to-br from-indigo-400/25 to-violet-400/15 blur-3xl dark:from-indigo-600/20" />
      </div>
      <div className="absolute top-4 right-4">
        <ThemeToggle />
      </div>
      <div className="w-full max-w-md">
        <div className="mb-8 flex justify-center">
          <Logo />
        </div>
        <section className="rounded-3xl border border-line bg-surface p-6 shadow-pop sm:p-8" aria-labelledby="staff-title">
          <div className="mb-6 flex items-center gap-3">
            <div className="grid size-11 place-items-center rounded-2xl bg-surface-2">
              <Building2 className="size-5" aria-hidden />
            </div>
            <div>
              <h1 id="staff-title" className="text-xl font-semibold tracking-tight">
                Staff sign-in
              </h1>
              <p className="text-sm text-fg-muted">Warehouse, finance and operations.</p>
            </div>
          </div>
          <StaffLoginForm />
        </section>
        <div className="mt-4">
          <DemoAccounts />
        </div>
      </div>
    </div>
  );
}
