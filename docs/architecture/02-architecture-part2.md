# ReturnRight — Architecture, Part 2 (Deliverables 9–20)

> Status: **Draft v0.1** · Date: 2026-11-20 · Continues [Part 1](01-architecture-part1.md) (assumptions A-nn, transitions T-nn, failure modes F-nn, DB rules BR-nn).
> Deliverable 18 (draft CLAUDE.md) is at the repo root: [../../CLAUDE.md](../../CLAUDE.md).

---

## 9. Eligibility engine

### 9.1 Contract

Location: `backend/app/modules/eligibility/engine.py`. **It is a pure function.** It does no DB access, no I/O, never calls `datetime.now()`, and imports nothing but the stdlib and its own types. The caller (`returns` module) gathers the facts with one SQL query and injects `now` from the `Clock`.

```python
RULE_VERSION = "2026-11-v1"
BUSINESS_TZ = ZoneInfo("Asia/Kolkata")

class UnitState(StrEnum):            # computed by orders.returnable_units() query
    AVAILABLE = "AVAILABLE"           # not in any non-cancelled return_item
    IN_ACTIVE_RETURN = "IN_ACTIVE_RETURN"
    ALREADY_RETURNED = "ALREADY_RETURNED"   # in a return that is REFUNDED/REJECTED

@dataclass(frozen=True, slots=True)
class UnitFacts:
    order_item_id: int
    unit_index: int
    order_status: str                 # 'PLACED'|'SHIPPED'|'DELIVERED'|'CANCELLED'
    delivered_at: datetime | None     # tz-aware UTC
    category_code: str
    category_name: str
    is_returnable: bool
    window_days: int | None
    unit_state: UnitState

@dataclass(frozen=True, slots=True)
class EligibilityResult:
    eligible: bool
    reason_code: ReasonCode           # ELIGIBLE when eligible
    message: str                      # customer-facing microcopy (rendered)
    details: dict                     # machine-readable facts, see 9.3
    rule_version: str = RULE_VERSION

def evaluate(unit: UnitFacts, now: datetime, tz: ZoneInfo = BUSINESS_TZ) -> EligibilityResult: ...
def evaluate_many(units: Sequence[UnitFacts], now: datetime, tz=BUSINESS_TZ) -> list[EligibilityResult]: ...
```

`now` must be timezone-aware, otherwise `ValueError` is raised. A naive datetime is a programming error, not an eligibility outcome.

### 9.2 Rule evaluation order (first failure wins)

| Step | Check | Fails with | Why at this position |
|---|---|---|---|
| R1 | `order_status == 'CANCELLED'` | `ORDER_CANCELLED` | The most fundamental fact. Nothing else is relevant. |
| R2 | `order_status != 'DELIVERED'` or `delivered_at is None` | `ORDER_NOT_DELIVERED` | [A-01]: the window cannot start without a delivery date |
| R3 | `delivered_date_local > today_local` | `DELIVERY_DATE_INVALID` | Corrupt data must never produce a "you have 34 days left" message. A warning is also logged. |
| R4 | `not is_returnable` | `CATEGORY_NOT_RETURNABLE` | A permanent policy fact. It must win over "window expired", because telling someone their innerwear "window expired" is misleading. |
| R5 | `unit_state == ALREADY_RETURNED` | `UNIT_ALREADY_RETURNED` | A permanent fact about this unit |
| R6 | `unit_state == IN_ACTIVE_RETURN` | `UNIT_RETURN_IN_PROGRESS` | Points the customer to the existing return |
| R7 | `days_since_delivery > window_days` | `RETURN_WINDOW_EXPIRED` | The only time-dependent rule, so it is last |
| ✓ | — | `ELIGIBLE` | — |

The day arithmetic ([A-02]) is a single helper with no alternatives:

```python
today_local       = now.astimezone(tz).date()
delivered_local   = unit.delivered_at.astimezone(tz).date()
days_since        = (today_local - delivered_local).days          # day 0 = delivery day
last_eligible     = delivered_local + timedelta(days=window_days) # inclusive
eligible_by_time  = days_since <= window_days
```

### 9.3 Output `details` (always present, nulls where not applicable)

```json
{
  "category_code": "ELECTRONICS",
  "window_days": 10,
  "delivered_date": "2026-11-09",
  "evaluated_date": "2026-11-20",
  "days_since_delivery": 11,
  "last_eligible_date": "2026-11-19",
  "days_remaining": -1,
  "timezone": "Asia/Kolkata"
}
```

The whole result is stored verbatim in `return_items.eligibility_snapshot` at submit time, so a later dispute can show exactly what was decided and under which `rule_version`.

### 9.4 Reason codes and customer microcopy

| `reason_code` | Eligible | Customer-facing microcopy (template) | Staff note |
|---|---|---|---|
| `ELIGIBLE` | ✅ | "You can return this item until **{last_eligible_date:%d %b %Y}**." (if `days_remaining ≤ 2`: "**Last {days_remaining+1} day(s)** to return this item.") | — |
| `ORDER_CANCELLED` | ❌ | "This order was cancelled, so there's nothing to return. If you were charged, any refund is handled automatically." | — |
| `ORDER_NOT_DELIVERED` | ❌ | "This order hasn't been delivered yet. You'll be able to start a return once it arrives." | — |
| `DELIVERY_DATE_INVALID` | ❌ | "We can't check this item right now. Please contact support and quote order **{order_number}**." | Data error: delivered_at is in the future |
| `CATEGORY_NOT_RETURNABLE` | ❌ | "**{category_name}** can't be returned for hygiene and safety reasons. If the item arrived damaged or wrong, please contact support." | Innerwear, perishables |
| `UNIT_ALREADY_RETURNED` | ❌ | "This item has already been returned." | — |
| `UNIT_RETURN_IN_PROGRESS` | ❌ | "A return for this item is already in progress. [Track return {rma_number}]" | — |
| `RETURN_WINDOW_EXPIRED` | ❌ | "The return window for **{category_name}** is {window_days} days from delivery. This item was delivered on {delivered_date:%d %b %Y}, so the last day to return it was **{last_eligible_date:%d %b %Y}**." | — |

The microcopy lives in `eligibility/messages.py` as one dict `ReasonCode → template`. The frontend renders `message` and **does not** construct its own text. Tests snapshot every message.

### 9.5 Testability

- Parametrised table tests in `tests/unit/eligibility/test_engine.py` using `now=datetime(2026,11,20,0,0,tzinfo=IST)` and its variants. They cover: day 0, 29, 30, 31; electronics 9, 10, 11; delivered at **23:50 IST** (= 18:20 UTC the same day) and at **00:10 IST** (= 18:40 UTC the previous day); `now` at 23:59:59 and 00:00:00 IST on the boundary day; each failing rule; and the rule-precedence pairs (innerwear + expired → `CATEGORY_NOT_RETURNABLE`).
- A Hypothesis property test checks that for any delivered date and window, `eligible ⇔ evaluated_date ≤ last_eligible_date`.
- The DB trigger `trg_return_item_eligibility` (BR-9) mirrors R2, R4 and R7. An integration test feeds the same boundary cases to both and asserts that they agree.

