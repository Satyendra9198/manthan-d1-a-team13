# Warehouse inspection

Figma: **Agent – Inspection** (scan, "Find return", condition grade keys 1–3, "Today: 27 inspected") and **Agent – Inspection dialogs** ("Reject return", "Partially accept").

All endpoints need a `WAREHOUSE_AGENT` token. An action that isn't possible in the return's current state answers **409 `ILLEGAL_TRANSITION`**; two agents changing the same return at once gives **409 `CONCURRENT_UPDATE`** to the second.

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/warehouse/returns/queue` | Received parcels waiting for inspection, oldest first |
| `GET` | `/api/v1/warehouse/returns/{rma}` | "Find return" after scanning the label QR (the QR holds the RMA) |
| `POST` | `/api/v1/warehouse/returns/{rma}/receive` | Scan the parcel in (from `APPROVED` or `IN_TRANSIT`, A-17) |
| `POST` | `/api/v1/warehouse/returns/{rma}/inspection` | Grade every line, accept all or part |
| `POST` | `/api/v1/warehouse/returns/{rma}/reject` | "Reject return" dialog |
| `GET` | `/api/v1/warehouse/returns/{rma}/photos/{photoId}` | A customer photo (image bytes) |
| `GET` | `/api/v1/warehouse/inspections/today` | "Today: N inspected" |

## Find return

The response lists every line with its `returnItemId` (send it back in the inspection), the customer's `reason`, `customerComment` and `photos[].url`, plus `allowedActions`, e.g. `["RECEIVE"]` before scanning in, `["COMPLETE_INSPECTION", "REJECT_AFTER_INSPECTION"]` after. Use it to enable buttons.

## Inspection

```json
{
  "items": [
    { "returnItemId": "77777777-bbbb-0000-0000-000000000001", "grade": "C", "acceptedQuantity": 1 },
    { "returnItemId": "77777777-bbbb-0000-0000-000000000002", "grade": "A", "acceptedQuantity": 1,
      "rejectionReason": "MISSING_PARTS_OR_TAGS", "note": "One wallet without tags" }
  ]
}
```

Rules (all problems come back together as **422 `INVALID_INSPECTION`**, one `details` entry per line):

- Every line on the return exactly once.
- `acceptedQuantity` from 0 to the quantity returned. Fewer than returned is a **partial accept** (A-05) and needs a `rejectionReason`.
- `grade` (`A`, `B`, `C`) is required when anything is accepted.
- `rejectionReason` must be one of `NOT_AS_DESCRIBED`, `USED_OR_WORN`, `MISSING_PARTS_OR_TAGS`, `OUTSIDE_RETURN_WINDOW`, `OTHER`; `OTHER` needs a `note`.

**200**

```json
{ "rmaNumber": "RMA-DEMO-00006", "status": "REFUNDED", "refundAmountPaise": 254850,
  "financeApprovalRequired": false, "refundStatus": "SUCCEEDED" }
```

What happens next:

- Refund = unit price × accepted units × grade % (A and B 100%, C 50%), rounded down to the paisa (A-06, A-07).
- **Above ₹10,000** → `AWAITING_FINANCE_APPROVAL`, nothing paid yet (A-08).
- Otherwise the refund is paid at once → `REFUNDED`. If the gateway times out the return stays `REFUND_PENDING` and a background job retries every minute with the same key, so it is never paid twice (A-15, A-16).
- **Nothing accepted** → the return is `REJECTED` with the first line's reason (A-14).

## Reject return

```json
{ "reason": "USED_OR_WORN", "note": "Sweat marks on the waistband" }
```

Same reason list as above; `OTHER` needs a note. The customer sees the reason's message (and the note only for `OTHER`). Returns 200 with `status: "REJECTED"`; 422 `INVALID_INSPECTION` for a bad reason or missing note.

## Photo

Returns the image with its content type, `Cache-Control: no-store` and `X-Content-Type-Options: nosniff`. 404 `PHOTO_NOT_FOUND` if the photo isn't on that return. Browsers can't add a bearer token to `<img src>`, so fetch it with the token and show it from a blob URL.

## Today

```json
{ "date": "2026-11-20", "inspectedToday": 27, "inspectedByYouToday": 9 }
```

Counts inspections and warehouse rejections since midnight India time.
