// Types mirror docs/api/contract.md (v1). If the contract changes, change this file first.

export type Role = "CUSTOMER" | "WAREHOUSE_AGENT" | "FINANCE_REVIEWER" | "OPS_MANAGER" | "ADMIN";

export type ReturnStatus =
  | "REQUESTED"
  | "APPROVED"
  | "IN_TRANSIT"
  | "RECEIVED"
  | "INSPECTED"
  | "PENDING_FINANCE"
  | "REFUND_PENDING"
  | "REFUND_FAILED"
  | "REFUNDED"
  | "REJECTED"
  | "CANCELLED";

export type TimelineStep =
  | "REQUESTED"
  | "APPROVED"
  | "IN_TRANSIT"
  | "RECEIVED"
  | "INSPECTED"
  | "REFUNDED"
  | "REJECTED"
  | "CANCELLED";

export type ItemStatus = "REQUESTED" | "RECEIVED" | "ACCEPTED" | "REJECTED" | "REFUNDED" | "CANCELLED";

export type ReturnReason =
  | "DAMAGED"
  | "DEFECTIVE"
  | "WRONG_ITEM"
  | "SIZE_FIT"
  | "NOT_AS_DESCRIBED"
  | "CHANGED_MIND"
  | "LATE_DELIVERY"
  | "OTHER";

export type Grade = "A" | "B" | "C";
export type Decision = "ACCEPT" | "REJECT";

export type RejectReason =
  | "NOT_IN_PARCEL"
  | "WRONG_ITEM_RETURNED"
  | "USED_OR_DAMAGED_BY_CUSTOMER"
  | "MISSING_PARTS"
  | "FINANCE_REJECTED";

export type RefundMethod = "ORIGINAL_PAYMENT" | "STORE_CREDIT";
export type RefundStatus = "PENDING" | "SUCCEEDED" | "FAILED";
export type AttemptStatus = "PENDING" | "SUCCEEDED" | "FAILED" | "UNKNOWN";

export type EligibilityReason =
  | "ELIGIBLE"
  | "ORDER_CANCELLED"
  | "ORDER_NOT_DELIVERED"
  | "DELIVERY_DATE_INVALID"
  | "CATEGORY_NOT_RETURNABLE"
  | "UNIT_ALREADY_RETURNED"
  | "UNIT_RETURN_IN_PROGRESS"
  | "RETURN_WINDOW_EXPIRED";

export type Action =
  | "SHIP"
  | "CANCEL"
  | "INSPECT"
  | "COMPLETE_INSPECTION"
  | "FINANCE_APPROVE"
  | "FINANCE_REJECT"
  | "RETRY_REFUND"
  | "RECHECK_REFUND";

export interface ErrorEnvelope {
  error: {
    code: string;
    message: string;
    details?: Record<string, unknown> | null;
    correlation_id?: string | null;
  };
}

export interface ListResponse<T> {
  items: T[];
  total: number;
}

// ---------- Auth ----------
export interface Principal {
  type: "customer" | "staff";
  id: number;
  name: string;
  role: Role;
  username?: string;
  email?: string;
}

export interface AuthResponse {
  access_token: string;
  expires_in: number;
  principal: Principal;
}

export interface CustomerLookupRequest {
  order_number: string;
  email: string;
}

export interface StaffLoginRequest {
  username: string;
  password: string;
}

// ---------- Shared ----------
export interface EligibilityDetails {
  category_code: string;
  window_days: number | null;
  delivered_date: string | null;
  evaluated_date: string;
  days_since_delivery: number | null;
  last_eligible_date: string | null;
  days_remaining: number | null;
  timezone: string;
}

export interface Eligibility {
  eligible: boolean;
  reason_code: EligibilityReason;
  message: string;
  details: EligibilityDetails;
}

export interface ReturnItemDTO {
  id: string;
  order_item_id: number;
  unit_index: number;
  product_name: string;
  sku: string;
  category_code: string;
  category_name: string;
  status: ItemStatus;
  reason: ReturnReason;
  reason_note: string | null;
  unit_price_paise: number;
  grade: Grade | null;
  decision: Decision | null;
  reject_reason: RejectReason | null;
  refund_paise: number | null;
  inspected_at: string | null;
}

export interface TimelineEntry {
  step: TimelineStep;
  label: string;
  at: string | null;
  state: "done" | "current" | "upcoming";
}

export interface RefundAttemptDTO {
  id: string;
  attempt_no: number;
  method: RefundMethod;
  status: AttemptStatus;
  decline_code: string | null;
  gateway_idempotency_key: string;
  created_at: string;
  completed_at: string | null;
}

export interface RefundDTO {
  id: string;
  status: RefundStatus;
  amount_paise: number;
  method: RefundMethod;
  attempts: RefundAttemptDTO[];
}

export type LabelStatus = "ACTIVE" | "USED" | "VOIDED" | "EXPIRED";

