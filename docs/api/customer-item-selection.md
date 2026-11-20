# Customer item selection

Figma: **Customer – Wizard, step 2 "Which items are you returning?"**, called when the customer clicks **Continue**.

`POST /api/v1/customer/orders/selection/validate`

This call checks the selection and returns the summary for the footer ("2 items selected · ₹3,148") and the next steps. **Nothing is saved.** The return is created when the customer submits the review step.

The customer has no login, so the order number and email from step 1 are sent again to prove ownership.

## Request

```json
{
  "orderNumber": "RR-10482",
  "email": "aditi.sharma@example.com",
  "items": [
    { "itemId": "11111111-0000-0000-0000-000000000001", "quantity": 1 },
    { "itemId": "11111111-0000-0000-0000-000000000002", "quantity": 1 }
  ]
}
```

| Field | Rules |
|---|---|
| `orderNumber`, `email` | Same as the [order lookup](customer-order-lookup.md). |
| `items` | 1 to 50 entries. |
| `items[].itemId` | An `itemId` from the order lookup response. |
| `items[].quantity` | At least 1, and no more than the item's `returnableQuantity`. Part of a line may be returned. |

## 200 OK

```json
{
  "orderId": "11111111-1111-1111-1111-111111111111",
  "orderNumber": "RR-10482",
  "selectedItemCount": 2,
  "selectedUnitCount": 2,
  "estimatedRefundPaise": 314800,
  "items": [
    {
      "itemId": "11111111-0000-0000-0000-000000000001",
      "productName": "Linen shirt",
      "variant": "M / Sand",
      "category": "APPAREL",
      "unitPricePaise": 189900,
      "quantity": 1,
      "lineTotalPaise": 189900,
      "lastReturnDate": "2026-12-15"
    }
  ]
}
```

- `estimatedRefundPaise` is the sum of `lineTotalPaise`. The final refund depends on inspection (grade C is refunded at 50%), which is why it's an estimate.
- Keep `items` for steps 3 and 4 (reason, refund method) and the review screen.

## Errors

| Status | `code` | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | Empty `items`, missing fields, or a quantity below 1. `details` names each field, e.g. `items[0].quantity: must be greater than 0`. |
| 400 | `MALFORMED_REQUEST` | The body isn't valid JSON or has the wrong shape (e.g. `itemId` isn't a UUID). |
| 404 | `ORDER_NOT_FOUND` | The order number and email don't match. |
| 422 | `SELECTION_NOT_RETURNABLE` | One or more selected items can't be returned as chosen. `details` has one entry per problem, formatted `<itemId>: <message>`, so each message can be shown under its item. |

All problems are reported together. Reasons for a 422:

- the item isn't part of this order
- the item was selected more than once
- the item's category is non-returnable (innerwear, perishables)
- the item hasn't been delivered yet
- the return window has closed
- the quantity is more than can still be returned
