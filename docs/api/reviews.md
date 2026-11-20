# Finance and operations reviews

These screens were not in the Figma export we had, but the flows need them: without them, refunds above ₹10,000 and returns from flagged customers would wait forever.

## Finance (`FINANCE_REVIEWER`)

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/finance/returns/pending` | Refunds above ₹10,000 waiting for approval (A-08) |
| `POST` | `/api/v1/finance/returns/{rma}/approve` | Approve and pay the refund at once |
| `POST` | `/api/v1/finance/returns/{rma}/decline` | Decline; body `{"note": "…"}` (required). The return becomes `REJECTED` with reason `REFUND_DECLINED`. |

Approve answers `{"rmaNumber", "status", "refundStatus"}`: normally `REFUNDED` / `SUCCEEDED`.

## Operations (`OPERATIONS_MANAGER`)

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/operations/returns/flagged` | Returns held because the customer made more than 3 requests in 30 days (A-10, A-11), with `requestsInLast30Days` |
| `POST` | `/api/v1/operations/returns/{rma}/approve` | Approve: the customer's label becomes available |
| `POST` | `/api/v1/operations/returns/{rma}/decline` | Decline; body `{"note": "…"}` (required). Reason `REQUEST_DECLINED`. |

## Errors (both areas)

| Status | `code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Decline without a note. |
| 403 | `FORBIDDEN` | Wrong role. |
| 404 | `RETURN_NOT_FOUND` | Unknown RMA. |
| 409 | `ILLEGAL_TRANSITION` | The return isn't waiting for this decision (e.g. approved twice). |
