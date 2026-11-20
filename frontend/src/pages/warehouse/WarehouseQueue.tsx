import { RefreshCw, ScanLine } from "lucide-react";
import { useState } from "react";
import { useSearchParams } from "react-router";
import { useWarehouseQueue } from "../../api/hooks/warehouse";
import { PageHeader } from "../../components/PageHeader";
import { ReturnQueueList } from "../../components/ReturnQueueList";
import { Button, ButtonLink } from "../../components/ui/Button";
import { Card } from "../../components/ui/Card";
import { Tabs } from "../../components/ui/Tabs";

type Tab = "APPROVED" | "IN_TRANSIT" | "RECEIVED";
const TABS: { value: Tab; label: string; empty: string }[] = [
  { value: "APPROVED", label: "Approved", empty: "No approved returns waiting to ship." },
  { value: "IN_TRANSIT", label: "In transit", empty: "Nothing on the way right now." },
  { value: "RECEIVED", label: "Received", empty: "No parcels waiting for inspection." },
];

function isTab(v: string | null): v is Tab {
  return v === "APPROVED" || v === "IN_TRANSIT" || v === "RECEIVED";
}

export function WarehouseQueue() {
  const [params, setParams] = useSearchParams();
  const initial = params.get("tab");
  const [tab, setTab] = useState<Tab>(isTab(initial) ? initial : "RECEIVED");

  const approved = useWarehouseQueue(["APPROVED"]);
  const transit = useWarehouseQueue(["IN_TRANSIT"]);
  const received = useWarehouseQueue(["RECEIVED"]);
  const byTab: Record<Tab, typeof approved> = { APPROVED: approved, IN_TRANSIT: transit, RECEIVED: received };
  const q = byTab[tab];

  function change(t: Tab) {
    setTab(t);
    setParams({ tab: t }, { replace: true });
  }

  return (
    <>
      <PageHeader
        eyebrow="Warehouse"
        title="Returns queue"
        description="Returns heading to the warehouse and parcels waiting for inspection."
        actions={
          <>
            <Button variant="ghost" onClick={() => void q.refetch()} loading={q.isFetching && !q.isPending} icon={<RefreshCw className="size-4" />}>
              Refresh
            </Button>
            <ButtonLink to="/warehouse" icon={<ScanLine className="size-4" />}>
              Scan station
            </ButtonLink>
          </>
        }
      />
      <Tabs<Tab>
        label="Queue status"
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
          href={(r) => `/warehouse/returns/${r.id}`}
          emptyTitle="All clear"
          emptyDescription={TABS.find((t) => t.value === tab)?.empty}
          testId="warehouse-queue"
        />
      </Card>
    </>
  );
}
