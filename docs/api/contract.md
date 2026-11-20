# ReturnRight API contract (v1, implementation-binding)

This is the concrete contract that the backend implements and the frontend consumes. It refines §10 of
`docs/architecture/02-architecture-part2.md`. If they differ, **this file wins**.

## Conventions
- Base URL `/api/v1`. JSON. Money is an **integer paise** (`*_paise`); format it in the UI as INR (`₹1,23,456.78`, `en-IN`).
- Timestamps are ISO-8601 UTC strings. Display them in `Asia/Kolkata`.
- Auth: `Authorization: Bearer <access_token>`.
- Headers:
  - `Idempotency-Key: <uuid>` on endpoints marked 🔑. The client generates **one key per confirm dialog / form submission**, not per click.
  - `If-Match: "<version>"` on endpoints marked 🔒 (version from `ReturnDTO.version`).
  - The response header `X-Correlation-ID` is echoed. Show it in error toasts ("Ref: …").
- Error envelope (all non-2xx):
  `{"error": {"code": "ILLEGAL_TRANSITION", "message": "human readable", "details": {...}, "correlation_id": "..."}}`
  Notable codes: `UNAUTHENTICATED` 401, `TOKEN_EXPIRED` 401, `FORBIDDEN_ROLE` 403, `NOT_FOUND` 404,
  `ILLEGAL_TRANSITION` 409, `STALE_VERSION` 409, `REQUEST_IN_PROGRESS` 409, `UNIT_ALREADY_IN_RETURN` 409,
  `RETURN_CANCELLED` 409, `LABEL_VOID` 409, `LABEL_EXPIRED` 409, `VALIDATION_ERROR` 422 (`details.fields[]`),
  `ITEM_NOT_ELIGIBLE` 422 (`details.items[]`), `GUARD_FAILED` 422, `INSPECTION_INCOMPLETE` 422,
  `IDEMPOTENCY_KEY_REUSED` 422, `RATE_LIMITED` 429, `INTERNAL_ERROR` 500.
- Lists: `{"items": [...], "total": n}`.

## Enums
- `Role`: `CUSTOMER | WAREHOUSE_AGENT | FINANCE_REVIEWER | OPS_MANAGER | ADMIN`
- `ReturnStatus`: `REQUESTED | APPROVED | IN_TRANSIT | RECEIVED | INSPECTED | PENDING_FINANCE | REFUND_PENDING | REFUND_FAILED | REFUNDED | REJECTED | CANCELLED`
- `TimelineStep`: `REQUESTED | APPROVED | IN_TRANSIT | RECEIVED | INSPECTED | REFUNDED | REJECTED | CANCELLED`
- `ItemStatus`: `REQUESTED | RECEIVED | ACCEPTED | REJECTED | REFUNDED | CANCELLED`
- `ReturnReason`: `DAMAGED | DEFECTIVE | WRONG_ITEM | SIZE_FIT | NOT_AS_DESCRIBED | CHANGED_MIND | LATE_DELIVERY | OTHER`
- `Grade`: `A | B | C` · `Decision`: `ACCEPT | REJECT`
- `RejectReason`: `NOT_IN_PARCEL | WRONG_ITEM_RETURNED | USED_OR_DAMAGED_BY_CUSTOMER | MISSING_PARTS | FINANCE_REJECTED`
- `RefundMethod`: `ORIGINAL_PAYMENT | STORE_CREDIT`
- `RefundStatus`: `PENDING | SUCCEEDED | FAILED` · `AttemptStatus`: `PENDING | SUCCEEDED | FAILED | UNKNOWN`
- `EligibilityReason`: `ELIGIBLE | ORDER_CANCELLED | ORDER_NOT_DELIVERED | DELIVERY_DATE_INVALID | CATEGORY_NOT_RETURNABLE | UNIT_ALREADY_RETURNED | UNIT_RETURN_IN_PROGRESS | RETURN_WINDOW_EXPIRED`
- `Action` (in `allowed_actions`): `SHIP | CANCEL | INSPECT | COMPLETE_INSPECTION | FINANCE_APPROVE | FINANCE_REJECT | RETRY_REFUND | RECHECK_REFUND`

