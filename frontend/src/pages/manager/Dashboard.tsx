import { clsx } from "clsx";
import {
  Activity,
  CheckCircle2,
  Clock,
  Flag,
  IndianRupee,
  Inbox,
  Percent,
  RotateCcw,
  Timer,
  Wallet,
} from "lucide-react";
import { motion, useReducedMotion } from "motion/react";
import { useCallback, useState } from "react";
import type { ReactNode } from "react";
import { Link, useSearchParams } from "react-router";
import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { useOverview } from "../../api/hooks/manager";
import type { AnalyticsOverview, ReturnStatus } from "../../api/types";
import { ChartTooltip, Legend } from "../../components/charts/ChartTooltip";
import { ReconciliationCard } from "../../components/ReconciliationCard";
import { ErrorState } from "../../components/ErrorState";
import { PageHeader } from "../../components/PageHeader";
import { Card, CardHeader } from "../../components/ui/Card";
import { CountUp } from "../../components/ui/CountUp";
import { EmptyState } from "../../components/ui/EmptyState";
import { Segmented } from "../../components/ui/Segmented";
import { Skeleton } from "../../components/ui/Skeleton";
import { formatCalendarDate, formatHours, formatNumber, formatPaise, formatPct } from "../../lib/format";
import { RETURN_REASON_LABEL } from "../../lib/labels";

type Days = "7" | "30" | "90";

const S1 = "var(--rr-series-1)";
const S2 = "var(--rr-series-2)";
const AXIS = { fontSize: 11, fill: "var(--rr-fg-subtle)" };

/** Status buckets keep the donut at ≤5 slices; color follows the bucket (fixed slots), never rank. */
const BUCKETS: { key: string; label: string; color: string; statuses: ReturnStatus[] }[] = [
  { key: "open", label: "Awaiting parcel", color: "var(--rr-series-1)", statuses: ["REQUESTED", "APPROVED", "IN_TRANSIT"] },
  { key: "wh", label: "In warehouse", color: "var(--rr-series-2)", statuses: ["RECEIVED", "INSPECTED"] },
  { key: "refunded", label: "Refunded", color: "var(--rr-series-3)", statuses: ["REFUNDED"] },
  { key: "fin", label: "Finance / refund in progress", color: "var(--rr-series-4)", statuses: ["PENDING_FINANCE", "REFUND_PENDING", "REFUND_FAILED"] },
  { key: "closed", label: "Rejected / cancelled", color: "var(--rr-series-5)", statuses: ["REJECTED", "CANCELLED"] },
];

function isDays(v: string | null): v is Days {
  return v === "7" || v === "30" || v === "90";
}

