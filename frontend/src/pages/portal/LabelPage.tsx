import { clsx } from "clsx";
import { AlertTriangle, Printer, Scissors } from "lucide-react";
import { useParams } from "react-router";
import { useLabel } from "../../api/hooks/customer";
import { ErrorState } from "../../components/ErrorState";
import { PageHeader } from "../../components/PageHeader";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Skeleton } from "../../components/ui/Skeleton";
import { formatDate, formatDateTime } from "../../lib/format";
import { humanize } from "../../lib/labels";

/** Printable shipping label. Print CSS hides the app chrome; the label fits one A4 page. */
export function LabelPage() {
  const { id = "" } = useParams();
  const { data: label, isPending, error, refetch } = useLabel(id);

  if (isPending) return <Skeleton className="mx-auto h-[560px] max-w-2xl rounded-3xl" />;
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />;

  const active = label.status === "ACTIVE";
  // qr_svg is a data: URI from our backend; only allow image data URIs.
  const qrSrc = label.qr_svg.startsWith("data:image/") ? label.qr_svg : "";

  return (
    <>
      <PageHeader
        back={{ to: `/portal/returns/${id}`, label: "Back to return" }}
        eyebrow="Shipping label"
        title="Print your return label"
        description="Print this page, fold it, and stick it on the parcel. One label per parcel."
        actions={
          <Button onClick={() => window.print()} icon={<Printer className="size-4" />} disabled={!active} data-testid="print-label">
            Print label
          </Button>
        }
      />

      {!active ? (
        <div role="alert" className="mb-4 flex gap-3 rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/50 dark:text-amber-100 print:hidden">
          <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
          <p>This label is {humanize(label.status).toLowerCase()} and can't be used to ship a parcel.</p>
        </div>
      ) : null}

      <article
        className={clsx(
          "mx-auto max-w-2xl overflow-hidden rounded-3xl border-2 border-dashed border-line-strong bg-white text-slate-900 shadow-card print:max-w-none print:rounded-none print:border-slate-900 print:shadow-none",
          !active && "opacity-60",
        )}
        data-testid="shipping-label"
        aria-label="Shipping label"
      >
        <div className="flex items-center justify-between bg-slate-900 px-6 py-4 text-white print:bg-slate-900">
          <div className="flex items-center gap-2.5">
            <img src="/favicon.svg" alt="" className="size-7" />
            <span className="text-lg font-semibold tracking-tight">ReturnRight</span>
          </div>
          <span className="text-xs font-semibold tracking-[0.2em] uppercase">Return parcel</span>
        </div>

        <div className="grid gap-6 p-6 sm:grid-cols-[auto_1fr]">
          <div className="flex flex-col items-center">
            {qrSrc ? (
              <img src={qrSrc} alt={`QR code for return ${label.rma_number}`} className="size-48 [image-rendering:pixelated] sm:size-52" data-testid="label-qr" />
            ) : (
              <div className="grid size-48 place-items-center rounded-xl bg-slate-100 text-sm text-slate-500">QR unavailable</div>
            )}
            <p className="mt-2 max-w-48 text-center font-mono text-[10px] break-all text-slate-500">{label.qr_token}</p>
          </div>
          <div className="min-w-0 space-y-4">
            <div>
              <p className="text-[11px] font-semibold tracking-wider text-slate-500 uppercase">RMA</p>
              <p className="font-mono text-3xl font-bold tracking-tight" data-testid="label-rma">
                {label.rma_number}
              </p>
            </div>
            <div>
              <p className="text-[11px] font-semibold tracking-wider text-slate-500 uppercase">Tracking number</p>
              <p className="font-mono text-lg font-semibold">{label.tracking_number}</p>
            </div>
            <div className="flex flex-wrap gap-2">
              <Badge tone={active ? "success" : "neutral"} className="print:ring-slate-400">
                {humanize(label.status)}
              </Badge>
              <span className="text-xs text-slate-500">
                Issued {formatDateTime(label.issued_at)} · Valid until <strong className="text-slate-800">{formatDate(label.expires_at)}</strong>
              </span>
            </div>
          </div>
        </div>

        <div className="grid border-t-2 border-dashed border-slate-300 sm:grid-cols-2">
          <div className="p-6 sm:border-r-2 sm:border-dashed sm:border-slate-300">
            <p className="text-[11px] font-semibold tracking-wider text-slate-500 uppercase">From</p>
            <p className="mt-1 font-semibold">{label.customer_name}</p>
          </div>
          <div className="border-t-2 border-dashed border-slate-300 p-6 sm:border-t-0">
            <p className="text-[11px] font-semibold tracking-wider text-slate-500 uppercase">Ship to</p>
            <address className="mt-1 not-italic">
              <span className="block text-lg font-bold">{label.ship_to.name}</span>
              <span className="block">{label.ship_to.line1}</span>
              <span className="block">
                {label.ship_to.city} — <span className="font-mono font-semibold">{label.ship_to.pincode}</span>
              </span>
            </address>
          </div>
        </div>

        <div className="border-t-2 border-dashed border-slate-300 p-6">
          <p className="text-[11px] font-semibold tracking-wider text-slate-500 uppercase">Contents ({label.items.length})</p>
          <ul className="mt-2 space-y-1 text-sm">
            {label.items.map((it, i) => (
              <li key={`${it.product_name}-${it.unit_index}-${i}`} className="flex justify-between gap-3">
                <span>{it.product_name}</span>
                <span className="text-slate-500">unit {it.unit_index}</span>
              </li>
            ))}
          </ul>
        </div>
        <div className="flex items-center gap-2 bg-slate-50 px-6 py-3 text-xs text-slate-500">
          <Scissors className="size-3.5" aria-hidden /> Cut along the dashed line. Do not cover the QR code with tape.
        </div>
      </article>
    </>
  );
}
