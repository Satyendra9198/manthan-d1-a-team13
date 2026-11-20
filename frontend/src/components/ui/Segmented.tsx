import { clsx } from "clsx";
import type { ReactNode } from "react";

export interface SegmentOption<T extends string> {
  value: T;
  label: ReactNode;
  hint?: string;
  tone?: "default" | "success" | "danger" | "warning";
}

const activeTone = {
  default: "bg-brand text-brand-fg shadow-sm",
  success: "bg-emerald-600 text-white shadow-sm dark:bg-emerald-500 dark:text-emerald-950",
  danger: "bg-rose-600 text-white shadow-sm dark:bg-rose-500 dark:text-rose-950",
  warning: "bg-amber-500 text-amber-950 shadow-sm",
};

/** Radio-group semantics so it is keyboard operable with arrow keys and announced correctly. */
export function Segmented<T extends string>({
  name,
  options,
  value,
  onChange,
  label,
  size = "md",
  disabled,
  testId,
}: {
  name: string;
  options: SegmentOption<T>[];
  value: T | null;
  onChange: (v: T) => void;
  label: string;
  size?: "md" | "lg";
  disabled?: boolean;
  testId?: string;
}) {
  return (
    <fieldset className="min-w-0" disabled={disabled} data-testid={testId}>
      <legend className="sr-only">{label}</legend>
      <div className="flex w-full gap-1 rounded-xl border border-line bg-surface-2 p-1">
        {options.map((o) => {
          const active = o.value === value;
          return (
            <label
              key={o.value}
              title={o.hint}
              className={clsx(
                "flex flex-1 cursor-pointer items-center justify-center gap-1.5 rounded-lg text-center font-medium transition-colors has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-ring",
                size === "lg" ? "min-h-11 px-3 text-sm" : "min-h-9 px-2.5 text-sm",
                active ? activeTone[o.tone ?? "default"] : "text-fg-muted hover:bg-surface hover:text-fg",
                disabled && "cursor-not-allowed opacity-60",
              )}
            >
              <input
                type="radio"
                className="sr-only"
                name={name}
                value={o.value}
                checked={active}
                onChange={() => onChange(o.value)}
                data-testid={testId ? `${testId}-${o.value}` : undefined}
              />
              {o.label}
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}
