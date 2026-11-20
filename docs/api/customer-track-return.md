# Customer track return

Figma: **Customer – Return** pages: "Return requested", in transit, refunded ("₹949.50 refunded … Why ₹949.50 and not ₹1,899?") and rejected ("We couldn't accept this return"). Also the "Already started a return? Track it" link on step 1.

`POST /api/v1/customer/returns/track` (public)

```json
{ "rmaNumber": "RMA-DEMO-00007", "email": "neha.gupta@example.com" }
```

Case and surrounding spaces are ignored. POST keeps the email out of URLs and logs.

## 200 OK

```json
{
  "rmaNumber": "RMA-DEMO-00007",
  "orderNumber": "RR-10760",
  "status": "REFUNDED",
  "statusMessage": "₹949.50 refunded to your original payment method.",
  "refundMethod": "ORIGINAL_PAYMENT",
  "createdAt": "2026-11-10T12:23:14Z",
  "estimatedRefundPaise": 189900,
  "timeline": [
    { "step": "REQUESTED",  "state": "COMPLETED", "at": "2026-11-10T12:23:14Z" },
    { "step": "APPROVED",   "state": "COMPLETED", "at": "2026-11-10T12:23:14Z" },
    { "step": "IN_TRANSIT", "state": "COMPLETED", "at": "2026-11-12T12:23:14Z" },
    { "step": "RECEIVED",   "state": "COMPLETED", "at": "2026-11-15T12:23:14Z" },
    { "step": "INSPECTED",  "state": "COMPLETED", "at": "2026-11-17T12:23:14Z" },
    { "step": "REFUNDED",   "state": "COMPLETED", "at": "2026-11-17T12:23:14Z" }
  ],
  "items": [
    { "itemId": "88888888-0000-0000-0000-000000000001", "productName": "Linen shirt",
      "variant": "L / White", "quantity": 1, "reason": "TOO_SMALL", "lineTotalPaise": 189900,
      "grade": "C", "acceptedQuantity": 1, "refundPaise": 94950 }
  ],
  "label": null,
  "refund": {
    "amountPaise": 94950, "requestedAmountPaise": 189900, "method": "ORIGINAL_PAYMENT",
    "status": "REFUNDED", "refundedAt": "2026-11-17T12:23:14Z",
    "explanation": ["Linen shirt: graded C at inspection (visible wear or damage), so it is refunded at 50%."]
  },
  "rejection": null
}
```

| Section | Present when |
|---|---|
| `timeline` | Always. `COMPLETED` steps have a time; the `CURRENT` step is where the return is now; `UPCOMING` steps have `at: null`. A step that was skipped (no in-transit scan) is left out. Rejected or cancelled returns end with that step. |
| `label` | Status `APPROVED` or `IN_TRANSIT`: `qrCodeValue` (draw it as a QR code), `dropOffBy` (14 days after approval, A-13), `shipTo`, `instructions`. |
| `refund` | From inspection on, unless rejected. `status` is `PROCESSING` until paid, then `REFUNDED`. `explanation` is empty when refunded in full. |
| `rejection` | Status `REJECTED`: `reason`, customer-facing `message`, and `note` only for reason `OTHER`. |

`statusMessage` is ready-made header text for each status.

## Errors

| Status | `code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Missing or malformed field. |
| 404 | `RETURN_NOT_FOUND` | Unknown RMA **or** wrong email (same answer, so return numbers can't be probed). |
