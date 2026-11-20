import { clsx } from "clsx";
import type { ReactNode } from "react";
import type { Tone } from "../../lib/labels";

const tones: Record<Tone, string> = {
  neutral: "bg-surface-2 text-fg-muted ring-line",
  brand: "bg-brand-soft text-brand-soft-fg ring-indigo-200 dark:ring-indigo-900",
  info: "bg-sky-50 text-sky-800 ring-sky-200 dark:bg-sky-950/60 dark:text-sky-200 dark:ring-sky-900",
  success: "bg-emerald-50 text-emerald-800 ring-emerald-200 dark:bg-emerald-950/60 dark:text-emerald-200 dark:ring-emerald-900",
  warning: "bg-amber-50 text-amber-900 ring-amber-200 dark:bg-amber-950/60 dark:text-amber-200 dark:ring-amber-900",
  danger: "bg-rose-50 text-rose-800 ring-rose-200 dark:bg-rose-950/60 dark:text-rose-200 dark:ring-rose-900",
};

const dots: Record<Tone, string> = {
  neutral: "bg-fg-subtle",
  brand: "bg-brand",
  info: "bg-sky-500",
  success: "bg-emerald-500",
  warning: "bg-amber-500",
  danger: "bg-rose-500",
};

export function Badge({
  tone = "neutral",
  children,
  dot = false,
  pulse = false,
  icon,
  className,
  ...rest
}: {
  tone?: Tone;
  children: ReactNode;
  dot?: boolean;
  pulse?: boolean;
  icon?: ReactNode;
  className?: string;
  "data-testid"?: string;
  title?: string;
}) {
  return (
    <span
      className={clsx(
        "inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium whitespace-nowrap ring-1 ring-inset",
        tones[tone],
        className,
      )}
      {...rest}
    >
      {dot ? (
        <span className="relative flex size-1.5">
          {pulse ? <span className={clsx("absolute inline-flex size-full animate-ping rounded-full opacity-75", dots[tone])} /> : null}
          <span className={clsx("relative inline-flex size-1.5 rounded-full", dots[tone])} />
        </span>
      ) : null}
      {icon}
      {children}
    </span>
  );
}
