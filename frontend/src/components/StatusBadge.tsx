import type { AttemptStatus, ItemStatus, ReturnStatus } from "../api/types";
import {
  ATTEMPT_STATUS_LABEL,
  ATTEMPT_STATUS_TONE,
  ITEM_STATUS_LABEL,
  ITEM_STATUS_TONE,
  RETURN_STATUS_LABEL,
  RETURN_STATUS_TONE,
} from "../lib/labels";
import { Badge } from "./ui/Badge";

export function ReturnStatusBadge({ status }: { status: ReturnStatus }) {
  const live = status === "REFUND_PENDING" || status === "PENDING_FINANCE" || status === "IN_TRANSIT";
  return (
    <Badge tone={RETURN_STATUS_TONE[status]} dot pulse={live} data-testid="return-status">
      {RETURN_STATUS_LABEL[status]}
    </Badge>
  );
}

export function ItemStatusBadge({ status }: { status: ItemStatus }) {
  return <Badge tone={ITEM_STATUS_TONE[status]}>{ITEM_STATUS_LABEL[status]}</Badge>;
}

export function AttemptStatusBadge({ status }: { status: AttemptStatus }) {
  return (
    <Badge tone={ATTEMPT_STATUS_TONE[status]} dot pulse={status === "UNKNOWN" || status === "PENDING"} data-testid={`attempt-${status}`}>
      {ATTEMPT_STATUS_LABEL[status]}
    </Badge>
  );
}
