# API layer

- `client.ts` is the fetch wrapper. It reads `VITE_API_BASE_URL` (default `/api`) and turns non-2xx responses into `ApiError`.
- `types.ts` defines every request and response payload. **Money is in paise** (₹1,899 → `189900`).
- `inspection.ts` holds the Scan & inspect endpoints. When `VITE_USE_MOCKS` is not `"false"` it serves `mocks/inspection.mock.ts` instead.

To use the real backend, create `.env.local` with `VITE_USE_MOCKS=false` and `VITE_API_BASE_URL=http://localhost:8080/api`.

## Scan & inspect endpoints

| Method | Path | Request | Response |
| --- | --- | --- | --- |
| POST | `/returns/scan` | `ScanReturnRequest` | `ReturnDetails` (404 `RETURN_NOT_FOUND`) |
| GET | `/inspection/config` | – | `InspectionConfig` |
| GET | `/inspection/stats/today` | – | `InspectionStats` |
| POST | `/returns/:returnId/inspection` | `InspectionDecisionRequest` | `InspectionDecisionResponse` (422 `GRADE_REQUIRED`) |

Error body: `{ "message": string, "code"?: string }`.

### Example: scan

```http
POST /returns/scan
{ "qrCode": "rr-qr-7f3a9c41" }
```
```json
{
  "id": "ret_00731",
  "rmaNumber": "RMA-2026-00731",
  "qrCode": "rr-qr-7f3a9c41",
  "item": { "name": "Linen shirt", "sku": "LS-L-WHT", "pricePaise": 189900 },
  "customer": { "id": "cus_4412", "name": "Ananya Rao" },
  "reason": "Too large",
  "refundMethod": "original_payment",
  "status": "received",
  "previousStatus": "in_transit",
  "photos": [{ "url": "https://…/1.jpg", "alt": "Front" }],
  "customerNote": "Ordered L, fits like XL. Tags still attached."
}
```

### Decision payloads

Every decision call needs the header `Idempotency-Key: <uuid>`. A retry that reuses the same key returns the original response and never pays twice.

`requiresFinanceApproval` is `true` when the refund is **above ₹10,000** or the customer is **flagged** (more than 3 returns in 30 days). The client sends it as a hint. The server recalculates it, and the server's value is the one used.

**Accept (A):** no popup.
```http
POST /returns/ret_00731/inspection
{ "decision": "accept", "grade": "C", "notes": "Seam torn at left sleeve, 3 cm", "requiresFinanceApproval": false }
```

**Partially accept (P):** sent from the popup.
- `refundAmountPaise` must be between 1 and the grade's refund (grade C is capped at 50%).
- `reason` is shown to the customer.
```http
{ "decision": "partial_accept", "grade": "B", "refundAmountPaise": 300000,
  "reason": "Missing parts or accessories", "notes": "Original box and spare laces missing.",
  "requiresFinanceApproval": false }
```

**Reject (R):** sent from the popup.
- `reason` is one of `not_as_described`, `used_or_worn`, `missing_parts_or_tags`, `outside_return_window` or `other`. Its label is shown to the customer.
- `notes` is required when the reason is `other`.
```http
{ "decision": "reject", "grade": null, "reason": "used_or_worn", "notes": "Visible wear on the hem; tags removed." }
```

**Response**
```json
{
  "inspectionId": "insp_9f2",
  "returnId": "ret_00731",
  "decision": "accept",
  "grade": "C",
  "refundAmountPaise": 94950,
  "requiresFinanceApproval": false,
  "outcome": "refund_initiated",
  "status": "refunded",
  "decidedAt": "2026-11-20T10:15:00Z"
}
```

`outcome` is one of:
- `refund_initiated`: the refund was paid and the return is now `refunded`.
- `sent_to_finance`: `requiresFinanceApproval` was true, so the return stays `inspected` until finance approves it.
- `rejected`: the return was rejected.

Error codes are listed under `InspectionErrorCode` in `types.ts`.

## Customer tracking

`GET /returns/:rmaNumber/tracking` returns a `ReturnTracking` object (see `types.ts`). The customer status page at `/track/:rmaNumber` uses it.

- `history` lists every status the return has reached, with its timestamp. Every state change must be added to it.
- `requiresFinanceApproval` is the flag saved at inspection:
  - `true`: the page shows an "Under review" badge and a **Refund review** step between Inspected and Refunded.
  - `false`: there is no review step.
  - `null`: the return isn't inspected yet, or it was rejected.
- `inspection`, `rejection` and `refund` fill in the explanation text, such as "Why ₹949.50 and not ₹1,899?", the rejection reason, and the payment reference.
- **Never** include internal data here: the customer-flag reason, audit entries, or agent names. The page deliberately doesn't tell the customer *why* a review is happening.
- In production this endpoint must check the order email or a signed link. An RMA number alone can be guessed.

## Finance approval

A return lands in the queue when inspection set `requiresFinanceApproval: true`, meaning the refund is above ₹10,000 or the customer is flagged. When the server queues it, it assigns a **refund key** (`rr-refund-88214`). That makes this "one refund per return" idempotency: the key belongs to the refund, not to a click.

| Method | Path | Request | Response |
| --- | --- | --- | --- |
| GET | `/finance/queue` | – | `FinanceQueueItem[]`, oldest first |
| POST | `/finance/refunds/:returnId/approve` | header `Idempotency-Key: <refundKey>` | `FinanceDecisionResponse` (`outcome: "paid"`) |
| POST | `/finance/refunds/:returnId/decline` | `DeclineRefundRequest` + header `Idempotency-Key: <refundKey>` | `FinanceDecisionResponse` (`outcome: "declined"`) |

