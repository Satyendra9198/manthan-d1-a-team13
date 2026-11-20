import { BellOff } from "lucide-react";
import { useState } from "react";
import { useNotifications } from "../../api/hooks/customer";
import { ErrorState } from "../../components/ErrorState";
import { NotificationRow } from "../../components/NotificationsBell";
import { PageHeader } from "../../components/PageHeader";
import { Card } from "../../components/ui/Card";
import { EmptyState } from "../../components/ui/EmptyState";
import { SkeletonCards } from "../../components/ui/Skeleton";

export function Notifications() {
  const { data, isPending, error, refetch } = useNotifications();
  const [now] = useState(() => Date.now());

  return (
    <>
      <PageHeader eyebrow="Updates" title="Notifications" description="Everything that happened with your returns." />
      {isPending ? (
        <SkeletonCards count={4} />
      ) : error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : data.items.length === 0 ? (
        <EmptyState icon={<BellOff />} title="You're all caught up" description="We'll post updates here as your returns move along." />
      ) : (
        <Card className="overflow-hidden">
          <ul className="divide-y divide-line" data-testid="notifications-list">
            {data.items.map((n) => (
              <li key={n.id}>
                <NotificationRow n={n} unread={n.read_at === null} now={now} />
              </li>
            ))}
          </ul>
        </Card>
      )}
    </>
  );
}