export function Dashboard() {
  const [params, setParams] = useSearchParams();
  const initial = params.get("days");
  const [days, setDays] = useState<Days>(isDays(initial) ? initial : "30");
  const { data, isPending, error, refetch, isFetching } = useOverview(Number(days));

  return (
    <>
      <PageHeader
        eyebrow="Operations"
        title="Returns dashboard"
        description="Volume, refunds, cycle time and risk across the selected window."
        actions={
          <div className="w-full sm:w-64">
            <Segmented<Days>
              name="window"
              label="Time window"
              value={days}
              onChange={(d) => {
                setDays(d);
                setParams({ days: d }, { replace: true });
              }}
              options={[
                { value: "7", label: "7 days" },
                { value: "30", label: "30 days" },
                { value: "90", label: "90 days" },
              ]}
              testId="window"
            />
          </div>
        }
      />
      {isPending ? (
        <DashboardSkeleton />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : (
        <div className={clsx("space-y-6 transition-opacity", isFetching && "opacity-80")} data-testid="dashboard">
          <Kpis data={data} />
          <div className="grid gap-6 xl:grid-cols-2">
            <CategoryChart data={data} />
            <ReasonsChart data={data} />
          </div>
          <DailyChart data={data} />
          <div className="grid gap-6 xl:grid-cols-[1.2fr_1fr]">
            <CycleTime data={data} />
            <StatusDonut data={data} />
          </div>
          <ReconciliationCard />
        </div>
      )}
    </>
  );
}

function DashboardSkeleton() {
  return (
    <div className="space-y-6" role="status" aria-label="Loading dashboard">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {Array.from({ length: 8 }, (_, i) => (
          <Skeleton key={i} className="h-28 rounded-2xl" />
        ))}
      </div>
      <div className="grid gap-6 xl:grid-cols-2">
        <Skeleton className="h-80 rounded-2xl" />
        <Skeleton className="h-80 rounded-2xl" />
      </div>
      <Skeleton className="h-72 rounded-2xl" />
    </div>
  );
}

function Kpi({ label, icon, value, format, sub, index, testId }: { label: string; icon: ReactNode; value: number | null; format: (n: number) => string; sub?: ReactNode; index: number; testId: string }) {
  const reduce = useReducedMotion();
  return (
    <motion.div
      initial={reduce ? false : { opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.04 }}
      className="rounded-2xl border border-line bg-surface p-4 shadow-card sm:p-5"
      data-testid={testId}
    >
      <div className="flex items-center justify-between gap-2">
        <p className="text-xs font-medium text-fg-muted sm:text-sm">{label}</p>
        <span className="grid size-8 place-items-center rounded-lg bg-brand-soft text-brand-soft-fg [&>svg]:size-4" aria-hidden>
          {icon}
        </span>
      </div>
      <p className="mt-2 text-xl font-semibold tracking-tight text-fg sm:text-[26px]">
        {value === null ? "—" : <CountUp value={value} format={format} />}
      </p>
      {sub ? <p className="mt-1 text-xs text-fg-subtle">{sub}</p> : null}
    </motion.div>
  );
}

function Kpis({ data }: { data: AnalyticsOverview }) {
  const k = data.kpis;
  const int = useCallback((n: number) => formatNumber(Math.round(n)), []);
  const pct = useCallback((n: number) => formatPct(n), []);
  const money = useCallback((n: number) => formatPaise(Math.round(n), { compact: true }), []);
  const hours = useCallback((n: number) => formatHours(n), []);
  return (
    <div className="grid grid-cols-2 gap-3 sm:gap-4 lg:grid-cols-4">
      <Kpi index={0} label="Returns" icon={<RotateCcw />} value={k.returns_count} format={int} sub={`Last ${data.window_days} days`} testId="kpi-returns" />
      <Kpi index={1} label="Return rate" icon={<Percent />} value={k.return_rate_pct} format={pct} sub="Units returned / sold" testId="kpi-return-rate" />
      <Kpi index={2} label="Refunded" icon={<IndianRupee />} value={k.refunded_paise} format={money} sub={`${formatNumber(k.refund_count)} refunds`} testId="kpi-refunded" />
      <Kpi
        index={3}
        label="Refund success"
        icon={<CheckCircle2 />}
        value={k.refund_success_rate === null ? null : k.refund_success_rate <= 1 ? k.refund_success_rate * 100 : k.refund_success_rate}
        format={pct}
        sub="Gateway attempts"
        testId="kpi-success"
      />
      <Kpi index={4} label="Open returns" icon={<Inbox />} value={k.open_returns} format={int} testId="kpi-open" />
      <Kpi index={5} label="Avg cycle time" icon={<Timer />} value={k.avg_cycle_hours} format={hours} sub="Request → refund" testId="kpi-cycle" />
      <Kpi index={6} label="Refund count" icon={<Wallet />} value={k.refund_count} format={int} testId="kpi-refund-count" />
      <Kpi
        index={7}
        label="Flagged customers"
        icon={<Flag />}
        value={k.flagged_customers}
        format={int}
        sub={
          <Link to="/manager/flagged" className="font-medium text-brand hover:underline">
            Review flags
          </Link>
        }
        testId="kpi-flagged"
      />
    </div>
  );
}

function ChartCard({ title, description, children, legend, empty }: { title: string; description?: string; children: ReactNode; legend?: ReactNode; empty?: boolean }) {
  return (
    <Card>
      <CardHeader title={title} description={description} />
      <div className="px-3 pt-4 pb-4 sm:px-5">
        {legend ? <div className="mb-3 px-2 sm:px-0">{legend}</div> : null}
        {empty ? <EmptyState icon={<Activity />} title="No data in this window" className="py-10" /> : children}
      </div>
    </Card>
  );
}

function CategoryChart({ data }: { data: AnalyticsOverview }) {
  const rows = [...data.return_rate_by_category].sort((a, b) => b.return_rate_pct - a.return_rate_pct).slice(0, 10);
  return (
    <ChartCard title="Return rate by category" description="Units returned ÷ units sold" empty={rows.length === 0}>
      <div className="h-72" role="img" aria-label="Bar chart of return rate by category">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows} margin={{ top: 8, right: 8, left: -12, bottom: 0 }} barCategoryGap={6}>
            <CartesianGrid vertical={false} stroke="var(--rr-grid)" />
            <XAxis dataKey="category_name" tick={AXIS} tickLine={false} axisLine={false} interval={0} angle={-25} textAnchor="end" height={56} />
            <YAxis tick={AXIS} tickLine={false} axisLine={false} tickFormatter={(v: number) => `${v}%`} width={44} />
            <Tooltip
              cursor={{ fill: "var(--rr-surface-2)" }}
              content={(p) => (
                <ChartTooltip
                  active={p.active}
                  payload={p.payload}
                  label={p.label}
                  formatValue={(v) => formatPct(v)}
                />
              )}
            />
            <Bar dataKey="return_rate_pct" name="Return rate" fill={S1} radius={[4, 4, 0, 0]} maxBarSize={36} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </ChartCard>
  );
}