Rules the server must enforce:
- **Retries pay once:** an approve repeated with the same key, whether a double click, a retry after a timeout, or two requests at the same time, returns the original response and pays only once. The ledger also holds at most one refund per return.
- **Wrong keys are refused:**
  - A key that doesn't belong to the return → 422 `IDEMPOTENCY_KEY_MISMATCH`.
  - Approving after declining, or the other way round → 422 `IDEMPOTENCY_KEY_REUSED`.
- **Only waiting returns:** a return that is no longer waiting → 409 `NOT_AWAITING_APPROVAL`. The screen removes that row and reloads the queue.
- **Status and audit:**
  - Approving moves the return from `inspected` to `refunded` (audit actor `finance_reviewer`) and adds a ledger entry.
  - Declining moves it from `inspected` to `rejected`, with reason `declined_after_review`. The note is shown to the customer.

## Refunds, gateway and ledger

| Method | Path | Request | Response |
| --- | --- | --- | --- |
| GET | `/refunds` | – | `RefundRecord[]`, newest first |
| GET | `/ledger` | – | `LedgerResponse`: entries (newest first), totals and `balanced` |
| POST | `/refunds/:refundId/retry` | header `Idempotency-Key: <refund key>` | `RefundRecord` |

- **One refund per return.** Each return gets exactly one refund record, and its key (`rr-refund-…`) is created along with it. Every gateway attempt, automatic retry and manual retry uses that same key, and the gateway charges each key at most once.
- **Automatic retries.** The server makes 1 attempt plus 3 automatic retries. If all of them time out, the refund is `failed`: nothing was charged, there are no ledger rows, and the return stays `inspected`. Finance retries it from the Refunds & ledger screen.
- **Double-entry ledger, insert-only.** Rows are posted only after the gateway confirms the payment, and are never updated or deleted. A correction would be a new reversing journal.
  - Gateway refund: debit `returns_expense`, credit `gateway_clearing` (type `REFUND`).
  - Store credit: debit `returns_expense`, credit `store_credit_liability` (type `STORE_CREDIT`), with no gateway call.
- **Declined refunds.** When finance declines, the refund record becomes `cancelled` and is never posted.
- **Retry errors.**
  - Retrying with another refund's key → 422 `IDEMPOTENCY_KEY_MISMATCH`.
  - Retrying a refund that isn't failed → 409 `NOT_RETRYABLE`.
  - Retrying a refund that already succeeded returns the existing result and doesn't charge again.

To test failures (for example the SRE "break the gateway" exercise), call `setMockGatewayMode('timeout')` from `mocks/gateway.mock.ts`.

## Manager dashboard

`GET /dashboard?days=7|14|30` returns a `DashboardResponse`. The mock calculates it with the pure functions in `src/domain/returns/analytics.ts`, run over the seed data from `mocks/seed/synthetic.ts`. That seed is deterministic: 2,000 orders, 40 products in 6 categories, 500 customers. The backend can use the same functions.

- **Return rate:** orders in the period that have a return, divided by all orders in the period.
- **By category:** innerwear and perishables are `null` ("n/a"), not 0%, because they can't be returned.
- **Avg cycle time:** the mean time from request to refunded or rejected, for returns closed in the period.
- **Refund success rate:** successful gateway attempts divided by all attempts.
- **Flagged customers:** always uses the fixed rule of more than 3 returns in the last 30 days, whatever period is selected.

## Customer flag review

| Method | Path | Request | Response |
| --- | --- | --- | --- |
| GET | `/customers/:customerId/returns-profile` | – | `CustomerProfile` |
| POST | `/customers/:customerId/flag-review` | `FlagReviewRequest` `{ action: "keep" \| "clear", note }` | `CustomerProfile` |

- **Note required.** An empty note → 422 `NOTES_REQUIRED`.
- **Flagged customers only.** Reviewing a customer who isn't flagged → 409 `NOT_FLAGGED`.
- **Audit rows.** `keep` writes a `FLAG_REVIEWED` audit row. `clear` writes a `FLAG_CHANGED` row (flagged → cleared).
- **When a cleared flag comes back.** It stays off until a *new* return arrives that takes the 30-day count over 3 (`isFlagActive` in `src/domain/returns/flagging.ts`).
- **While flagged,** every refund for the customer goes to finance, whatever the amount (`requiresFinanceApproval`).

## Audit log

`GET /audit?rma=&actor=&action=&days=&customer=` returns `AuditLogEntry[]`, newest first. It's read-only: the API has no way to edit or delete a row.

- **Write rows inside the state change's own database transaction.** Every row written by one API call shares a `requestId`. A replayed retry (same idempotency key) writes nothing.
- **Actions:**
  - `STATUS_CHANGED`: a change of the return's status.
  - `GRADED`: received → inspected, written by the agent.
  - `REFUND_ROUTED`: sent to `refund_pending` or `finance_review`.
  - `REFUND_APPROVED` / `REFUND_DECLINED`: the finance decision.
  - `REFUND_ATTEMPTED`: one row per gateway attempt, with the key, the result and the latency.
  - `FLAG_CHANGED` / `FLAG_REVIEWED`: manager flag reviews.
- **Pseudo-states.** `refund_pending`, `finance_review`, `refund_failed` and `cancelled` are refund states, not return statuses. The return's own status only changes on `STATUS_CHANGED` and `GRADED` rows.