## Shared shapes

```ts
type Principal = { type: "customer" | "staff"; id: number; name: string; role: Role; username?: string; email?: string };
type AuthResponse = { access_token: string; expires_in: number; principal: Principal };

type Eligibility = {
  eligible: boolean; reason_code: EligibilityReason; message: string;
  details: { category_code: string; window_days: number | null; delivered_date: string | null;
             evaluated_date: string; days_since_delivery: number | null;
             last_eligible_date: string | null; days_remaining: number | null; timezone: string };
};

type ReturnItemDTO = {
  id: string; order_item_id: number; unit_index: number; product_name: string; sku: string;
  category_code: string; category_name: string; status: ItemStatus; reason: ReturnReason; reason_note: string | null;
  unit_price_paise: number; grade: Grade | null; decision: Decision | null; reject_reason: RejectReason | null;
  refund_paise: number | null; inspected_at: string | null;
};

type TimelineEntry = { step: TimelineStep; label: string; at: string | null; state: "done" | "current" | "upcoming" };

type RefundAttemptDTO = { id: string; attempt_no: number; method: RefundMethod; status: AttemptStatus;
  decline_code: string | null; gateway_idempotency_key: string; created_at: string; completed_at: string | null };
type RefundDTO = { id: string; status: RefundStatus; amount_paise: number; method: RefundMethod;
  attempts: RefundAttemptDTO[] };   // attempts = [] for customers

type ReturnDTO = {
  id: string; rma_number: string; order_number: string; status: ReturnStatus; timeline_step: TimelineStep;
  status_label: string;              // customer-safe text, e.g. "Your refund is being processed"
  version: number; refund_method: RefundMethod; approved_refund_paise: number | null;
  requested_refund_paise: number;    // sum of unit prices
  outcome: "FULL" | "PARTIAL" | "NONE" | null; created_at: string; updated_at: string;
  items: ReturnItemDTO[];
  label: { tracking_number: string; status: "ACTIVE" | "USED" | "VOIDED" | "EXPIRED"; expires_at: string } | null;
  refund: RefundDTO | null;
  timeline: TimelineEntry[];
  allowed_actions: Action[];         // computed for the calling principal
  // staff only (null for customers):
  customer: { id: number; name: string; email: string; is_flagged: boolean; returns_30d: number } | null;
  requires_finance_review: boolean | null; finance_review_reason: "AMOUNT" | "FLAGGED" | "BOTH" | null;
};
```

## Endpoints

### Auth
| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/auth/customer/lookup` | `{order_number, email}` | `AuthResponse` (404 `NOT_FOUND` generic on mismatch, 429 on abuse) |
| POST | `/auth/staff/login` | `{username, password}` | `AuthResponse` (401 `INVALID_CREDENTIALS`, 423 `ACCOUNT_LOCKED` after 5 failures / 15 min) |
| GET | `/auth/me` | — | `Principal` |
| POST | `/auth/logout` | — | `204` (token revoked server-side) |

### Customer (role CUSTOMER)
| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/customer/orders` | — | `{items: [{order_number, status, placed_at, delivered_at, total_paise, item_count, returnable_units}], total}` |
| GET | `/customer/orders/{order_number}` | — | `{order_number, status, placed_at, delivered_at, payment_method, total_paise, units: [{order_item_id, unit_index, product_name, sku, category_code, category_name, price_paise, eligibility: Eligibility, active_return_id: string \| null}]}` |
| POST | `/customer/returns` 🔑 | `{order_number, refund_method, items: [{order_item_id, unit_index, reason, note?}]}` | `201 ReturnDTO` |
| GET | `/customer/returns` | — | `{items: ReturnDTO[], total}` |
| GET | `/customer/returns/{id}` | — | `ReturnDTO` |
| GET | `/customer/returns/{id}/label` | — | `{rma_number, tracking_number, qr_token, qr_svg: string /* data:image/svg+xml;base64,... */, status, issued_at, expires_at, ship_to: {name, line1, city, pincode}, customer_name, items: [{product_name, unit_index}]}` |
| POST | `/customer/returns/{id}/ship` 🔒 | — | `ReturnDTO` |
| POST | `/customer/returns/{id}/cancel` 🔒 | — | `ReturnDTO` |
| GET | `/customer/notifications` | — | `{items: [{id, template, title, body, return_id, created_at, read_at}], total}` |

