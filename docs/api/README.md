# ReturnRight API

Base URL locally: `http://localhost:8080`. Every error uses the shared `ErrorResponse` body: `code`, `message`, `details`, `timestamp`. Money is always in **paise** (`189900` = ₹1,899).

## Customer (public)

| Method | Path | Figma screen | Doc |
|---|---|---|---|
| `POST` | `/api/v1/customer/orders/lookup` | Step 1 "Start a return" | [customer-order-lookup.md](customer-order-lookup.md) |
| `POST` | `/api/v1/customer/orders/selection/validate` | Step 2 "Which items are you returning?" | [customer-item-selection.md](customer-item-selection.md) |
| `POST` (multipart) | `/api/v1/customer/returns` | Steps 3–4 and review "Submit return" | [customer-submit-return.md](customer-submit-return.md) |
| `POST` | `/api/v1/customer/returns/track` | "Return requested", tracking, refunded and rejected pages | [customer-track-return.md](customer-track-return.md) |

## Staff (bearer token)

| Method | Path | Role | Figma screen | Doc |
|---|---|---|---|---|
| `POST` | `/api/v1/auth/login` | public | Staff – Login | [staff-auth.md](staff-auth.md) |
| `GET` | `/api/v1/auth/me` | any staff | – | [staff-auth.md](staff-auth.md) |
| `GET` | `/api/v1/warehouse/returns/queue` | warehouse | Agent – Inspection | [warehouse.md](warehouse.md) |
| `GET` | `/api/v1/warehouse/returns/{rma}` | warehouse | "Find return" | [warehouse.md](warehouse.md) |
| `POST` | `/api/v1/warehouse/returns/{rma}/receive` | warehouse | scan in | [warehouse.md](warehouse.md) |
| `POST` | `/api/v1/warehouse/returns/{rma}/inspection` | warehouse | grade keys, "Partially accept" | [warehouse.md](warehouse.md) |
| `POST` | `/api/v1/warehouse/returns/{rma}/reject` | warehouse | "Reject return" dialog | [warehouse.md](warehouse.md) |
| `GET` | `/api/v1/warehouse/returns/{rma}/photos/{photoId}` | warehouse | customer photos | [warehouse.md](warehouse.md) |
| `GET` | `/api/v1/warehouse/inspections/today` | warehouse | "Today: 27 inspected" | [warehouse.md](warehouse.md) |
| `GET` | `/api/v1/finance/returns/pending` | finance | not in Figma export | [reviews.md](reviews.md) |
| `POST` | `/api/v1/finance/returns/{rma}/approve` | finance | not in Figma export | [reviews.md](reviews.md) |
| `POST` | `/api/v1/finance/returns/{rma}/decline` | finance | not in Figma export | [reviews.md](reviews.md) |
| `GET` | `/api/v1/operations/returns/flagged` | operations | not in Figma export | [reviews.md](reviews.md) |
| `POST` | `/api/v1/operations/returns/{rma}/approve` | operations | not in Figma export | [reviews.md](reviews.md) |
| `POST` | `/api/v1/operations/returns/{rma}/decline` | operations | not in Figma export | [reviews.md](reviews.md) |
| `GET` | `/actuator/health` | public | – | Spring Boot health check |

Ready-to-run requests for every scenario: [returnright.http](returnright.http) (IntelliJ HTTP Client or the VS Code REST Client extension).

## Running locally with demo data

From `backend/`, with PostgreSQL running and a `returnright` database created:

```
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

- The `dev` profile loads [R__seed_demo_orders.sql](../../backend/src/main/resources/db/seed/R__seed_demo_orders.sql) and a dev-only token signing key. Flyway re-runs the seed whenever the file changes, which resets the demo orders and deletes any returns created against them.
- Payouts go to an in-process mock gateway that always succeeds.
- Database settings come from `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`; outside `dev`, `JWT_SECRET` is required (see `backend/.env.example`). Photos are written to `PHOTO_DIRECTORY` (default `backend/data/photos`).

## Demo data

Staff (password `Password@123` for all): `riya.warehouse@`, `arun.warehouse@`, `kavya.finance@`, `manoj.ops@` — all `@returnright.example`.

| Order | Email | Return | What it tests |
|---|---|---|---|
| `RR-10482` | `aditi.sharma@example.com` | – | Figma basket: shirt and mugs returnable, earbuds' window closed, briefs non-returnable |
| `RR-10577` | `rahul.verma@example.com` | – | Not delivered yet |
| `RR-10601` | `priya.nair@example.com` | – | Happy path: T-shirts ×3, sunscreen ×2, smartwatch inside its window, perishable mangoes |
| `RR-10655` | `vikram.singh@example.com` | `RMA-DEMO-00001/2` | 1 of 2 shoes left, lamp already returned, speaker returnable again (its return was cancelled) |
| `RR-10702` | `meera.iyer@example.com` | `RMA-DEMO-00003/4/5` | Returning the linen trousers is her 4th request in 30 days → held for review |
| `RR-10388` | `arjun.mehta@example.com` | – | Delivered 45 days ago: every window closed |
| `RR-10750` | `karan.kapoor@example.com` | `RMA-DEMO-00006` | **Received**, waiting for inspection |
| `RR-10760` | `neha.gupta@example.com` | `RMA-DEMO-00007` | **Refunded** ₹949.50 after grade C |
| `RR-10770` | `sanjay.rao@example.com` | `RMA-DEMO-00008` | **Rejected**: used or worn |
| `RR-10780` | `divya.menon@example.com` | `RMA-DEMO-00009` | Laptop ₹54,999 **waiting for finance** |
| `RR-10703` | `meera.iyer@example.com` | `RMA-DEMO-00010` | **Flagged**, waiting for operations |
| `RR-10790` | `farah.khan@example.com` | `RMA-DEMO-00011` | **In transit**, label shown, ready to scan in |

Track any return with its RMA and the email in the same row.

### Resetting the demo data

Submitting, inspecting and approving use up the demo data. To put everything back, run this in pgAdmin or `psql`, then restart the app with the `dev` profile:

```sql
DELETE FROM flyway_schema_history WHERE script = 'R__seed_demo_orders.sql';
```

Item ids follow the order: `<order prefix>-0000-0000-0000-00000000000<line>`, e.g. the first T-shirt line of `RR-10601` is `33333333-0000-0000-0000-000000000001`. The lookup response lists them all.