export interface ReturnCustomerInfo {
  id: number;
  name: string;
  email: string;
  is_flagged: boolean;
  returns_30d: number;
}

export type FinanceReviewReason = "AMOUNT" | "FLAGGED" | "BOTH";

export interface ReturnDTO {
  id: string;
  rma_number: string;
  order_number: string;
  status: ReturnStatus;
  timeline_step: TimelineStep;
  status_label: string;
  version: number;
  refund_method: RefundMethod;
  approved_refund_paise: number | null;
  requested_refund_paise: number;
  outcome: "FULL" | "PARTIAL" | "NONE" | null;
  created_at: string;
  updated_at: string;
  items: ReturnItemDTO[];
  label: { tracking_number: string; status: LabelStatus; expires_at: string } | null;
  refund: RefundDTO | null;
  timeline: TimelineEntry[];
  allowed_actions: Action[];
  customer: ReturnCustomerInfo | null;
  requires_finance_review: boolean | null;
  finance_review_reason: FinanceReviewReason | null;
}

// ---------- Customer ----------
export interface OrderSummary {
  order_number: string;
  status: string;
  placed_at: string;
  delivered_at: string | null;
  total_paise: number;
  item_count: number;
  returnable_units: number;
}

export interface OrderUnit {
  order_item_id: number;
  unit_index: number;
  product_name: string;
  sku: string;
  category_code: string;
  category_name: string;
  price_paise: number;
  eligibility: Eligibility;
  active_return_id: string | null;
}

export interface OrderDetail {
  order_number: string;
  status: string;
  placed_at: string;
  delivered_at: string | null;
  payment_method: string;
  total_paise: number;
  units: OrderUnit[];
}

export interface CreateReturnItem {
  order_item_id: number;
  unit_index: number;
  reason: ReturnReason;
  note?: string;
}

export interface CreateReturnRequest {
  order_number: string;
  refund_method: RefundMethod;
  items: CreateReturnItem[];
}

export interface ReturnLabel {
  rma_number: string;
  tracking_number: string;
  qr_token: string;
  qr_svg: string;
  status: LabelStatus;
  issued_at: string;
  expires_at: string;
  ship_to: { name: string; line1: string; city: string; pincode: string };
  customer_name: string;
  items: { product_name: string; unit_index: number }[];
}

export interface NotificationDTO {
  id: string | number;
  template: string;
  title: string;
  body: string;
  return_id: string | null;
  created_at: string;
  read_at: string | null;
}

// ---------- Warehouse ----------
export interface ScanRequest {
  qr_token: string;
}

export interface ScanResponse {
  already_received: boolean;
  message: string;
  return: ReturnDTO;
}

export interface InspectionRequest {
  decision: Decision;
  grade?: Grade;
  reject_reason?: RejectReason;
  notes?: string;
}

// ---------- Finance ----------
export interface ApproveRequest {
  note?: string;
}
export interface RejectRequest {
  note: string;
}
export interface RetryRequest {
  method?: RefundMethod;
}

// ---------- Manager ----------
export interface AnalyticsOverview {
  window_days: number;
  kpis: {
    returns_count: number;
    refunded_paise: number;
    refund_count: number;
    avg_cycle_hours: number | null;
    open_returns: number;
    flagged_customers: number;
    refund_success_rate: number | null;
    return_rate_pct: number;
  };
  return_rate_by_category: {
    category_code: string;
    category_name: string;
    units_sold: number;
    units_returned: number;
    return_rate_pct: number;
  }[];
  top_reasons: { reason: ReturnReason; count: number; pct: number }[];
  cycle_time: {
    avg_hours: number | null;
    p50_hours: number | null;
    p90_hours: number | null;
    stages: {
      request_to_receive_hours: number | null;
      receive_to_inspect_hours: number | null;
      inspect_to_refund_hours: number | null;
    };
  };
  returns_by_status: { status: ReturnStatus; count: number }[];
  daily: { date: string; requested: number; refunded: number }[];
}

export interface FlaggedCustomer {
  customer_id: number;
  name: string;
  email_masked: string;
  returns_30d: number;
  open_returns: number;
  refunded_paise_90d: number;
  flagged_at: string | null;
  flag_reason: string | null;
}

export interface ClearFlagRequest {
  note: string;
}

export interface ClearFlagResponse {
  customer_id: number;
  is_flagged: false;
}

export interface AuditEntry {
  id: string | number;
  occurred_at: string;
  actor_type: string;
  actor_id: string | number | null;
  actor_role: string | null;
  entity_type: string;
  entity_id: string;
  event: string;
  from_state: string | null;
  to_state: string | null;
  correlation_id: string | null;
}

export interface ReconciliationCheck {
  check: string;
  description: string;
  mismatches: number;
  sample_ids: string[];
}

export interface ReconciliationReport {
  run_at: string;
  ok: boolean;
  checks: ReconciliationCheck[];
}
