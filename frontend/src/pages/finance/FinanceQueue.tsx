import { RefreshCw } from "lucide-react";
import { useState } from "react";
import { useSearchParams } from "react-router";
import { useFinanceQueue } from "../../api/hooks/finance";
import type { ReturnStatus } from "../../api/types";
import { PageHeader } from "../../components/PageHeader";
import { ReturnQueueList } from "../../components/ReturnQueueList";
import { RiskBadges } from "../../components/RiskBadges";
import { Button } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Tabs } from "../../components/ui/Tabs";

type Tab = "PENDING_FINANCE" | "REFUND_FAILED" | "REFUND_PENDING";
const TABS: { value: Tab; label: string; empty: string }[] = [
  { value: "PENDING_FINANCE", label: "Pending review", empty: "No returns waiting for review." },
  { value: "REFUND_FAILED", label: "Refund failed", empty: "No failed refunds. Nice." },
  { value: "REFUND_PENDING", label: "Processing", empty: "No refunds in flight." },
];

function isTab(v: string | null): v is Tab {
  return v === "PENDING_FINANCE" || v === "REFUND_FAILED" || v === "REFUND_PENDING";
}

export function FinanceQueue() {
  const [params, setParams] = useSearchParams();
  const initial = params.get("tab");
  const [tab, setTab] = useState<Tab>(isTab(initial) ? initial : "PENDING_FINANCE");

  const pending = useFinanceQueue(["PENDING_FINANCE"] satisfies ReturnStatus[]);
  const failed = useFinanceQueue(["REFUND_FAILED"]);
  const processing = useFinanceQueue(["REFUND_PENDING"]);
  const byTab: Record<Tab, typeof pending> = { PENDING_FINANCE: pending, REFUND_FAILED: failed, REFUND_PENDING: processing };
  const q = byTab[tab];

  function change(t: Tab) {
    setTab(t);
    setParams({ tab: t }, { replace: true });
  }

  return (
    <>
      <PageHeader
        eyebrow="Finance"
        title="Review queue"
        description="High-value and flagged returns, failed refunds and refunds still processing."
        actions={
          <Button variant="ghost" onClick={() => void q.refetch()} loading={q.isFetching && !q.isPending} icon={<RefreshCw className="size-4" />}>
            Refresh
          </Button>
        }
      />
      <Tabs<Tab>
        label="Finance queue"
        value={tab}
        onChange={change}
        items={TABS.map((t) => ({ value: t.value, label: t.label, count: byTab[t.value].data?.total }))}
        className="mb-4"
      />
      <Card className="overflow-hidden" role="tabpanel">
        <ReturnQueueList
          items={q.data?.items}
          isPending={q.isPending}
          error={q.error}
          onRetry={() => void q.refetch()}
          href={(r) => `/finance/returns/${r.id}`}
          extra={(r) => <RiskBadges ret={r} />}
          emptyTitle="Queue is empty"
          emptyDescription={TABS.find((t) => t.value === tab)?.empty}
          testId="finance-queue"
        />
      </Card>
    </>
  );
}
