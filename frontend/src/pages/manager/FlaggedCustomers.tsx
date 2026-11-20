import { FlagOff, ShieldCheck } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { shouldCloseDialogOnError } from "../../api/hooks/common";
import { useClearFlag, useFlaggedCustomers } from "../../api/hooks/manager";
import type { FlaggedCustomer } from "../../api/types";
import { ErrorState } from "../../components/ErrorState";
import { PageHeader } from "../../components/PageHeader";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Dialog } from "../../components/ui/Dialog";
import { EmptyState } from "../../components/ui/EmptyState";
import { TextArea } from "../../components/ui/Field";
import { SkeletonTable } from "../../components/ui/Skeleton";
import { formatDateTime, formatPaise } from "../../lib/format";
import { humanize } from "../../lib/labels";
import { toastError } from "../../lib/notify";
import { useConfirm } from "../../lib/useConfirm";

export function FlaggedCustomers() {
  const { data, isPending, error, refetch } = useFlaggedCustomers();
  const clear = useClearFlag();
  const dlg = useConfirm<FlaggedCustomer>();
  const [note, setNote] = useState("");

  function openFor(c: FlaggedCustomer) {
    setNote("");
    dlg.open(c);
  }

  return (
    <>
      <PageHeader
        eyebrow="Operations"
        title="Flagged customers"
        description="Customers with more than 3 return requests in 30 days. Every return they make goes to finance review [A-13, A-14]."
      />
      <Card className="overflow-hidden">
        {isPending ? (
          <SkeletonTable />
        ) : error ? (
          <ErrorState error={error} onRetry={() => void refetch()} />
        ) : data.items.length === 0 ? (
          <EmptyState icon={<ShieldCheck />} title="No flagged customers" description="Nobody is over the 30-day return threshold right now." />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[720px] text-sm" data-testid="flagged-table">
              <thead>
                <tr className="border-b border-line text-left text-xs font-semibold tracking-wide text-fg-subtle uppercase">
                  <th className="px-5 py-3">Customer</th>
                  <th className="px-3 py-3 text-right">Returns 30d</th>
                  <th className="px-3 py-3 text-right">Open</th>
                  <th className="px-3 py-3 text-right">Refunded 90d</th>
                  <th className="px-3 py-3">Flagged</th>
                  <th className="px-5 py-3 text-right">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line">
                {data.items.map((c) => (
                  <tr key={c.customer_id} className="hover:bg-surface-2/50" data-testid="flagged-row">
                    <td className="px-5 py-3.5">
                      <p className="font-medium">{c.name}</p>
                      <p className="font-mono text-xs text-fg-muted">{c.email_masked}</p>
                    </td>
                    <td className="px-3 py-3.5 text-right">
                      <Badge tone="danger">{c.returns_30d}</Badge>
                    </td>
                    <td className="px-3 py-3.5 text-right tabular">{c.open_returns}</td>
                    <td className="px-3 py-3.5 text-right font-medium tabular">{formatPaise(c.refunded_paise_90d)}</td>
                    <td className="px-3 py-3.5 text-xs text-fg-muted">
                      {formatDateTime(c.flagged_at)}
                      {c.flag_reason ? <span className="block">{humanize(c.flag_reason)}</span> : null}
                    </td>
                    <td className="px-5 py-3.5 text-right">
                      <Button size="sm" variant="outline" onClick={() => openFor(c)} icon={<FlagOff className="size-3.5" />} data-testid="clear-flag">
                        Clear flag
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <Dialog
        open={dlg.isOpen}
        onClose={dlg.close}
        busy={clear.isPending}
        title={`Clear flag for ${dlg.target?.name ?? "customer"}?`}
        description="Their future returns will follow normal routing again. The note is recorded in the audit log [A-15]."
        testId="clear-flag-dialog"
        footer={
          <>
            <Button variant="outline" onClick={dlg.close} disabled={clear.isPending}>
              Cancel
            </Button>
            <Button
              loading={clear.isPending}
              disabled={note.trim().length < 3 || !dlg.target}
              data-testid="confirm-clear-flag"
              onClick={() => {
                const target = dlg.target;
                if (!target) return;
                clear.mutate(
                  { customerId: target.customer_id, note, idempotencyKey: dlg.idempotencyKey },
                  {
                    onSuccess: () => {
                      toast.success(`Flag cleared for ${target.name}`);
                      dlg.close();
                    },
                    onError: (e) => {
                      toastError(e);
                      if (shouldCloseDialogOnError(e)) dlg.close();
                    },
                  },
                );
              }}
            >
              Clear flag
            </Button>
          </>
        }
      >
        <TextArea label="Note (required)" value={note} onChange={(e) => setNote(e.target.value)} maxLength={500} required data-testid="clear-flag-note" />
      </Dialog>
    </>
  );
}
