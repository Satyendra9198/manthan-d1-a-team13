# ReturnRight API (v1)

Base path `/api/v1`. JSON in and out. Money is always integer **paise** (`149900` = ₹1,499.00).
Timestamps are ISO-8601 UTC; `lastReturnDate` is a calendar date in `Asia/Kolkata`.

## Authentication

| Caller | How |
|---|---|
| Customer | No login. Order number + email: in the body for lookup/create, otherwise headers `X-Order-Number` and `X-Customer-Email`. A mismatch returns 404. |
| Staff | HTTP Basic. Seeded users `warehouse`, `finance`, `operations`; passwords come from env vars (see `backend/.env.example`). |

## Endpoints

### Customer — `/api/v1/customer` (public)

| Method | Path | Body | Returns | State change |
|---|---|---|---|---|
| POST | `/orders/lookup` | `{orderNumber, email}` | `OrderResponse` with per-item eligibility and `returnableQuantity` | — |
| POST | `/returns` | `{orderNumber, email, refundMethod, items:[{orderItemId, quantity, reason, note?}]}` | 201 `ReturnResponse` incl. `ineligibleItems` | → `REQUESTED` → `APPROVED` (label issued), or stays `REQUESTED` if the customer is flagged |
| GET | `/returns/{id}` | headers | `ReturnResponse` with `timeline` | — |
| GET | `/returns/{id}/label` | headers | `{labelCode, qrPayload, expiresAt}` | — |
| POST | `/returns/{id}/ship` | headers | `ReturnResponse` | `APPROVED` → `IN_TRANSIT` |
| POST | `/returns/{id}/cancel` | headers | `ReturnResponse` | `REQUESTED`/`APPROVED` → `CANCELLED` |

### Staff (any staff role) — `/api/v1/returns`

| Method | Path | Returns |
|---|---|---|
| GET | `?status=&flagged=` | `ReturnSummaryResponse[]` — queues: `RECEIVED` (warehouse), `AWAITING_FINANCE_APPROVAL` (finance), `REQUESTED&flagged=true` (manager) |
| GET | `/{id}` | `ReturnResponse` |

### Warehouse — `/api/v1/warehouse` (`WAREHOUSE_AGENT`)

| Method | Path | Body | State change |
|---|---|---|---|
| POST | `/scan` | `{labelCode}` | `APPROVED`/`IN_TRANSIT` → `RECEIVED`; second scan → 409 |
| POST | `/returns/{id}/inspection` | `{lines:[{returnItemId, acceptedQuantity, grade?, notes?}]}` — one line per item | `RECEIVED` → `INSPECTED` → `AWAITING_FINANCE_APPROVAL` (refund > ₹10,000) or `REFUND_PENDING` → `REFUNDED`; all lines rejected → `REJECTED` |

### Finance — `/api/v1/finance` (`FINANCE_REVIEWER`)

| Method | Path | Body / headers | State change |
|---|---|---|---|
| POST | `/returns/{id}/approve` | header **`Idempotency-Key`** (one per click, reused on retry) | `AWAITING_FINANCE_APPROVAL` → `REFUND_PENDING` → `REFUNDED`. Repeats never pay twice. |
| POST | `/returns/{id}/reject` | `{reason}` | → `REJECTED` |
| GET | `/refunds?status=` | — | `RefundResponse[]`; `UNKNOWN` = gateway outcome not known yet |
| POST | `/refunds/reconcile` | — | `{refundsSettled, returnsCompleted}`; safe to repeat |

### Operations — `/api/v1/operations` (`OPERATIONS_MANAGER`)

| Method | Path | Body | State change |
|---|---|---|---|
| POST | `/returns/{id}/approve` | — | flagged `REQUESTED` → `APPROVED` (label issued) |
| POST | `/returns/{id}/reject` | `{reason}` | `REQUESTED` → `REJECTED` |
| GET | `/dashboard` | — | `{returnRateByCategory, topReasons, averageCycleTimeHours, flaggedCustomers}` |

## Customer timeline

`ReturnResponse.timelineStep` and `timeline[]` use: `REQUESTED`, `APPROVED`, `IN_TRANSIT`, `RECEIVED`,
`INSPECTED`, `REFUNDED`, `REJECTED`, `CANCELLED`. Internal states `AWAITING_FINANCE_APPROVAL` and
`REFUND_PENDING` are shown as `INSPECTED`.

## Errors

Every error has the same body:

```json
{ "code": "ILLEGAL_TRANSITION", "message": "...", "details": [], "timestamp": "2026-11-20T10:48:57Z" }
```

| HTTP | `code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED`, `MALFORMED_REQUEST`, `INVALID_REQUEST` | Bad or missing input / header |
| 401 | — | Staff endpoint without credentials |
| 403 | — / `TRANSITION_NOT_PERMITTED` | Wrong role |
| 404 | `NOT_FOUND` | Unknown id, or order number/email mismatch |
| 409 | `ILLEGAL_TRANSITION` | Action not allowed in the current state (e.g. second QR scan) |
| 409 | `CONCURRENT_UPDATE` | Someone else changed the return first (optimistic lock) |
| 409 | `IDEMPOTENCY_CONFLICT` | Key reused for a different refund |
| 422 | `NO_ELIGIBLE_ITEMS`, `INSPECTION_INCOMPLETE`, `LABEL_NOT_AVAILABLE`, `UNKNOWN_ORDER_ITEM` | Business rule broken |

## Eligibility reason codes

| Code | Customer message |
|---|---|
| `ELIGIBLE` | You can return this item until {date}. |
| `NON_RETURNABLE_CATEGORY` | Innerwear and perishable items can't be returned for hygiene and safety reasons. |
| `NOT_DELIVERED` | This item hasn't been delivered yet. You can request a return once it arrives. |
| `INVALID_QUANTITY` | Choose at least one unit to return. |
| `ALREADY_RETURNED` | All units of this item are already part of a return. |
| `QUANTITY_EXCEEDS_REMAINING` | You can return up to {n} unit(s) of this item. |
| `RETURN_WINDOW_EXPIRED` | The {10\|30}-day return window for this item closed on {date}. |

## Mock gateway contract (consumed by the backend)

`POST {GATEWAY_BASE_URL}/v1/refunds`, header `Idempotency-Key`, body `{returnId, amountPaise}` →
`200 {status: "SUCCEEDED"|"DECLINED", transactionId, declineReason}`. The same key must return the
original result. Timeouts, connection errors and 5xx make the refund `UNKNOWN`; 4xx makes it `FAILED`.
