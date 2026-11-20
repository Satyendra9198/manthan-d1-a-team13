import { ChevronLeft } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router";

export function PageHeader({
  title,
  description,
  actions,
  back,
  eyebrow,
}: {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  back?: { to: string; label: string };
  eyebrow?: ReactNode;
}) {
  return (
    <header className="mb-6 print:hidden">
      {back ? (
        <Link
          to={back.to}
          className="mb-3 inline-flex items-center gap-1 rounded-md text-sm font-medium text-fg-muted transition-colors hover:text-fg"
        >
          <ChevronLeft className="size-4" aria-hidden />
          {back.label}
        </Link>
      ) : null}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div className="min-w-0">
          {eyebrow ? <div className="mb-1 text-xs font-semibold tracking-wider text-brand uppercase">{eyebrow}</div> : null}
          <h1 className="text-2xl font-semibold tracking-tight text-fg sm:text-[28px]">{title}</h1>
          {description ? <p className="mt-1.5 text-sm text-fg-muted sm:text-base">{description}</p> : null}
        </div>
        {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
      </div>
    </header>
  );
}