---

## 10. API design

### 10.1 Conventions

- Base path `/api/v1`. JSON in and out. Money is in **integer paise** (`amount_paise`), and the UI formats it.
- Auth: `Authorization: Bearer <JWT>` (see §11).
- `X-Correlation-ID`: accepted or generated, echoed in the response, and written to logs and `audit_log`.
- **`Idempotency-Key`** (UUID, 8–128 chars) is **required** where marked 🔑. Replays carry `Idempotent-Replayed: true`.
- **`If-Match: "<version>"`** is **required** where marked 🔒. Return payloads carry `ETag: "<version>"` and `"version": n`.
- Pagination: `?limit=50&cursor=<opaque>` gives `{ "items": [...], "next_cursor": "…" | null }`.
- Common return representation `ReturnDTO`:

```json
{
  "id": "0193…", "rma_number": "RMA-26-000451", "order_number": "RR-2026-000123",
  "status": "REFUND_PENDING", "timeline_step": "INSPECTED", "version": 8,
  "refund_method": "ORIGINAL_PAYMENT", "approved_refund_paise": 550050, "outcome": "PARTIAL",
  "items": [{ "id": "…", "product_name": "…", "category_code": "APPAREL", "unit_index": 1,
              "status": "ACCEPTED", "reason": "SIZE_FIT", "grade": "C",
              "unit_price_paise": 300100, "refund_paise": 150050 }],
  "label": { "tracking_number": "MOCKSHIP-7F3K2Q9A", "status": "USED", "expires_at": "…" },
  "refund": { "status": "PENDING", "attempts": [{ "attempt_no": 1, "status": "UNKNOWN" }] },
  "timeline": [{ "step": "REQUESTED", "at": "2026-11-02T10:01:12Z" }, …],
  "created_at": "…"
}
```

Customers get a redacted view: no `attempts`, no `requires_finance_review`, no flag.

### 10.2 Error format (every non-2xx)

```json
{
  "error": {
    "code": "ILLEGAL_TRANSITION",
    "message": "This return can't be approved because it is already being refunded.",
    "details": { "current_state": "REFUND_PENDING", "event": "FINANCE_APPROVE" },
    "correlation_id": "6f1c…"
  }
}
```

