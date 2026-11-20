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
