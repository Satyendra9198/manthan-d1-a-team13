/** Display helpers. Money is integer paise; dates are shown in Asia/Kolkata. */

export const IST = "Asia/Kolkata";

const groupFmt = new Intl.NumberFormat("en-IN", { maximumFractionDigits: 0 });

/** 12345678 → "₹1,23,456.78". Exact integer arithmetic, no float rounding. */
export function formatPaise(paise: number | null | undefined, opts: { compact?: boolean } = {}): string {
  if (paise === null || paise === undefined || !Number.isFinite(paise)) return "—";
  const p = Math.trunc(paise);
  const neg = p < 0;
  const abs = Math.abs(p);
  const rupees = Math.floor(abs / 100);
  const fraction = abs % 100;
  if (opts.compact && rupees >= 100000) {
    const lakhs = rupees / 100000;
    const s = lakhs >= 100 ? `${(lakhs / 100).toFixed(2)} Cr` : `${lakhs.toFixed(2)} L`;
    return `${neg ? "-" : ""}₹${s}`;
  }
  return `${neg ? "-" : ""}₹${groupFmt.format(rupees)}.${fraction.toString().padStart(2, "0")}`;
}

export function formatNumber(n: number | null | undefined, digits = 0): string {
  if (n === null || n === undefined || !Number.isFinite(n)) return "—";
  return new Intl.NumberFormat("en-IN", { maximumFractionDigits: digits, minimumFractionDigits: digits }).format(n);
}

export function formatPct(n: number | null | undefined, digits = 1): string {
  if (n === null || n === undefined || !Number.isFinite(n)) return "—";
  return `${formatNumber(n, digits)}%`;
}

/** Hours → "2d 4h" / "5.5h". */
export function formatHours(h: number | null | undefined): string {
  if (h === null || h === undefined || !Number.isFinite(h)) return "—";
  if (h >= 48) {
    const d = Math.floor(h / 24);
    const rem = Math.round(h - d * 24);
    return rem ? `${d}d ${rem}h` : `${d}d`;
  }
  return `${formatNumber(h, h < 10 ? 1 : 0)}h`;
}

const dateTimeFmt = new Intl.DateTimeFormat("en-IN", {
  timeZone: IST,
  day: "2-digit",
  month: "short",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  hour12: true,
});
const dateFmt = new Intl.DateTimeFormat("en-IN", { timeZone: IST, day: "2-digit", month: "short", year: "numeric" });
const shortDateFmt = new Intl.DateTimeFormat("en-IN", { timeZone: IST, day: "2-digit", month: "short" });

function parse(iso: string | null | undefined): Date | null {
  if (!iso) return null;
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? null : d;
}

/** ISO timestamp → "05 Nov 2026, 2:30 pm" (IST). */
export function formatDateTime(iso: string | null | undefined): string {
  const d = parse(iso);
  return d ? dateTimeFmt.format(d) : "—";
}

/** ISO timestamp → "05 Nov 2026" (IST calendar date). Date-only strings are not shifted. */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  if (/^\d{4}-\d{2}-\d{2}$/.test(iso)) return formatCalendarDate(iso);
  const d = parse(iso);
  return d ? dateFmt.format(d) : "—";
}

export function formatShortDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  if (/^\d{4}-\d{2}-\d{2}$/.test(iso)) return formatCalendarDate(iso, true);
  const d = parse(iso);
  return d ? shortDateFmt.format(d) : "—";
}

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

/** "2026-10-31" → "31 Oct 2026" — a calendar date already evaluated in IST by the backend [A-02]. */
export function formatCalendarDate(ymd: string, short = false): string {
  const [y, m, d] = ymd.split("-");
  const month = MONTHS[Number(m) - 1] ?? m;
  return short ? `${d} ${month}` : `${d} ${month} ${y}`;
}

export function relativeTime(iso: string | null | undefined, now: number): string {
  const d = parse(iso);
  if (!d) return "";
  const diff = Math.round((now - d.getTime()) / 1000);
  if (diff < 45) return "just now";
  const rtf = new Intl.RelativeTimeFormat("en-IN", { numeric: "auto" });
  if (diff < 3600) return rtf.format(-Math.round(diff / 60), "minute");
  if (diff < 86400) return rtf.format(-Math.round(diff / 3600), "hour");
  if (diff < 86400 * 30) return rtf.format(-Math.round(diff / 86400), "day");
  return formatDate(iso);
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase() ?? "")
    .join("");
}

/** Grade C refunds half, rounded up to the paise: (price + 1) // 2 [A-10]. Estimate only — backend is authoritative. */
export function estimateItemRefund(pricePaise: number, decision: "ACCEPT" | "REJECT" | null, grade: "A" | "B" | "C" | null): number {
  if (decision !== "ACCEPT" || !grade) return 0;
  return grade === "C" ? Math.floor((pricePaise + 1) / 2) : pricePaise;
}

/** ₹10,000.00 finance threshold, strictly greater, after grade [A-12]. */
export const FINANCE_THRESHOLD_PAISE = 1_000_000;
