import type { ReactNode } from "react";

export interface TooltipRow {
  name?: unknown;
  value?: unknown;
  color?: string;
  dataKey?: unknown;
}

/** Shared tooltip body: text in ink tokens, a colored swatch carries identity. */
export function ChartTooltip({
  active,
  payload,
  label,
  formatValue,
  formatLabel,
}: {
  active?: boolean;
  payload?: readonly TooltipRow[];
  label?: unknown;
  formatValue?: (v: number, row: TooltipRow) => string;
  formatLabel?: (l: unknown) => ReactNode;
}) {
  if (!active || !payload || payload.length === 0) return null;
  return (
    <div className="min-w-36 rounded-xl border border-line bg-elevated px-3 py-2 text-xs shadow-pop">
      {label !== undefined && label !== null && label !== "" ? (
        <p className="mb-1.5 font-semibold text-fg">{formatLabel ? formatLabel(label) : typeof label === "string" || typeof label === "number" ? label : ""}</p>
      ) : null}
      <ul className="space-y-1">
        {payload.map((row, i) => {
          const v = typeof row.value === "number" ? row.value : Number(row.value);
          return (
            <li key={i} className="flex items-center justify-between gap-4">
              <span className="flex items-center gap-1.5 text-fg-muted">
                <span className="size-2.5 rounded-sm" style={{ background: row.color }} aria-hidden />
                {String(row.name)}
              </span>
              <span className="font-semibold text-fg tabular">{formatValue ? formatValue(v, row) : v.toLocaleString("en-IN")}</span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

export function Legend({ items }: { items: { label: string; color: string }[] }) {
  return (
    <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-fg-muted">
      {items.map((i) => (
        <li key={i.label} className="flex items-center gap-1.5">
          <span className="size-2.5 rounded-sm" style={{ background: i.color }} aria-hidden />
          {i.label}
        </li>
      ))}
    </ul>
  );
}