| HTTP | `code` values |
|---|---|
| 400 | `VALIDATION_ERROR` (with `details.fields[]`), `IDEMPOTENCY_KEY_REQUIRED`, `PRECONDITION_REQUIRED` (missing If-Match) |
| 401 | `UNAUTHENTICATED`, `TOKEN_EXPIRED` |
| 403 | `FORBIDDEN_ROLE` |
| 404 | `NOT_FOUND` (also returned for other customers' resources, so their existence is not leaked) |
| 409 | `ILLEGAL_TRANSITION`, `STALE_VERSION`, `REQUEST_IN_PROGRESS`, `UNIT_ALREADY_IN_RETURN`, `RETURN_CANCELLED`, `LABEL_VOID`, `LABEL_EXPIRED` |
| 422 | `ITEM_NOT_ELIGIBLE` (with `details.items[] = EligibilityResult`), `GUARD_FAILED` (`details.guard`), `INSPECTION_INCOMPLETE`, `IDEMPOTENCY_KEY_REUSED`, `EMPTY_RETURN` |
| 429 | `RATE_LIMITED` (customer lookup) |
| 502/504 | never surfaced for refunds (they become `202` + `UNKNOWN`). Only for health and diagnostic endpoints. |
| 500 | `INTERNAL_ERROR` (no stack trace, only the correlation id) |

### 10.3 Endpoints

**Auth (public)**

| Method | Path | Request | Response | Role | Transition |
|---|---|---|---|---|---|
| POST | `/auth/customer/lookup` | `{order_number, email}` | `{access_token, expires_in, customer:{id, name}}` | public (rate-limited) | — |
| POST | `/auth/staff/login` | `{username, password}` | `{access_token, expires_in, user:{id, name, role}}` | public | — |
| GET | `/auth/me` | — | `{principal_type, id, name, role}` | any | — |

**Customer**

| Method | Path | Request | Response | Role | Transition |
|---|---|---|---|---|---|
| GET | `/customer/orders` | — | `[{order_number, placed_at, delivered_at, status, total_paise}]` | CUSTOMER | — |
| GET | `/customer/orders/{order_number}` | — | order + `units[] {order_item_id, unit_index, product, price_paise, eligibility: EligibilityResult}` | CUSTOMER (own) | — |
| POST | `/customer/returns` 🔑 | `{order_number, refund_method, items:[{order_item_id, quantity, reason, note?}]}` | `201 ReturnDTO` (status `APPROVED`) | CUSTOMER (own) | T1 + T2 |
| GET | `/customer/returns` | — | `[ReturnDTO(summary)]` | CUSTOMER | — |
| GET | `/customer/returns/{id}` | — | `ReturnDTO` + timeline | CUSTOMER (own) | — |
| GET | `/customer/returns/{id}/label` | — | `{rma_number, tracking_number, qr_png_url, expires_at, address}` (the print page renders it) | CUSTOMER (own) | — |
| GET | `/customer/returns/{id}/label/qr.png` | — | `image/png` | CUSTOMER (own) | — |
| POST | `/customer/returns/{id}/items/{item_id}/photos` | multipart `file` (jpeg/png ≤ 5 MB) | `201 {photo_id}` | CUSTOMER (own), status `APPROVED`/`IN_TRANSIT` | — |
| POST | `/customer/returns/{id}/ship` 🔒 | — | `ReturnDTO` | CUSTOMER (own) | T3 |
| POST | `/customer/returns/{id}/cancel` 🔒 | — | `ReturnDTO` | CUSTOMER (own) | T5 |
| GET | `/customer/notifications` | — | `[{template, payload, created_at, read_at}]` | CUSTOMER | — |

**Warehouse Agent**

| Method | Path | Request | Response | Role | Transition |
|---|---|---|---|---|---|
| POST | `/warehouse/scan` | `{qr_token}` | `200 {return: ReturnDTO, already_received: bool}` | WAREHOUSE_AGENT | T4 (idempotent; see F-05, F-06) |
| GET | `/warehouse/returns?status=RECEIVED` | — | paginated `ReturnDTO` | WAREHOUSE_AGENT | — |
| GET | `/warehouse/returns/{id}` | — | `ReturnDTO` + customer photos | WAREHOUSE_AGENT | — |
| PUT | `/warehouse/returns/{id}/items/{item_id}/inspection` 🔒 | `{decision: ACCEPT\|REJECT, grade?: A\|B\|C, reject_reason?, disposition, notes?}` | `ReturnDTO` (new version) | WAREHOUSE_AGENT | T7 / T8 (PUT is idempotent) |
| POST | `/warehouse/returns/{id}/items/{item_id}/photos` | multipart | `201 {photo_id}` | WAREHOUSE_AGENT | — |
| POST | `/warehouse/returns/{id}/complete-inspection` 🔑🔒 | — | `200 ReturnDTO` (routed state; may be `REFUNDED`, or `REFUND_PENDING` + `UNKNOWN` → `202`) | WAREHOUSE_AGENT | T9 → T10/T11/T12 (→ T15) |

**Finance Reviewer**

| Method | Path | Request | Response | Role | Transition |
|---|---|---|---|---|---|
| GET | `/finance/queue?status=PENDING_FINANCE\|REFUND_FAILED\|REFUND_PENDING` | — | paginated `ReturnDTO` + `finance_review_reason`, customer flag, 30-day return count | FINANCE_REVIEWER | — |
| GET | `/finance/returns/{id}` | — | `ReturnDTO` + attempts + inspection photos + customer history | FINANCE_REVIEWER | — |
| POST | `/finance/returns/{id}/approve` 🔑🔒 | `{note?}` | `200 ReturnDTO` (`REFUNDED`) · `202` (`REFUND_PENDING`, attempt `UNKNOWN`) | FINANCE_REVIEWER | T13 (→ T15/T16) |
| POST | `/finance/returns/{id}/reject` 🔑🔒 | `{note}` | `ReturnDTO` | FINANCE_REVIEWER | T14 |
| POST | `/finance/returns/{id}/refund/retry` 🔑🔒 | `{method?: STORE_CREDIT}` | `200`/`202 ReturnDTO` | FINANCE_REVIEWER | T17 (→ T15/T16) |
| POST | `/finance/refund-attempts/{attempt_id}/recheck` | — | `{attempt_status, return: ReturnDTO}` | FINANCE_REVIEWER | maybe T15/T16 (same code as the reconciler) |
| GET | `/finance/reconciliation/latest` | — | `{run_at, checks:[{check, mismatches, sample_ids}]}` | FINANCE_REVIEWER, OPS_MANAGER | — |

**Operations Manager**

| Method | Path | Request | Response | Role | Transition |
|---|---|---|---|---|---|
| GET | `/analytics/return-rate-by-category?from&to` | — | `[{category_code, units_sold, units_returned, return_rate_pct}]` | OPS_MANAGER | — |
| GET | `/analytics/top-reasons?from&to&limit=5` | — | `[{reason, count, pct}]` | OPS_MANAGER | — |
| GET | `/analytics/cycle-time?from&to` | — | `{avg_hours, p50_hours, p90_hours, stages:{…}}` | OPS_MANAGER | — |
| GET | `/analytics/flagged-customers` | — | `[{customer_id, name, email_masked, returns_30d, open_returns, refunded_paise_90d, flagged_at}]` | OPS_MANAGER, FINANCE_REVIEWER | — |
| POST | `/ops/customers/{id}/clear-flag` 🔑 | `{note}` | `{customer_id, is_flagged:false}` | OPS_MANAGER | (customer event `FLAG_CLEARED`, audited) |
| POST | `/ops/returns/{id}/cancel` 🔒 | `{note}` | `ReturnDTO` | OPS_MANAGER | T5 (`cancel_reason=OPS`) |
| POST | `/ops/reconciliation/run` | — | the reconciliation report | OPS_MANAGER | — |
| GET | `/ops/audit?entity_type&entity_id&correlation_id` | — | paginated `audit_log` rows | OPS_MANAGER, FINANCE_REVIEWER | — |

**Platform (no auth, not under `/api/v1`)**: `GET /healthz` (process alive), `GET /readyz` (DB + gateway reachable), `GET /metrics` (Prometheus).

---

## 11. Auth and RBAC

**Mechanism.** Stateless JWT (HS256, `PyJWT`), secret from `RR_JWT_SECRET` (dev default in `.env.example`, overridden in Compose). The token is sent as a `Bearer` header and kept in **`sessionStorage`** by the SPA.
*Trade-off:* an httpOnly cookie would be safer against XSS but needs CSRF handling. For a localhost hackathon demo with synthetic data, Bearer is simpler and Playwright/Locust-friendly. This is noted as a production hardening item.

| Claim | Customer token | Staff token |
|---|---|---|
| `sub` | `customer:{id}` | `user:{id}` |
| `role` | `CUSTOMER` | `WAREHOUSE_AGENT` / `FINANCE_REVIEWER` / `OPS_MANAGER` |
| `exp` | 60 min | 8 h |

**Seeded staff users** (created by the seed script; password from `RR_SEED_STAFF_PASSWORD`, dev default `ReturnRight#2026`, hashed with argon2id):

| username | role | why |
|---|---|---|
| `warehouse1`, `warehouse2` | WAREHOUSE_AGENT | concurrent scan demo |
| `finance1`, `finance2` | FINANCE_REVIEWER | **two reviewers approving at once** (F-04) demo |
| `manager1` | OPS_MANAGER | dashboard |

**Customer lookup.** `POST /auth/customer/lookup` with `order_number` + `email` checks `orders.order_number = :n AND lower(customers.email) = lower(:e)`. On success it issues a token scoped to that customer, who can then see **all** their orders ([A-24]).
- On failure it always returns the same `404 NOT_FOUND`: "We couldn't find an order with those details." It never reveals which field was wrong.
- Rate limit: 5 attempts/min per IP and 10/hour per order number (in-memory token bucket; fine for one app process).

**Backend guards** (the authority):
- `Depends(require_roles(Role.FINANCE_REVIEWER))` on each router or endpoint. Routers are grouped by prefix, so each prefix gets one guard: `/customer/*` → CUSTOMER, `/warehouse/*` → WAREHOUSE_AGENT, `/finance/*` → FINANCE_REVIEWER, `/ops/*` + `/analytics/*` → OPS_MANAGER (with the listed exceptions).
- **Ownership:** every customer query goes through `returns.repo.for_customer(customer_id)`, which adds `WHERE customer_id = :me`. A miss gives `404`, never `403`.
- The state machine checks the role again (`Transition.roles`), so a misconfigured router cannot let a warehouse agent approve a refund.
- A test in `tests/api/test_rbac_matrix.py` iterates the route table × roles and asserts 401/403/2xx against the matrix below.

| Capability | Customer | Warehouse | Finance | Ops Manager |
|---|:-:|:-:|:-:|:-:|
| Own orders/returns, create/cancel/ship | ✅ | | | |
| Scan, inspect, complete inspection | | ✅ | | |
| Approve/reject/retry/recheck refunds | | | ✅ | |
| Flagged customers, audit, reconciliation report | | | ✅ | ✅ |
| Analytics dashboard, clear flag, ops cancel | | | | ✅ |

The Ops Manager intentionally **cannot** approve refunds (separation of duties).

**Frontend guards** (UX only): React Router 7 layouts `<RequireAuth roles={[...]}/>` wrap `/portal/*` (customer), `/warehouse/*`, `/finance/*` and `/manager/*`. Unauthenticated users are redirected to the right login page. A wrong role sees a 403 page. Nav items are filtered by role. A `401` from any API call clears the token and redirects to login.

---

## 12. Mock gateway contract

A separate FastAPI service (`gateway/`) with its own SQLite file (`/data/gateway.db`, its own volume), port **8100**.

### 12.1 Endpoints

**`POST /v1/refunds`** (header `Idempotency-Key` required)

```json
// request
{ "payment_reference": "pay_UPI_000123", "amount_paise": 550050, "currency": "INR",
  "merchant_reference": "refund:0193…", "metadata": { "rma": "RMA-26-000451" } }
// 201 Created (first time) | 200 OK + Idempotent-Replayed: true (replay)
{ "gateway_refund_id": "gw_rf_9f3a…", "idempotency_key": "rr-0193…-1",
  "status": "SUCCEEDED",              // SUCCEEDED | FAILED | PROCESSING
  "decline_code": null,               // e.g. CARD_CLOSED, ACCOUNT_FROZEN when FAILED
  "amount_paise": 550050, "currency": "INR",
  "created_at": "2026-11-20T09:12:01Z", "settled_at": "2026-11-20T09:12:01Z" }
```

| Case | Response |
|---|---|
| Missing key | `400 {"error":{"code":"IDEMPOTENCY_KEY_REQUIRED"}}` |
| Same key, same body hash | Stored result replayed (`200`, `Idempotent-Replayed: true`). **No second payout.** |
| Same key, different body | `422 IDEMPOTENCY_KEY_MISMATCH` |
| Same key still being processed | `409 REQUEST_IN_PROGRESS` |
| `payment_reference` ends in `-DECLINE` | `201` with `status: FAILED, decline_code: CARD_CLOSED` (deterministic decline for demos and tests) |
| `amount_paise <= 0` or currency ≠ INR | `422 VALIDATION_ERROR` |

How it is implemented: within one SQLite transaction (`BEGIN IMMEDIATE`), it inserts `refunds(idempotency_key UNIQUE, request_hash, status, …)` before any simulated processing. That serialises concurrent requests with the same key.

**`GET /v1/refunds/{idempotency_key}`** returns `200` with the same body, or `404 {"error":{"code":"NOT_FOUND"}}`. Used by the reconciler.
**`GET /v1/refunds?since=<iso>&limit=500`** returns `{items:[…], next_since}`. Used by the three-way reconciliation (R3).
**`GET /healthz`**, **`GET /metrics`** (`gateway_requests_total{endpoint,code}`, `gateway_refunds_total{status}`, `gateway_faults_injected_total{type}`, `gateway_request_duration_seconds`).

### 12.2 Fault injection ("break the gateway")

Faults can be configured through **env vars at start-up** or **live** through the admin API (`X-Admin-Token: $GATEWAY_ADMIN_TOKEN`). Each fault is decided with an RNG seeded from `FAULT_SEED` (default `42`), so a test run is reproducible.

| Env var / JSON field | Default | Effect | Exercises |
|---|---|---|---|
| `LATENCY_MS_MIN` / `LATENCY_MS_MAX` | 50 / 200 | Uniform added latency per request | latency histogram |
| `FAULT_TIMEOUT_RATE` | 0.0 | Sleep `TIMEOUT_SLEEP_MS` (default 10000) **before** processing, then process | F-02 (client gives up; refund may still happen later) |
| `FAULT_ERROR_RATE` | 0.0 | Return `503` **without** processing | F-19 |
| `FAULT_ERROR_AFTER_COMMIT_RATE` | 0.0 | Process + commit, then return `500` | F-03 variant |
| `FAULT_DROP_RESPONSE_RATE` | 0.0 | Process + commit, then hang the response for `TIMEOUT_SLEEP_MS` | **F-03 lost response** |
| `FAULT_DECLINE_RATE` | 0.0 | Return `status: FAILED, decline_code: ISSUER_DECLINED` | A-22 / T16 |
| `FAULT_PROCESSING_RATE` | 0.0 | Return `PROCESSING`; it settles to SUCCEEDED after `SETTLE_SECONDS` (default 20) when queried | reconciler polling |

Admin API:

| Method | Path | Body | Purpose |
|---|---|---|---|
| GET | `/admin/faults` | — | Current config |
| PUT | `/admin/faults` | partial JSON of the fields above | Change live |
| POST | `/admin/faults/profile/{name}` | — | Presets: `healthy`, `slow` (latency 2–6 s), `flaky` (error 0.2, timeout 0.1), `lost-responses` (drop 0.5), `blackhole` (timeout 1.0), `declines` (decline 0.3) |
| POST | `/admin/faults/reset` | — | Back to env defaults |
| GET | `/admin/refunds?limit=50` | — | Ledger of what the gateway "paid" (for the demo and the double-pay check) |
| POST | `/admin/reset-data` | — | Wipe gateway refunds (only when `GATEWAY_ENV=dev`; used by `seed --reset`) |

The SRE demo script `infra/scripts/chaos.ps1 lost-responses` switches profiles with a single command.

---

## 13. Audit log design

**Schema:** `audit_log` (Part 1 §6.3). It is append-only (trigger + REVOKE) and has no FKs, so an audit write can never be blocked by an entity delete. (We don't delete anything anyway.)

**What is captured per row**

| Field | Source |
|---|---|
| `occurred_at` | the `Clock` value of the unit of work (one timestamp for all rows in the txn) |
| `correlation_id` | `X-Correlation-ID` request header, or generated by middleware; for background jobs, one UUID per job run |
| `actor_type`, `actor_id`, `actor_role` | the authenticated principal; `SYSTEM` + `system:reconciler` / `system:label-expiry` / `system:router` for automatic events |
| `entity_type`, `entity_id` | `return`, `return_item`, `refund`, `refund_attempt`, `customer`, `label`, `finance_review` |
| `event` | the `ReturnEvent` name (`FINANCE_APPROVE`) or a domain event (`CUSTOMER_FLAGGED`, `FLAG_CLEARED`, `RECONCILE_CHECK`, `LEDGER_POSTED`) |
| `from_state`, `to_state` | status before and after (null for non-state events) |
| `before`, `after` | a **whitelisted** JSON snapshot via `to_audit_dict()` on each model: status, version, money fields, grade/decision, refund method. **No email, phone or password hash.** |
| `metadata` | event-specific: guard results, `idempotency_key`, gateway key, `eligibility.reason_code`, request IP for staff actions |

**Same-transaction guarantee**

1. FastAPI dependency `get_uow()` opens **one** `Session` + transaction per request: `with session.begin():`. It commits on success and rolls back on any exception.
2. `returns.apply_event()` and every other state-changing service function take that `session` and call `audit.record(session, …)` **before returning**. `audit.record` only does `session.add(AuditLog(...))`. It never commits or flushes on its own.
3. Because the audit row and the state change are in the same transaction, **both commit or neither does**. A failure to write the audit rolls back the transition.
4. The external gateway call happens post-commit. Its *outcome* is recorded by `finalize_attempt`, again in one transaction with the attempt update and the audit row.
5. Enforcement test: a SQLAlchemy `before_flush` listener in the test suite asserts that **every flushed change to `returns.status`, `return_items.status`, `refund_attempts.status` or `customers.is_flagged` has a matching pending `AuditLog`** in the same flush. Any slice that forgets the audit fails CI.

The customer timeline (Part 1 §4.4) and the manager's cycle-time stages (§14) are both **derived from `audit_log`**, so audit is load-bearing and therefore cannot silently rot.

---

## 14. Analytics

**Decision: compute live with plain SQL views, not materialised views.** The data size (2,000 orders, ~5k units, ~400 returns, ~5k audit rows) makes every query < 50 ms with the indexes defined in Part 1, so live data needs no refresh job and the dashboard updates immediately during the demo. *Trade-off:* this won't scale to millions of rows. The upgrade path is `CREATE MATERIALIZED VIEW … ; REFRESH MATERIALIZED VIEW CONCURRENTLY` on a 5-minute loop, which changes only the view definitions, not the API.

Views live in an Alembic migration (`analytics` schema). Grafana reads them through `rr_readonly`, and the API reads them with date filters.

```sql
-- Return rate by category (units returned / units delivered), for orders delivered in [from, to)
CREATE VIEW analytics.v_units_by_category AS
SELECT c.code AS category_code,
       o.delivered_at,
       oi.quantity                                                   AS units_sold,
       (SELECT count(*) FROM return_items ri
         WHERE ri.order_item_id = oi.id AND ri.status <> 'CANCELLED') AS units_returned
FROM order_items oi
JOIN orders o     ON o.id = oi.order_id AND o.status = 'DELIVERED'
JOIN products p   ON p.id = oi.product_id
JOIN categories c ON c.id = p.category_id;

-- API query:
SELECT category_code,
       sum(units_sold) AS units_sold,
       sum(units_returned) AS units_returned,
       round(100.0 * sum(units_returned) / NULLIF(sum(units_sold),0), 2) AS return_rate_pct
FROM analytics.v_units_by_category
WHERE delivered_at >= :from AND delivered_at < :to
GROUP BY category_code ORDER BY return_rate_pct DESC;

-- Top reasons (units), returns created in period
CREATE VIEW analytics.v_return_reasons AS
SELECT ri.reason, r.created_at, c.code AS category_code
FROM return_items ri
JOIN returns r ON r.id = ri.return_id AND r.status <> 'CANCELLED'
JOIN order_items oi ON oi.id = ri.order_item_id
JOIN products p ON p.id = oi.product_id
JOIN categories c ON c.id = p.category_id;
-- API: SELECT reason, count(*), round(100.0*count(*)/sum(count(*)) OVER (),1) pct
--      FROM analytics.v_return_reasons WHERE created_at >= :from AND created_at < :to
--      GROUP BY reason ORDER BY count(*) DESC LIMIT :limit;

-- Cycle time: request → terminal, plus stage durations from audit_log
CREATE VIEW analytics.v_return_cycle AS
SELECT r.id, r.status, r.created_at, r.closed_at,
       EXTRACT(EPOCH FROM r.closed_at - r.created_at)/3600.0 AS total_hours,
       min(a.occurred_at) FILTER (WHERE a.to_state = 'RECEIVED')        AS received_at,
       min(a.occurred_at) FILTER (WHERE a.to_state = 'INSPECTED')       AS inspected_at,
       min(a.occurred_at) FILTER (WHERE a.to_state = 'PENDING_FINANCE') AS finance_at,
       min(a.occurred_at) FILTER (WHERE a.to_state = 'REFUNDED')        AS refunded_at
FROM returns r
LEFT JOIN audit_log a ON a.entity_type = 'return' AND a.entity_id = r.id::text
WHERE r.status IN ('REFUNDED','REJECTED')
GROUP BY r.id;
-- API: avg(total_hours), percentile_cont(0.5/0.9) WITHIN GROUP (ORDER BY total_hours),
--      avg(received_at - created_at), avg(inspected_at - received_at), avg(refunded_at - inspected_at)

-- Flagged customers
CREATE VIEW analytics.v_flagged_customers AS
SELECT cu.id AS customer_id, cu.full_name,
       regexp_replace(cu.email, '(^.).*(@.*$)', '\1***\2') AS email_masked,
       cu.flagged_at, cu.flag_reason,
       count(r.id) FILTER (WHERE r.created_at >= now() - interval '30 days') AS returns_30d,
       count(r.id) FILTER (WHERE r.closed_at IS NULL)                          AS open_returns,
       coalesce(sum(r.approved_refund_paise) FILTER (WHERE r.status = 'REFUNDED'
                AND r.closed_at >= now() - interval '90 days'),0)             AS refunded_paise_90d
FROM customers cu
LEFT JOIN returns r ON r.customer_id = cu.id
WHERE cu.is_flagged
GROUP BY cu.id;
```

Also index `audit_log (entity_type, entity_id, occurred_at)` (already defined) for the cycle view.

The manager dashboard in the SPA uses Recharts: a bar chart for rate by category, a horizontal bar for top reasons, stat tiles for avg/p50/p90 cycle time with a stage breakdown, and a table of flagged customers with a "Clear flag" action.

---

## 15. Observability

**Instrumentation:** `prometheus-client`. The app runs **one Uvicorn worker** (enough for the demo and Locust at hackathon scale), which avoids multiprocess-mode complexity. If we ever scale to more workers, set `PROMETHEUS_MULTIPROC_DIR`. Logs are JSON lines on stdout (`ts, level, msg, correlation_id, route, return_id`).

### 15.1 Metrics (app)

| Metric | Type | Labels | Notes |
|---|---|---|---|
| `rr_http_requests_total` | counter | `method, route, status` | `route` = template, never the raw path (cardinality) |
| `rr_http_request_duration_seconds` | histogram | `method, route` | buckets 0.01…5 |
| `rr_return_transitions_total` | counter | `from_state, to_state, event` | incremented **after commit** |
| `rr_illegal_transitions_total` | counter | `from_state, event` | 409 ILLEGAL_TRANSITION / STALE_VERSION |
| `rr_eligibility_checks_total` | counter | `result (eligible\|ineligible), reason_code` | eligibility rejections |
| `rr_refund_attempts_total` | counter | `method, outcome (succeeded\|failed\|unknown)` | **refund success rate** source |
| `rr_refund_amount_paise_total` | counter | `method` | money out |
| `rr_gateway_request_duration_seconds` | histogram | `endpoint (create\|get), outcome (2xx\|4xx\|5xx\|timeout\|conn_error)` | buckets 0.05, 0.1, 0.25, 0.5, 1, 2, 3, 5, 10 |
| `rr_refund_attempts_unknown` | gauge | — | count in UNKNOWN (collected on scrape) |
| `rr_refund_attempts_unknown_oldest_age_seconds` | gauge | — | for the stuck alert |
| `rr_reconciler_runs_total` | counter | `result (ok\|error)` | |
| `rr_reconciliation_mismatches` | gauge | `check (R1..R5)` | |
| `rr_idempotency_replays_total` | counter | `route` | shows double clicks being absorbed |
| `rr_returns_open` | gauge | `status` | queue depth per state (collected on scrape) |
| `rr_customers_flagged` | gauge | — | |

Gateway metrics are in §12.1.

Key PromQL:

```promql
# Refund success rate (5m)
sum(rate(rr_refund_attempts_total{outcome="succeeded"}[5m]))
  / clamp_min(sum(rate(rr_refund_attempts_total[5m])), 1e-9)
# Gateway p95 latency
histogram_quantile(0.95, sum by (le) (rate(rr_gateway_request_duration_seconds_bucket{endpoint="create"}[5m])))
```

### 15.2 Alert rules (`infra/prometheus/alerts.yml`)

```yaml
groups:
  - name: returnright-refunds
    rules:
      - alert: RefundSuccessRateLow
        expr: |
          (
            sum(rate(rr_refund_attempts_total{outcome="succeeded"}[5m]))
            / sum(rate(rr_refund_attempts_total[5m]))
          ) < 0.90
          and sum(rate(rr_refund_attempts_total[5m])) > 0.01
        for: 2m
        labels: { severity: page }
        annotations:
          summary: "Refund success rate below 90% for 2m"
          description: "Check gateway latency/errors panel and /admin/faults. UNKNOWN attempts are reconciled automatically; do NOT retry manually."
      - alert: RefundReconciliationStuck
        expr: rr_refund_attempts_unknown_oldest_age_seconds > 900
        for: 1m
        labels: { severity: ticket }
        annotations:
          summary: "A refund attempt has been UNKNOWN for >15 minutes"
```

Alerts are shown in the Prometheus UI and the Grafana alert list panel. There is no Alertmanager, which keeps the Compose file to the five required services.

### 15.3 Grafana dashboard "ReturnRight — Operations" (provisioned JSON)

| Row | Panels |
|---|---|
| Money path | Refund success rate (stat + time series) · Refund attempts by outcome (stacked) · UNKNOWN attempts + oldest age (stat, red > 900 s) · Reconciliation mismatches (stat, must be 0) · Idempotent replays/min |
| Gateway | Latency p50/p95/p99 · Requests by outcome (timeout/5xx highlighted) · Gateway-side injected faults by type |
| Workflow | State transitions/min by `to_state` · Open returns by status (bar gauge = queue depths) · Illegal transitions/min · Eligibility rejections by reason code |
| API | RPS by route · error rate by status class · latency p95 by route |
| Business (Postgres datasource, `rr_readonly`) | Return rate by category · Top reasons · Avg cycle time · Flagged customers table |

---

## 16. Seed data strategy

Command: `docker compose run --rm app python -m app.seed --as-of 2026-11-20 --reset`

- **Determinism:** `SEED = 20261120`. `random.Random(SEED)` is used for every choice, plus `Faker.seed(SEED)` with locale `en_IN`. All dates are computed **relative to `--as-of`** (default: today in IST), at a fixed noon IST anchor. The same seed + `--as-of` gives a byte-identical dataset, and seeding on demo day keeps the day-30/31 edge cases on the boundary. Tests use `FixedClock(as_of)`.
- **Process:** reference data → customers → orders → **historical returns via the service layer** (`apply_event` with `FixedClock` set to each historical moment, the real gateway on its `healthy` profile, so the gateway ledger matches and R3 reconciles) → `validate_seed.py`. Raw INSERTs are used **only** for reference data, customers and orders.
- **Guard:** the script refuses to run against a non-empty DB without `--reset`. `--reset` truncates in FK order and also calls the gateway's `POST /admin/reset-data` (dev only).

### 16.1 Catalogue (hand-curated lists, not Faker, so prices and categories are controlled)

| Category | Policy | # products | Price range (₹) | Examples |
|---|---|---|---|---|
| ELECTRONICS | 10 days | 8 | 1,499 – 84,999 | Laptop 14" (62,999), Phone (34,999), Earbuds (2,499), Smartwatch (12,499) |
| APPAREL | 30 days | 10 | 499 – 6,999 | Jeans, kurta, jacket |
| FOOTWEAR | 30 days | 6 | 999 – 11,999 | Running shoes (11,999) |
| HOME_KITCHEN | 30 days | 8 | 399 – 24,999 | Mixer grinder, air fryer (8,999), sofa cover |
| INNERWEAR | not returnable | 4 | 299 – 1,499 | |
| PERISHABLES | not returnable | 4 | 199 – 1,999 | Dry fruit box, chocolates |

That is 40 products in total. 7 SKUs cost more than ₹10,000.

### 16.2 Customers (500) and orders (2,000)

- Customers: Faker `en_IN` names. Email = `firstname.lastname.{i}@example.com` (unique by construction; a reserved domain).
- Orders: `placed_at` uniformly over the last 180 days; status mix **90 % DELIVERED, 4 % SHIPPED, 3 % PLACED, 3 % CANCELLED**; `delivered_at = placed_at + 2–7 days` (random time of day IST); 1–4 lines, qty 1–3 (weighted to 1); payment mix CARD 40 / UPI 40 / NETBANKING 10 / WALLET 10; `payment_reference = pay_{method}_{n:06}`; `order_number = RR-2026-{n:06}`. Customer order counts follow a Zipf-like distribution, so some customers have many orders.
- Historical returns: about 12 % of delivered orders, reasons weighted (SIZE_FIT 30 %, DAMAGED 15 %, DEFECTIVE 15 %, CHANGED_MIND 15 %, …), grades A 60 / B 25 / C 15, reject rate 8 %. They end in a mix of states (REFUNDED 60 %, REJECTED 8 %, CANCELLED 7 %, and some open in every non-terminal state, so every work queue has items on demo day).

### 16.3 Golden edge cases (fixed order numbers, written to `docs/demo-data.md`)

Customer for most edge cases: `edge.customer@example.com`. Delivery times are anchored relative to `as_of`.

| Order | Scenario | Expected |
|---|---|---|
| `RR-EDGE-001` | Apparel delivered exactly 30 days ago | ELIGIBLE, "last day" message |
| `RR-EDGE-002` | Apparel delivered 31 days ago | `RETURN_WINDOW_EXPIRED` |
| `RR-EDGE-003` | Electronics, 10 days | ELIGIBLE |
| `RR-EDGE-004` | Electronics, 11 days | `RETURN_WINDOW_EXPIRED` |
| `RR-EDGE-005` | Apparel delivered 30 days ago at **00:10 IST** (previous UTC date) | ELIGIBLE (timezone trap) |
| `RR-EDGE-006` | Innerwear, 2 days ago | `CATEGORY_NOT_RETURNABLE` |
| `RR-EDGE-007` | Perishables, 1 day ago | `CATEGORY_NOT_RETURNABLE` |
| `RR-EDGE-008` | Mixed basket: jeans + innerwear | Jeans selectable, innerwear disabled with reason |
| `RR-EDGE-009` | Laptop ₹62,999 | > 10k → PENDING_FINANCE |
| `RR-EDGE-010` | Running shoes ₹11,999 (graded C → ₹6,000) | **Auto-refund** (threshold after grade, [A-12]) |
| `RR-EDGE-011` | Two items ₹6,000 each | Return total ₹12,000 → finance, although each item is below the threshold |
| `RR-EDGE-012` | One item exactly ₹10,000.00 | Auto-refund (not strictly greater) |
| `RR-EDGE-013` | Line qty 3, one unit already returned | 2 units selectable |
| `RR-EDGE-014` | Order SHIPPED (not delivered) | `ORDER_NOT_DELIVERED` |
| `RR-EDGE-015` | Cancelled order | `ORDER_CANCELLED` |
| `RR-EDGE-016` | `payment_reference` ends in `-DECLINE` | Gateway FAILED → REFUND_FAILED → retry as store credit |
| `RR-EDGE-017` | Delivered today (day 0) | ELIGIBLE |
| `flag.near@example.com` | Has **3** returns in the last 30 days, plus an eligible order | The next submit flags the customer and routes to finance |
| `flag.already@example.com` | Already flagged, cheap item | ₹799 return still goes to finance |
| `flag.cancelled@example.com` | 2 returns + 1 cancelled in 30 days | Cancelled returns count ([A-14]), so the next submit flags |

Demo personas also get a fixed email for the customer login: `asha.demo@example.com` + order `RR-2026-000001`.

### 16.4 Validation (`app/seed/validate.py`, also run in CI)

- Counts: 500 customers, 40 products, 6 categories, 2,000 + golden orders.
- No `return_items` row violates the engine when re-evaluated at its `returns.created_at`.
- Ledger R1–R5 = 0 mismatches. Global ledger balance = 0.
- `customers.is_flagged` ⇔ the flag rule held at some request (recomputed from `returns`).
- Every golden order has its expected eligibility `reason_code` at `as_of`.

---

## 17. Repository structure

```text
manthan-d1-a-team13/
├── CLAUDE.md
├── README.md                     # quick start: compose up, seed, logins, demo script
├── .gitattributes                # * text=auto eol=lf (Windows: keep shell/SQL files LF)
├── .env.example
├── docker-compose.yml            # app, db, gateway, prometheus, grafana
├── .github/workflows/
│   ├── ci.yml                    # lint, typecheck, unit, integration (db+gateway services), e2e
│   └── load.yml                  # manual: Locust smoke
├── backend/
│   ├── Dockerfile                # multi-stage: node build SPA → python runtime serves /static
│   ├── pyproject.toml            # uv; ruff, mypy, import-linter contracts
│   ├── alembic.ini
│   ├── migrations/versions/
│   └── app/
│       ├── main.py               # app factory, lifespan (reconciler, label-expiry loops)
│       ├── core/                 # config, db, clock, money, errors, idempotency, metrics, logging
│       ├── modules/
│       │   ├── auth/  orders/  eligibility/  labels/  audit/  notifications/
│       │   ├── ledger/  gateway_client/  returns/  refunds/
│       │   ├── inspection/  finance/  analytics/
│       │   └── <module>/{models.py, schemas.py, service.py, router.py}
│       ├── api/                  # router assembly, deps (uow, current_principal, require_roles)
│       ├── jobs/                 # reconciler.py, label_expiry.py, idempotency_cleanup.py
│       └── seed/                 # __main__.py, catalogue.py, golden.py, validate.py
├── gateway/
│   ├── Dockerfile
│   ├── pyproject.toml
│   └── gateway/{main.py, store.py, faults.py, schemas.py}
├── frontend/
│   ├── package.json  vite.config.ts  tsconfig.json
│   └── src/
│       ├── api/                  # typed client (generated from OpenAPI) + error mapping
│       ├── auth/                 # token store, RequireAuth
│       ├── features/{customer, warehouse, finance, manager}/
│       ├── components/           # Timeline, Money, StatusBadge, ConfirmDialog (idempotency key)
│       └── routes.tsx
├── tests/
│   ├── unit/                     # pure: eligibility, state table, money, ledger templates
│   ├── integration/              # real Postgres: constraints, triggers, concurrency, refunds+gateway faults
│   ├── api/                      # FastAPI TestClient: RBAC matrix, error envelope, idempotency headers
│   ├── e2e/                      # Playwright: persona journeys
│   ├── load/locustfile.py        # browse, submit returns, approve storm with duplicate keys
│   └── conftest.py               # FixedClock, db fixtures, gateway fault fixtures
├── infra/
│   ├── prometheus/{prometheus.yml, alerts.yml}
│   ├── grafana/provisioning/{datasources/, dashboards/} + dashboards/returnright.json
│   ├── postgres/init/01-roles.sql
│   └── scripts/{chaos.ps1, chaos.sh, demo-reset.ps1}
└── docs/
    ├── architecture/{01-architecture-part1.md, 02-architecture-part2.md}
    ├── adr/ADR-001-refund-idempotency.md
    ├── runbooks/refund-unknown.md
    └── demo-data.md              # generated by seed
```

---

## 18. Draft CLAUDE.md

Written to the repo root: [CLAUDE.md](../../CLAUDE.md).

---

## 19. Build plan (vertical slices, riskiest first)

Assumes a 4-person team over ~4.5 days. Tracks: **B** = backend, **G** = gateway/infra, **F** = frontend, **Q** = QA/SRE. Each slice ends with a commit and green CI.

| # | Slice | Contents | Definition of done | How to verify |
|---|---|---|---|---|
| S0 | **Walking skeleton** (day 1 AM, all) | Compose with 5 services, FastAPI `/healthz` + `/metrics`, Alembic baseline + DB roles, gateway `/healthz`, Vite app served by FastAPI, CI with ruff/mypy/pytest/import-linter | `docker compose up` on a clean Windows laptop gives all services healthy; CI green | `curl :8000/readyz`, `:8100/healthz`, Prometheus targets UP |
| S1 | **Schema + state machine core** (B, day 1) | All tables/enums/constraints/triggers from Part 1 §6; `apply_event` + transition table + guards; audit writer; `FixedClock` | Every T1–T17 transition is unit-tested; illegal pairs give 409; trigger set == Python table; audit-coverage listener active | `pytest tests/unit/test_state_table.py tests/integration/test_constraints.py`; concurrency test (2 threads approve) → exactly one wins |
| S2 | **Mock gateway** (G, day 1) | `/v1/refunds` with idempotency, GET by key, list, fault profiles, metrics | The same key 50× concurrently gives 1 payout; key mismatch → 422; every fault profile behaves as specified | `pytest gateway/tests`; `chaos.ps1 lost-responses` then POST ×10 → `/admin/refunds` shows correct counts |
| S3 | **Refund engine + ledger + reconciler** (B, day 2) | `refunds.initiate/execute/finalize_attempt`, store credit, ledger templates + balance trigger + immutability, reconciler loop, recheck | **ADR-001 scenarios A/B/C pass** against the real gateway with faults; Locust "approve storm" (10 users, duplicate keys) gives `gateway payouts == REFUNDED returns`; R1–R5 = 0 | `pytest tests/integration/test_refund_idempotency.py -k "timeout or lost or double or concurrent or crash"` |
| S4 | **Eligibility engine + seed** (B/Q, day 2) | Pure engine + messages; seed with golden cases; validate script | Boundary tests pass; seed is deterministic (two runs → identical `pg_dump` hash); validate passes | `pytest tests/unit/eligibility`; `python -m app.seed --as-of … && python -m app.seed.validate` |
| S5 | **Customer journey** (F+B, day 2–3) | Lookup, orders with per-unit eligibility, submit (🔑), label print page + QR, ship, cancel, timeline | A customer can return `RR-EDGE-001` and see Approved with a QR; `RR-EDGE-008` shows the innerwear reason | Playwright `customer.spec.ts` |
| S6 | **Warehouse journey** (F+B, day 3) | Scan (keyboard-wedge input), inspect per unit, complete → auto-route → auto-refund | Scan → grade C → complete gives REFUNDED with a 50 % refund; a double scan is harmless; scanning a cancelled return gives a clear message | Playwright `warehouse.spec.ts`; ledger shows the condition-recovery line |
| S7 | **Finance journey** (F+B, day 3) | Queue, approve/reject with ConfirmDialog keys + If-Match, retry/store credit, recheck UNKNOWN | `RR-EDGE-009` approve under `lost-responses` ends REFUNDED exactly once; two browsers approving gives one success + one 409 | Playwright `finance.spec.ts` (two contexts) |
| S8 | **Observability** (G/Q, day 3–4) | All metrics, Prometheus alerts, provisioned Grafana dashboard | Under the `flaky` profile the success-rate panel drops and `RefundSuccessRateLow` fires within ~3 min, then recovers on `healthy` | Run the chaos script while watching Grafana; screenshot for the demo |
| S9 | **Manager dashboard + flags** (F+B, day 4) | Analytics endpoints/views, charts, flagged table, clear flag | Numbers match hand-checked SQL on seed data; the `flag.near` scenario appears after a submit | API tests vs fixture data; Playwright `manager.spec.ts` |
| S10 | **Hardening + demo** (all, day 4–5) | Locust scenario, full Playwright suite in CI, README quick start, runbook, demo script, cut list applied | A fresh clone → `compose up` → seed → scripted demo in < 10 min | Dry-run the demo on a second laptop |

**Order rationale:** S1–S3 contain every hard correctness property (state, money, idempotency) and have no UI dependency, so they are built and proven first by tests. The UI slices (S5–S7) are thin over an already-proven core. The gateway (S2) runs in parallel with S1, so S3 can start on day 2.

---

## 20. Top risks and what to cut

### 20.1 Risks

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| K1 | Idempotency/reconciler subtle bugs (double pay in the demo) | Med | **Critical** | Built first (S3); fault-injection integration tests + Locust storm in CI; DB constraints as the backstop |
| K2 | Timezone/boundary bugs in eligibility | Med | High | Pure engine + boundary tests + DB trigger parity test + golden seed cases |
| K3 | Windows/Docker friction (CRLF in scripts, volume perf, port clashes) | High | Med | `.gitattributes` LF; named volumes, not bind mounts, for the DB; all tests can run inside containers; PowerShell + sh chaos scripts |
| K4 | Bleeding-edge versions (Postgres 18 image, Node 26, React 19 libraries) incompatibility | Med | Med | Pin exact image/package versions on day 1 (S0); verify the official `postgres:18` image and Playwright 1.60 browsers install on Windows early |
| K5 | Frontend takes longer than planned | High | Med | Plain CSS/minimal component lib; generated API client; one screen per persona; cut list below |
| K6 | Integration drift between B and F | Med | Med | API contract (this doc §10) → FastAPI OpenAPI is the source → generated TS client; contract tests |
| K7 | Seed via service layer is slow | Low | Low | ~250 returns × few ms is fine. Fall back to one bulk session with the same service functions. |
| K8 | Scope creep | High | High | This cut list is agreed **before** day 1 |

### 20.2 Protected must-haves (never cut)

1. The state machine with the single transition function, guards, row lock and audit in the same transaction.
2. The eligibility engine (all rules, IST boundaries) with tests.
3. Refund idempotency: client key + DB uniqueness + gateway key + UNKNOWN + reconciler.
4. The append-only double-entry ledger, with R1/R4 reconciliation.
5. The gateway with at least the `timeout`, `drop_response` and `error` faults, plus the `healthy`/`lost-responses`/`flaky` profiles.
6. Happy-path UI for Customer → Warehouse → Finance, and a manager view with the four metrics (even as tables).
7. The refund success rate + gateway latency metrics, one alert, and one Grafana dashboard.
8. Seed with golden edge cases; pytest + at least one Playwright end-to-end journey in CI.

### 20.3 Cut order (first to go → last)

1. Photo uploads (customer and inspection). Keep the table, hide the UI.
2. In-app notifications UI (rows still written).
3. `REGRADE_ITEM` (inspector re-submits before completion instead).
4. Label expiry background job. Keep `expires_at` and enforce it on scan only.
5. Ops cancel + audit browser UI (the API stays).
6. `FAULT_PROCESSING_RATE` / PROCESSING status in the gateway.
7. Three-way reconciliation R3 against the gateway list (keep R1, R4, R5).
8. Grafana Postgres business panels (the manager dashboard in the SPA covers them).
9. Store-credit switch on retry (keep store credit as a customer choice).
10. Charts in the manager dashboard become tables.
11. Locust beyond the single "approve storm" scenario.