function ReasonsChart({ data }: { data: AnalyticsOverview }) {
  const rows = [...data.top_reasons]
    .sort((a, b) => b.count - a.count)
    .slice(0, 8)
    .map((r) => ({ ...r, label: RETURN_REASON_LABEL[r.reason] ?? r.reason }));
  return (
    <ChartCard title="Top return reasons" description="Share of returned units" empty={rows.length === 0}>
      <div className="h-72" role="img" aria-label="Horizontal bar chart of top return reasons">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows} layout="vertical" margin={{ top: 0, right: 40, left: 8, bottom: 0 }} barCategoryGap={6}>
            <CartesianGrid horizontal={false} stroke="var(--rr-grid)" />
            <XAxis type="number" tick={AXIS} tickLine={false} axisLine={false} allowDecimals={false} />
            <YAxis type="category" dataKey="label" tick={{ ...AXIS, fill: "var(--rr-fg-muted)" }} tickLine={false} axisLine={false} width={130} />
            <Tooltip
              cursor={{ fill: "var(--rr-surface-2)" }}
              content={(p) => (
                <ChartTooltip
                  active={p.active}
                  payload={p.payload}
                  label={p.label}
                  formatValue={(v, row) => {
                    const pctRow = rows.find((r) => r.label === p.label);
                    void row;
                    return `${formatNumber(v)}${pctRow ? ` · ${formatPct(pctRow.pct)}` : ""}`;
                  }}
                />
              )}
            />
            <Bar dataKey="count" name="Units" fill={S1} radius={[0, 4, 4, 0]} maxBarSize={22} label={{ position: "right", fill: "var(--rr-fg-muted)", fontSize: 11 }} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </ChartCard>
  );
}

function DailyChart({ data }: { data: AnalyticsOverview }) {
  const rows = data.daily;
  return (
    <ChartCard
      title="Daily returns"
      description="Requested vs refunded per day (IST)"
      empty={rows.length === 0}
      legend={
        <Legend
          items={[
            { label: "Requested", color: S1 },
            { label: "Refunded", color: S2 },
          ]}
        />
      }
    >
      <div className="h-64" role="img" aria-label="Area chart of daily requested and refunded returns">
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={rows} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
            <defs>
              <linearGradient id="gReq" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={S1} stopOpacity={0.28} />
                <stop offset="100%" stopColor={S1} stopOpacity={0} />
              </linearGradient>
              <linearGradient id="gRef" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={S2} stopOpacity={0.22} />
                <stop offset="100%" stopColor={S2} stopOpacity={0} />
              </linearGradient>
            </defs>
            <CartesianGrid vertical={false} stroke="var(--rr-grid)" />
            <XAxis dataKey="date" tick={AXIS} tickLine={false} axisLine={false} tickFormatter={(d: string) => formatCalendarDate(d, true)} minTickGap={24} />
            <YAxis tick={AXIS} tickLine={false} axisLine={false} allowDecimals={false} width={40} />
            <Tooltip
              cursor={{ stroke: "var(--rr-line-strong)", strokeWidth: 1 }}
              content={(p) => (
                <ChartTooltip active={p.active} payload={p.payload} label={p.label} formatLabel={(l) => formatCalendarDate(String(l))} />
              )}
            />
            <Area type="monotone" dataKey="requested" name="Requested" stroke={S1} strokeWidth={2} fill="url(#gReq)" activeDot={{ r: 4, strokeWidth: 2, stroke: "var(--rr-surface)" }} />
            <Area type="monotone" dataKey="refunded" name="Refunded" stroke={S2} strokeWidth={2} fill="url(#gRef)" activeDot={{ r: 4, strokeWidth: 2, stroke: "var(--rr-surface)" }} />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </ChartCard>
  );
}

