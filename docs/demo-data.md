# Demo data

Everything here is **synthetic**. It is created by the seeder (Part 2 §16) with a fixed random seed (`20261120`),
so two runs with the same `--seed.as-of` date produce the same data.

```
docker compose run --rm app --spring.profiles.active=seed --seed.as-of=YYYY-MM-DD --seed.reset=true
```

Use today's date (IST) for `--seed.as-of`: delivery dates are relative to it, so the day-30 / day-31 cases only
line up on that date. Re-seed before a demo, and again after running the API scenario or Playwright, because those
consume golden cases.

Volume: 505 customers, 40 products in 6 categories, ~2,030 orders, ~230 returns at every stage, and a ledger that
reconciles (R1–R5 = 0, printed at the end of the seed run).

## Logins

| Who | Username / login | Password |
|---|---|---|
| Warehouse agents | `warehouse1`, `warehouse2` | `ReturnRight#2026` |
| Finance reviewers | `finance1`, `finance2` | `ReturnRight#2026` |
| Operations manager | `manager1` | `ReturnRight#2026` |
| Admin (may act for any staff role) | `admin` | `ReturnRight#2026` |
| Customers | order number + the email on the order (no password) | – |

The staff password comes from `RR_SEED_STAFF_PASSWORD` (dev default above).

## Golden cases

Customer for the `RR-EDGE-*` orders: **`edge.customer@example.com`** (Asha Verma).

| Order | Scenario | Expected |
|---|---|---|
| `RR-EDGE-001` | Apparel delivered exactly 30 days ago | ELIGIBLE, "last day" message [A-02] |
| `RR-EDGE-002` | Apparel delivered 31 days ago | `RETURN_WINDOW_EXPIRED` |
| `RR-EDGE-003` | Electronics, 10 days ago | ELIGIBLE |
| `RR-EDGE-004` | Electronics, 11 days ago | `RETURN_WINDOW_EXPIRED` |
| `RR-EDGE-005` | Apparel delivered 30 days ago at 00:10 IST (previous UTC date) | ELIGIBLE (timezone trap) |
| `RR-EDGE-006` | Innerwear, 2 days ago | `CATEGORY_NOT_RETURNABLE` |
| `RR-EDGE-007` | Perishables, 1 day ago | `CATEGORY_NOT_RETURNABLE` |
| `RR-EDGE-008` | Mixed basket: jeans + innerwear | Jeans selectable, innerwear disabled with the reason |
| `RR-EDGE-009` | Laptop (> ₹10,000) | After inspection → `PENDING_FINANCE` |
| `RR-EDGE-010` | Running shoes ₹11,999, graded C → ₹6,000 | Auto-refund: the threshold applies after grading [A-12] |
| `RR-EDGE-011` | Two items at ₹6,000 each | Return total ₹12,000 → finance, although each item is below the threshold |
| `RR-EDGE-012` | One item at exactly ₹10,000.00 | Auto-refund (the rule is *strictly* greater) |
| `RR-EDGE-013` | Line quantity 3, one unit already returned | 2 units selectable |
| `RR-EDGE-014` | Order shipped, not delivered | `ORDER_NOT_DELIVERED` |
| `RR-EDGE-015` | Cancelled order | `ORDER_CANCELLED` |
| `RR-EDGE-016` | Payment reference ends in `-DECLINE` | Gateway declines → `REFUND_FAILED` → finance retries as store credit |
| `RR-EDGE-017` | Delivered today (day 0) | ELIGIBLE |

### Flag rule [A-14]

| Customer | Order | Scenario | Expected |
|---|---|---|---|
| `flag.near@example.com` | `RR-FLAG-104` | 3 returns in the last 30 days | The next submit flags the customer and routes the return to finance |
| `flag.cancelled@example.com` | `RR-FLAG-204` | 2 returns + 1 cancelled in 30 days | Cancelled returns count, so the next submit flags |
| `flag.already@example.com` | `RR-FLAG-301` | Already flagged, ₹799 item | Still goes to finance |

### Persona walkthrough (`demo.customer@example.com`, Neha Kapoor)

| Order | Seeded state | Use it to show |
|---|---|---|
| `RR-DEMO-101` | `PENDING_FINANCE`, tablet > ₹10,000 | Finance approval; two reviewers racing (one wins, one gets 409) |
| `RR-DEMO-102` | `RECEIVED`, 2 items | Warehouse inspection (grade per unit, complete → auto-route) |
| `RR-DEMO-103` | `REFUND_FAILED` (gateway declined the card) | Finance retry as store credit (T17) |
| `RR-DEMO-104` | `PENDING_FINANCE`, ₹24,999 graded C → ₹12,499.50 | Threshold applied after grading; still > ₹10,000 |
| `RR-DEMO-105` | `IN_TRANSIT`, 2 shirts | Scan at the warehouse station |

This customer has 5 returns in 30 days, so they are **flagged**; their new returns go to finance review.

> Part 2 §16.3 mentions `asha.demo@example.com` + `RR-2026-000001` for the persona login; the seeder uses
> `demo.customer@example.com` + the `RR-DEMO-1xx` orders above instead.
