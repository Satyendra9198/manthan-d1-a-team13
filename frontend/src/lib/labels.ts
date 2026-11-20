import type {
  AttemptStatus,
  FinanceReviewReason,
  ItemStatus,
  RefundMethod,
  RejectReason,
  ReturnReason,
  ReturnStatus,
} from "../api/types";

/** UI labels for enums. Eligibility text is NOT here: it always comes from the backend `message` (§9.4). */

export const RETURN_REASON_LABEL: Record<ReturnReason, string> = {
  DAMAGED: "Arrived damaged",
  DEFECTIVE: "Defective / doesn't work",
  WRONG_ITEM: "Wrong item sent",
  SIZE_FIT: "Size or fit issue",
  NOT_AS_DESCRIBED: "Not as described",
  CHANGED_MIND: "Changed my mind",
  LATE_DELIVERY: "Arrived too late",
  OTHER: "Other",
};

export const RETURN_REASONS = Object.keys(RETURN_REASON_LABEL) as ReturnReason[];

export const REJECT_REASON_LABEL: Record<RejectReason, string> = {
  NOT_IN_PARCEL: "Not in parcel",
  WRONG_ITEM_RETURNED: "Wrong item returned",
  USED_OR_DAMAGED_BY_CUSTOMER: "Used / damaged by customer",
  MISSING_PARTS: "Missing parts",
  FINANCE_REJECTED: "Rejected by finance",
};

/** Reasons a warehouse agent may pick (FINANCE_REJECTED is set by finance only). */
export const WAREHOUSE_REJECT_REASONS: RejectReason[] = [
  "NOT_IN_PARCEL",
  "WRONG_ITEM_RETURNED",
  "USED_OR_DAMAGED_BY_CUSTOMER",
  "MISSING_PARTS",
];

export const REFUND_METHOD_LABEL: Record<RefundMethod, string> = {
  ORIGINAL_PAYMENT: "Original payment",
  STORE_CREDIT: "Store credit",
};

export const RETURN_STATUS_LABEL: Record<ReturnStatus, string> = {
  REQUESTED: "Requested",
  APPROVED: "Approved",
  IN_TRANSIT: "In transit",
  RECEIVED: "Received",
  INSPECTED: "Inspected",
  PENDING_FINANCE: "Pending finance",
  REFUND_PENDING: "Refund processing",
  REFUND_FAILED: "Refund failed",
  REFUNDED: "Refunded",
  REJECTED: "Rejected",
  CANCELLED: "Cancelled",
};

export const ITEM_STATUS_LABEL: Record<ItemStatus, string> = {
  REQUESTED: "Requested",
  RECEIVED: "Received",
  ACCEPTED: "Accepted",
  REJECTED: "Rejected",
  REFUNDED: "Refunded",
  CANCELLED: "Cancelled",
};

export const ATTEMPT_STATUS_LABEL: Record<AttemptStatus, string> = {
  PENDING: "Pending",
  SUCCEEDED: "Succeeded",
  FAILED: "Failed",
  UNKNOWN: "Unknown",
};

export const FINANCE_REASON_LABEL: Record<FinanceReviewReason, string> = {
  AMOUNT: "Over ₹10,000",
  FLAGGED: "Flagged customer",
  BOTH: "Over ₹10,000 · flagged",
};

export type Tone = "neutral" | "brand" | "info" | "success" | "warning" | "danger";

export const RETURN_STATUS_TONE: Record<ReturnStatus, Tone> = {
  REQUESTED: "neutral",
  APPROVED: "brand",
  IN_TRANSIT: "info",
  RECEIVED: "info",
  INSPECTED: "info",
  PENDING_FINANCE: "warning",
  REFUND_PENDING: "warning",
  REFUND_FAILED: "danger",
  REFUNDED: "success",
  REJECTED: "danger",
  CANCELLED: "neutral",
};

export const ITEM_STATUS_TONE: Record<ItemStatus, Tone> = {
  REQUESTED: "neutral",
  RECEIVED: "info",
  ACCEPTED: "success",
  REJECTED: "danger",
  REFUNDED: "success",
  CANCELLED: "neutral",
};

export const ATTEMPT_STATUS_TONE: Record<AttemptStatus, Tone> = {
  PENDING: "warning",
  SUCCEEDED: "success",
  FAILED: "danger",
  UNKNOWN: "warning",
};

export function humanize(code: string): string {
  return code
    .toLowerCase()
    .split("_")
    .map((w, i) => (i === 0 ? w.charAt(0).toUpperCase() + w.slice(1) : w))
    .join(" ");
}