function CycleTime({ data }: { data: AnalyticsOverview }) {
  const c = data.cycle_time;
  const stages = [
    { label: "Request → received", v: c.stages.request_to_receive_hours },
    { label: "Received → inspected", v: c.stages.receive_to_inspect_hours },
    { label: "Inspected → refunded", v: c.stages.inspect_to_refund_hours },
  ];
  const max = Math.max(1, ...stages.map((s) => s.v ?? 0));
  return (
    <Card>
      <CardHeader title="Cycle time" description="From request to refund" icon={<Clock className="size-4" />} />
      <div className="p-5">
        <div className="grid grid-cols-3 gap-3">
          {(
            [
              ["Average", c.avg_hours],
              ["Median (p50)", c.p50_hours],
              ["p90", c.p90_hours],
            ] as const
          ).map(([l, v]) => (
            <div key={l} className="rounded-xl bg-surface-2 p-3">
              <p className="text-xs text-fg-muted">{l}</p>
              <p className="mt-1 text-lg font-semibold tracking-tight tabular sm:text-xl">{formatHours(v)}</p>
            </div>
          ))}
        </div>
        <ul className="mt-5 space-y-3">
          {stages.map((s) => (
            <li key={s.label}>
              <div className="mb-1 flex justify-between text-sm">
                <span className="text-fg-muted">{s.label}</span>
                <span className="font-medium tabular">{formatHours(s.v)}</span>
              </div>
              <div className="h-2 overflow-hidden rounded-full bg-surface-2">
                <motion.div
                  className="h-full rounded-full"
                  style={{ background: S1 }}
                  initial={{ width: 0 }}
                  animate={{ width: `${((s.v ?? 0) / max) * 100}%` }}
                  transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
                />
              </div>
            </li>
          ))}
        </ul>
      </div>
    </Card>
  );
}

function StatusDonut({ data }: { data: AnalyticsOverview }) {
  const rows = BUCKETS.map((b) => ({
    name: b.label,
    color: b.color,
    value: data.returns_by_status.filter((r) => b.statuses.includes(r.status)).reduce((s, r) => s + r.count, 0),
  }));
  const total = rows.reduce((s, r) => s + r.value, 0);
  return (
    <ChartCard title="Returns by status" description="Current state of returns in this window" empty={total === 0}>
      <div className="flex flex-col items-center gap-5 sm:flex-row">
        <div className="relative size-48 shrink-0" role="img" aria-label="Donut chart of returns by status">
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie data={rows} dataKey="value" nameKey="name" innerRadius="64%" outerRadius="100%" paddingAngle={1.5} stroke="var(--rr-surface)" strokeWidth={2}>
                {rows.map((r) => (
                  <Cell key={r.name} fill={r.color} />
                ))}
              </Pie>
              <Tooltip content={(p) => <ChartTooltip active={p.active} payload={p.payload} />} />
            </PieChart>
          </ResponsiveContainer>
          <div className="pointer-events-none absolute inset-0 grid place-items-center text-center">
            <div>
              <p className="text-2xl font-semibold tabular">{formatNumber(total)}</p>
              <p className="text-xs text-fg-muted">returns</p>
            </div>
          </div>
        </div>
        <table className="w-full text-sm">
          <caption className="sr-only">Returns by status</caption>
          <tbody>
            {rows.map((r) => (
              <tr key={r.name} className="border-b border-line last:border-0">
                <td className="py-2">
                  <span className="flex items-center gap-2 text-fg-muted">
                    <span className="size-2.5 shrink-0 rounded-sm" style={{ background: r.color }} aria-hidden />
                    {r.name}
                  </span>
                </td>
                <td className="py-2 text-right font-medium tabular">{formatNumber(r.value)}</td>
                <td className="w-14 py-2 text-right text-xs text-fg-subtle tabular">{total ? formatPct((r.value / total) * 100, 0) : "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </ChartCard>
  );
}
