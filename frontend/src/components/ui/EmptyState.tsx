import { clsx } from "clsx";
import type { ReactNode } from "react";

export function EmptyState({
  icon,
  title,
  description,
  action,
  className,
  testId,
}: {
  icon: ReactNode;
  title: ReactNode;
  description?: ReactNode;
  action?: ReactNode;
  className?: string;
  testId?: string;
}) {
  return (
    <div className={clsx("flex flex-col items-center justify-center px-6 py-14 text-center", className)} data-testid={testId}>
      <div className="relative mb-5">
        <div className="absolute inset-0 -m-3 rounded-full bg-brand-soft blur-xl" aria-hidden />
        <div className="relative grid size-16 place-items-center rounded-2xl border border-line bg-surface text-brand shadow-card [&>svg]:size-7">
          {icon}
        </div>
      </div>
      <h3 className="text-base font-semibold text-fg">{title}</h3>
      {description ? <p className="mt-1.5 max-w-sm text-sm text-fg-muted">{description}</p> : null}
      {action ? <div className="mt-5">{action}</div> : null}
    </div>
  );
}
