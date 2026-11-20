import { ArrowRight, BadgeCheck, Building2, PackageSearch, QrCode, ShieldCheck, Timer, Wallet } from "lucide-react";
import { motion, useReducedMotion } from "motion/react";
import { Navigate } from "react-router";
import { homeFor, useSession } from "../../auth/session";
import { Logo, ThemeToggle } from "../../components/Brand";
import { ButtonLink } from "../../components/ui/Button";
import { CustomerLookupForm, DemoAccounts, StaffLoginForm } from "./AuthForms";

const FEATURES = [
  { icon: <Timer className="size-5" />, title: "Clear return windows", body: "See exactly which items can go back and until when." },
  { icon: <QrCode className="size-5" />, title: "Printable QR label", body: "One label per return. Drop it off, we scan and inspect." },
  { icon: <Wallet className="size-5" />, title: "Refunds you can track", body: "From requested to refunded, every step on one timeline." },
];

export function Landing() {
  const session = useSession();
  const reduce = useReducedMotion();
  if (session) return <Navigate to={homeFor(session.principal.role)} replace />;

  const fade = (delay: number) =>
    reduce ? {} : { initial: { opacity: 0, y: 14 }, animate: { opacity: 1, y: 0 }, transition: { delay, duration: 0.5, ease: [0.16, 1, 0.3, 1] as const } };

  return (
    <div className="relative min-h-dvh overflow-hidden">
      {/* Decorative backdrop */}
      <div aria-hidden className="pointer-events-none absolute inset-0 -z-10">
        <div className="absolute -top-40 left-1/2 h-[520px] w-[900px] -translate-x-1/2 rounded-full bg-gradient-to-br from-indigo-400/30 via-violet-400/20 to-fuchsia-300/10 blur-3xl dark:from-indigo-600/25 dark:via-violet-600/15 dark:to-transparent" />
        <div className="absolute inset-0 bg-[radial-gradient(var(--rr-line)_1px,transparent_1px)] [background-size:22px_22px] [mask-image:linear-gradient(to_bottom,black,transparent_70%)] opacity-60" />
      </div>

      <header className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6">
        <Logo />
        <div className="flex items-center gap-1">
          <ThemeToggle />
          <ButtonLink to="/login" variant="ghost" size="sm" className="hidden sm:inline-flex">
            Staff sign-in
          </ButtonLink>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 pt-8 pb-16 sm:px-6 sm:pt-14">
        <motion.div {...fade(0)} className="mx-auto max-w-3xl text-center">
          <span className="inline-flex items-center gap-2 rounded-full border border-line bg-surface/80 px-3 py-1 text-xs font-medium text-fg-muted backdrop-blur">
            <BadgeCheck className="size-3.5 text-emerald-500" aria-hidden /> Hassle-free returns, honest refunds
          </span>
          <h1 className="mt-5 text-4xl font-semibold tracking-tight text-balance text-fg sm:text-6xl">
            Returns that feel{" "}
            <span className="bg-gradient-to-r from-indigo-500 via-violet-500 to-fuchsia-500 bg-clip-text text-transparent">right</span>.
          </h1>
          <p className="mx-auto mt-4 max-w-xl text-base text-pretty text-fg-muted sm:text-lg">
            Start a return in under a minute, print a QR label, and follow your refund from doorstep to bank — every step visible.
          </p>
        </motion.div>

        <div className="mt-10 grid gap-5 lg:mt-14 lg:grid-cols-[1.15fr_1fr]">
          <motion.section
            {...fade(0.08)}
            aria-labelledby="customer-entry"
            className="relative overflow-hidden rounded-3xl border border-line bg-surface p-6 shadow-pop sm:p-8"
          >
            <div aria-hidden className="absolute -top-16 -right-16 size-48 rounded-full bg-brand-soft blur-2xl" />
            <div className="relative">
              <div className="mb-5 flex items-center gap-3">
                <div className="grid size-11 place-items-center rounded-2xl bg-gradient-to-br from-indigo-500 to-violet-500 text-white shadow-lg shadow-indigo-500/25">
                  <PackageSearch className="size-5" aria-hidden />
                </div>
                <div>
                  <h2 id="customer-entry" className="text-lg font-semibold tracking-tight">
                    Track or start a return
                  </h2>
                  <p className="text-sm text-fg-muted">Use your order number and the email you ordered with.</p>
                </div>
              </div>
              <CustomerLookupForm />
            </div>
          </motion.section>

          <motion.section
            {...fade(0.16)}
            aria-labelledby="staff-entry"
            className="rounded-3xl border border-line bg-surface/80 p-6 shadow-card backdrop-blur sm:p-8"
          >
            <div className="mb-5 flex items-center gap-3">
              <div className="grid size-11 place-items-center rounded-2xl bg-surface-2 text-fg">
                <Building2 className="size-5" aria-hidden />
              </div>
              <div>
                <h2 id="staff-entry" className="text-lg font-semibold tracking-tight">
                  Staff sign-in
                </h2>
                <p className="text-sm text-fg-muted">Warehouse, finance and operations teams.</p>
              </div>
            </div>
            <StaffLoginForm />
          </motion.section>
        </div>

        <motion.div {...fade(0.24)} className="mt-5">
          <DemoAccounts />
        </motion.div>

        <motion.ul {...fade(0.32)} className="mt-14 grid gap-4 sm:grid-cols-3">
          {FEATURES.map((f) => (
            <li key={f.title} className="rounded-2xl border border-line bg-surface/70 p-5 backdrop-blur">
              <div className="mb-3 grid size-10 place-items-center rounded-xl bg-brand-soft text-brand-soft-fg">{f.icon}</div>
              <h3 className="font-semibold">{f.title}</h3>
              <p className="mt-1 text-sm text-fg-muted">{f.body}</p>
            </li>
          ))}
        </motion.ul>

        <footer className="mt-16 flex flex-col items-center justify-between gap-3 border-t border-line pt-6 text-xs text-fg-subtle sm:flex-row">
          <span className="flex items-center gap-1.5">
            <ShieldCheck className="size-3.5" aria-hidden /> Synthetic demo data only · Prices in INR
          </span>
          <ButtonLink to="/login" variant="ghost" size="sm" className="sm:hidden">
            Staff sign-in <ArrowRight className="size-3.5" />
          </ButtonLink>
        </footer>
      </main>
    </div>
  );
}