### Warehouse (role WAREHOUSE_AGENT)
| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/warehouse/scan` | `{qr_token}` (token or RMA number) | `{already_received: boolean, message: string, return: ReturnDTO}` |
| GET | `/warehouse/returns?status=RECEIVED` | `status` optional, comma list | `{items: ReturnDTO[], total}` |
| GET | `/warehouse/returns/{id}` | — | `ReturnDTO` |
| PUT | `/warehouse/returns/{id}/items/{item_id}/inspection` 🔒 | `{decision, grade?, reject_reason?, notes?}` | `ReturnDTO` |
| POST | `/warehouse/returns/{id}/complete-inspection` 🔑🔒 | — | `ReturnDTO` (status after routing: `REFUNDED`, `REFUND_PENDING`, `PENDING_FINANCE`, `REJECTED`) |

### Finance (role FINANCE_REVIEWER)
| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/finance/queue?status=PENDING_FINANCE` | `status` comma list (default `PENDING_FINANCE,REFUND_FAILED,REFUND_PENDING`) | `{items: ReturnDTO[], total}` |
| GET | `/finance/returns/{id}` | — | `ReturnDTO` |
| POST | `/finance/returns/{id}/approve` 🔑🔒 | `{note?}` | `ReturnDTO` (HTTP 200 if final, **202** if the refund outcome is UNKNOWN/PENDING) |
| POST | `/finance/returns/{id}/reject` 🔑🔒 | `{note}` | `ReturnDTO` |
| POST | `/finance/returns/{id}/refund/retry` 🔑🔒 | `{method?: RefundMethod}` | `ReturnDTO` (200/202) |
| POST | `/finance/refund-attempts/{attempt_id}/recheck` | — | `ReturnDTO` |

### Operations Manager (role OPS_MANAGER; ADMIN has all staff rights)
| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/analytics/overview?days=30` | — | see below |
| GET | `/analytics/flagged-customers` | — | `{items: [{customer_id, name, email_masked, returns_30d, open_returns, refunded_paise_90d, flagged_at, flag_reason}], total}` |
| POST | `/ops/customers/{id}/clear-flag` 🔑 | `{note}` | `{customer_id, is_flagged: false}` |
| GET | `/ops/audit?entity_id=&limit=50` | — | `{items: [{id, occurred_at, actor_type, actor_id, actor_role, entity_type, entity_id, event, from_state, to_state, correlation_id}], total}` |
| GET | `/ops/reconciliation` | — | `{run_at, ok: boolean, checks: [{check, description, mismatches, sample_ids}]}` |

`/analytics/overview` response:
```ts
{
  window_days: number;
  kpis: { returns_count: number; refunded_paise: number; refund_count: number; avg_cycle_hours: number | null;
          open_returns: number; flagged_customers: number; refund_success_rate: number | null; return_rate_pct: number };
  return_rate_by_category: { category_code: string; category_name: string; units_sold: number; units_returned: number; return_rate_pct: number }[];
  top_reasons: { reason: ReturnReason; count: number; pct: number }[];
  cycle_time: { avg_hours: number | null; p50_hours: number | null; p90_hours: number | null;
                stages: { request_to_receive_hours: number | null; receive_to_inspect_hours: number | null; inspect_to_refund_hours: number | null } };
  returns_by_status: { status: ReturnStatus; count: number }[];
  daily: { date: string; requested: number; refunded: number }[];
}
```

### Platform
`GET /healthz`, `GET /readyz`, `GET /metrics` (not under `/api/v1`).
