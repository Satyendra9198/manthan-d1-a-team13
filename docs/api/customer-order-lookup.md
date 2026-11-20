# Customer order lookup

Figma: **Customer – Wizard, step 1 "Start a return"** (the response also feeds step 2 "Which items are you returning?").

`POST /api/v1/customer/orders/lookup`

POST instead of GET keeps the email out of URLs, browser history and access logs.

## Request

```json
{ "orderNumber": "RR-10482", "email": "aditi.sharma@example.com" }
```

| Field | Rules |
|---|---|
| `orderNumber` | Required, max 32 characters. Case and surrounding spaces are ignored. |
| `email` | Required, valid email, max 254 characters. Case is ignored. |

## 200 OK

```json
{
  "orderId": "11111111-1111-1111-1111-111111111111",
  "orderNumber": "RR-10482",
  "placedAt": "2026-10-31T10:52:21Z",
  "returnableItemCount": 2,
  "items": [
    {
      "itemId": "11111111-0000-0000-0000-000000000001",
      "sku": "APP-LINEN-M-SAND",
      "productName": "Linen shirt",
      "variant": "M / Sand",
      "category": "APPAREL",
      "unitPricePaise": 189900,
      "quantity": 1,
      "returnableQuantity": 1,
      "deliveredAt": "2026-11-15T10:52:21Z",
      "returnable": true,
      "eligibilityReason": "ELIGIBLE",
      "eligibilityMessage": "You can return this item until 15 Dec 2026.",
      "lastReturnDate": "2026-12-15"
    }
  ]
}
```

- Money is in **paise** (`189900` = ₹1,899.00).
- `returnable` drives the "Returnable" / "Not returnable" tag. Show `eligibilityMessage` under the item.
- `eligibilityReason` values: `ELIGIBLE`, `NON_RETURNABLE_CATEGORY`, `NOT_DELIVERED`, `ALREADY_RETURNED`, `RETURN_WINDOW_EXPIRED`.
- `lastReturnDate` is a calendar date in India time (assumption A-02). It is `null` for non-returnable and undelivered items.
- If `returnableItemCount` is 0, disable "Continue".

## Errors

All errors use the shared `ErrorResponse` shape.

| Status | `code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | A field is missing or malformed. `details` lists each problem. |
| 404 | `ORDER_NOT_FOUND` | Unknown order number **or** the email doesn't match. Both cases are deliberately identical, so the API never confirms that an order number exists. Show `message` in the red box. |

## Demo data (`dev` profile)

| Order | Email | Scenario |
|---|---|---|
| `RR-10482` | `aditi.sharma@example.com` | Two returnable items, one with an expired electronics window, one innerwear |
| `RR-10577` | `rahul.verma@example.com` | Not delivered yet, so nothing is returnable |

Run with `SPRING_PROFILES_ACTIVE=dev` to load it.
