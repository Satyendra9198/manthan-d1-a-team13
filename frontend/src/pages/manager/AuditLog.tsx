import { ArrowRight, FileSearch, Search, X } from "lucide-react";
import { useState } from "react";
import type { FormEvent } from "react";
import { useSearchParams } from "react-router";
import { useAudit } from "../../api/hooks/manager";
import { ErrorState } from "../../components/ErrorState";
import { PageHeader } from "../../components/PageHeader";
import { Badge } from "../../components/ui/Badge";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { EmptyState } from "../../components/ui/EmptyState";
import { TextField } from "../../components/ui/Field";
import { SkeletonTable } from "../../components/ui/Skeleton";
import { formatDateTime } from "../../lib/format";
import { humanize } from "../../lib/labels";

export function AuditLog() {
  const [params, setParams] = useSearchParams();
  const entity = params.get("entity_id") ?? "";
  const [input, setInput] = useState(entity);
  const [limit, setLimit] = useState(50);
  const { data, isPending, error, refetch, isFetching } = useAudit(entity, limit);

  function submit(e: FormEvent) {
    e.preventDefault();
    const v = input.trim();
    setLimit(50);
    setParams(v ? { entity_id: v } : {}, { replace: true });
  }

  return (
    <>
      <PageHeader eyebrow="Operations" title="Audit log" description="Every state change, append-only. Search by return, refund or customer id." />
      <Card className="mb-4 p-4">
        <form onSubmit={submit} className="flex flex-col gap-3 sm:flex-row sm:items-end" role="search">
          <TextField
            label="Entity id"
            srOnlyLabel
            className="flex-1"
            placeholder="Return / refund / customer id…"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            spellCheck={false}
            data-testid="audit-search"
          />
          <div className="flex gap-2">
            <Button type="submit" icon={<Search className="size-4" />} loading={isFetching && !isPending}>
              Search
            </Button>
            {entity ? (
              <Button
                variant="ghost"
                icon={<X className="size-4" />}
                onClick={() => {
                  setInput("");
                  setParams({}, { replace: true });
                }}
              >
                Clear
              </Button>
            ) : null}
          </div>
        </form>
      </Card>
      <Card className="overflow-hidden">
        {isPending ? (
          <SkeletonTable rows={10} />
        ) : error ? (
          <ErrorState error={error} onRetry={() => void refetch()} />
        ) : data.items.length === 0 ? (
          <EmptyState icon={<FileSearch />} title="No audit entries" description={entity ? `Nothing recorded for “${entity}”.` : "Nothing recorded yet."} />
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[860px] text-sm" data-testid="audit-table">
                <thead>
                  <tr className="border-b border-line text-left text-xs font-semibold tracking-wide text-fg-subtle uppercase">
                    <th className="px-5 py-3">When (IST)</th>
                    <th className="px-3 py-3">Event</th>
                    <th className="px-3 py-3">Transition</th>
                    <th className="px-3 py-3">Entity</th>
                    <th className="px-3 py-3">Actor</th>
                    <th className="px-5 py-3">Correlation</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {data.items.map((a) => (
                    <tr key={a.id} className="align-top hover:bg-surface-2/50" data-testid="audit-row">
                      <td className="px-5 py-3 text-xs whitespace-nowrap text-fg-muted">{formatDateTime(a.occurred_at)}</td>
                      <td className="px-3 py-3">
                        <span className="font-mono text-xs font-semibold">{a.event}</span>
                      </td>
                      <td className="px-3 py-3">
                        {a.from_state || a.to_state ? (
                          <span className="inline-flex flex-wrap items-center gap-1">
                            <Badge tone="neutral">{a.from_state ?? "∅"}</Badge>
                            <ArrowRight className="size-3 text-fg-subtle" aria-label="to" />
                            <Badge tone="brand">{a.to_state ?? "∅"}</Badge>
                          </span>
                        ) : (
                          <span className="text-fg-subtle">—</span>
                        )}
                      </td>
                      <td className="px-3 py-3">
                        <p className="text-xs text-fg-muted">{humanize(a.entity_type)}</p>
                        <button
                          type="button"
                          className="max-w-48 truncate text-left font-mono text-xs text-brand hover:underline"
                          onClick={() => {
                            setInput(a.entity_id);
                            setParams({ entity_id: a.entity_id }, { replace: true });
                          }}
                          title={`Filter by ${a.entity_id}`}
                        >
                          {a.entity_id}
                        </button>
                      </td>
                      <td className="px-3 py-3 text-xs">
                        <p>{a.actor_role ? humanize(a.actor_role) : humanize(a.actor_type)}</p>
                        {a.actor_id !== null ? <p className="font-mono text-fg-subtle">#{a.actor_id}</p> : null}
                      </td>
                      <td className="max-w-40 truncate px-5 py-3 font-mono text-xs text-fg-subtle" title={a.correlation_id ?? ""}>
                        {a.correlation_id ?? "—"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="flex items-center justify-between border-t border-line px-5 py-3 text-xs text-fg-muted">
              <span>
                Showing {data.items.length} of {data.total}
              </span>
              {data.total > data.items.length ? (
                <Button size="sm" variant="outline" onClick={() => setLimit((l) => l + 50)} loading={isFetching}>
                  Load more
                </Button>
              ) : null}
            </div>
          </>
        )}
      </Card>
    </>
  );
}
