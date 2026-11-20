import { clsx } from "clsx";
import { useId } from "react";
import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from "react";

const control =
  "block w-full rounded-xl border border-line-strong bg-surface px-3.5 text-sm text-fg placeholder:text-fg-subtle shadow-xs transition-colors focus:border-brand focus:outline-none focus:ring-4 focus:ring-brand/15 disabled:cursor-not-allowed disabled:opacity-60 aria-[invalid=true]:border-rose-500 aria-[invalid=true]:focus:ring-rose-500/15";

interface FieldShell {
  label: ReactNode;
  hint?: ReactNode;
  error?: ReactNode;
  className?: string;
  srOnlyLabel?: boolean;
}

function Shell({
  id,
  label,
  hint,
  error,
  className,
  srOnlyLabel,
  children,
}: FieldShell & { id: string; children: ReactNode }) {
  return (
    <div className={clsx("min-w-0", className)}>
      <label htmlFor={id} className={clsx("mb-1.5 block text-sm font-medium text-fg", srOnlyLabel && "sr-only")}>
        {label}
      </label>
      {children}
      {error ? (
        <p id={`${id}-err`} className="mt-1.5 text-xs font-medium text-rose-600 dark:text-rose-400" role="alert">
          {error}
        </p>
      ) : hint ? (
        <p id={`${id}-hint`} className="mt-1.5 text-xs text-fg-muted">
          {hint}
        </p>
      ) : null}
    </div>
  );
}

export function TextField({
  label,
  hint,
  error,
  className,
  srOnlyLabel,
  inputClassName,
  ...rest
}: FieldShell & InputHTMLAttributes<HTMLInputElement> & { inputClassName?: string }) {
  const autoId = useId();
  const id = rest.id ?? autoId;
  return (
    <Shell id={id} label={label} hint={hint} error={error} className={className} srOnlyLabel={srOnlyLabel}>
      <input
        id={id}
        className={clsx(control, "h-11", inputClassName)}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-err` : hint ? `${id}-hint` : undefined}
        {...rest}
      />
    </Shell>
  );
}

export function TextArea({ label, hint, error, className, srOnlyLabel, ...rest }: FieldShell & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const autoId = useId();
  const id = rest.id ?? autoId;
  return (
    <Shell id={id} label={label} hint={hint} error={error} className={className} srOnlyLabel={srOnlyLabel}>
      <textarea
        id={id}
        rows={3}
        className={clsx(control, "py-2.5 leading-relaxed")}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-err` : hint ? `${id}-hint` : undefined}
        {...rest}
      />
    </Shell>
  );
}

export function SelectField({
  label,
  hint,
  error,
  className,
  srOnlyLabel,
  children,
  ...rest
}: FieldShell & SelectHTMLAttributes<HTMLSelectElement>) {
  const autoId = useId();
  const id = rest.id ?? autoId;
  return (
    <Shell id={id} label={label} hint={hint} error={error} className={className} srOnlyLabel={srOnlyLabel}>
      <select
        id={id}
        className={clsx(control, "h-11 appearance-none bg-[length:16px] bg-[right_0.75rem_center] bg-no-repeat pr-9")}
        style={{
          backgroundImage:
            "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%238a8fa3' stroke-width='2'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E\")",
        }}
        aria-invalid={error ? true : undefined}
        {...rest}
      >
        {children}
      </select>
    </Shell>
  );
}
