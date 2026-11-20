# Customer submit return

Figma: **Customer – Wizard, step 3 "Why are you returning it?"** (reason, comment, photos), **step 4 "How would you like your refund?"** and the **review step "Check your return"**. Called on **Submit return**; the response feeds the **"Return requested"** screen.

`POST /api/v1/customer/returns` with `multipart/form-data`

Steps 3 and 4 only collect answers in the browser. This one call validates everything and creates the return, so a customer who leaves halfway never leaves a half-made return behind.

## Parts

| Part name | Content type | Count | Contents |
|---|---|---|---|
| `return` | `application/json` (**required**) | exactly 1 | The JSON below |
| `photos.<itemId>` | any image | 0–5 per item | One file per photo, for the item with that `itemId` |

The `return` part **must** be sent with type `application/json`, otherwise the server answers 415:

```js
const form = new FormData();
form.append("return", new Blob([JSON.stringify(body)], { type: "application/json" }));
for (const [itemId, files] of Object.entries(photosByItem)) {
  for (const file of files) form.append(`photos.${itemId}`, file);
}
await fetch("/api/v1/customer/returns", { method: "POST", body: form }); // don't set Content-Type yourself
```

### `return` JSON

```json
{
  "orderNumber": "RR-10482",
  "email": "aditi.sharma@example.com",
  "items": [
    { "itemId": "11111111-0000-0000-0000-000000000001", "quantity": 1,
      "reason": "DAMAGED_ON_ARRIVAL", "comment": "Seam torn at the shoulder." }
  ],
  "refundMethod": "ORIGINAL_PAYMENT",
  "detailsConfirmed": true
}
```

| Field | Rules |
|---|---|
| `orderNumber`, `email` | Same as the [order lookup](customer-order-lookup.md). |
| `items` | 1–50 entries, same item and quantity rules as the [item selection](customer-item-selection.md). |
| `items[].reason` | `TOO_BIG`, `TOO_SMALL`, `DAMAGED_ON_ARRIVAL`, `NOT_AS_DESCRIBED`, `CHANGED_MY_MIND` |
| `items[].comment` | Optional "Tell us more", max 500 characters. |
| `refundMethod` | `ORIGINAL_PAYMENT` or `STORE_CREDIT` |
| `detailsConfirmed` | Must be `true` (the "The details and photos are accurate" checkbox). |

### Photo rules (assumption A-19)

- JPEG, PNG or WebP only. The type is checked from the file's bytes, not its name or declared type.
- At most **5 photos per item**, each **5 MB or smaller**; the whole request 40 MB or smaller.
- `DAMAGED_ON_ARRIVAL` and `NOT_AS_DESCRIBED` need **at least one photo**.
- Photos are only allowed for items on the return.

## 201 Created

`Location: /api/v1/customer/returns/RMA-2026-00731`

```json
{
  "returnId": "6a1f…",
  "rmaNumber": "RMA-2026-00731",
  "status": "APPROVED",
  "labelReady": true,
  "refundMethod": "ORIGINAL_PAYMENT",
  "estimatedRefundPaise": 189900,
  "createdAt": "2026-11-20T06:30:00Z",
  "items": [
    { "itemId": "11111111-0000-0000-0000-000000000001", "productName": "Linen shirt",
      "quantity": 1, "reason": "DAMAGED_ON_ARRIVAL", "lineTotalPaise": 189900, "photoCount": 1 }
  ]
}
```

- `status` is `APPROVED` with `labelReady: true` for most customers ("Approved · label ready").
- A customer with more than 3 returns in 30 days gets `status: "REQUESTED"` and `labelReady: false` (assumptions A-10, A-11). Show "We're reviewing your request". The response never says the customer was flagged.

## Errors

| Status | `code` | When |
|---|---|---|
| 400 | `MISSING_PART` | No `return` part. |
| 400 | `VALIDATION_FAILED` | A field in `return` is missing or invalid, or `detailsConfirmed` is not `true`. |
| 400 | `MALFORMED_REQUEST` | `return` isn't valid JSON, or has an unknown `reason` / `refundMethod`. |
| 400 | `INVALID_REQUEST` | A file part isn't named `photos.<valid itemId>`. |
| 404 | `ORDER_NOT_FOUND` | The order number and email don't match. |
| 413 | `UPLOAD_TOO_LARGE` | A file is over 5 MB or the request is over 40 MB. |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | The request isn't multipart, or the `return` part isn't `application/json`. |
| 422 | `SELECTION_NOT_RETURNABLE` | An item can't be returned as chosen (same reasons as item selection). |
| 422 | `INVALID_PHOTOS` | A photo breaks the rules above. `details` lists each problem as `<itemId>: <message>`. |

Nothing is saved when any error is returned.

## Concurrency

The order is locked while the return is created. If the same submission arrives twice (e.g. a double click), the second waits, then fails with 422 `SELECTION_NOT_RETURNABLE` because the units are already on the first return.
