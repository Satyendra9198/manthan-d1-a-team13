# ReturnRight API

Base URL locally: `http://localhost:8080`. Every error uses the shared `ErrorResponse` body: `code`, `message`, `details`, `timestamp`.

| # | Method | Path | Figma screen | Doc |
|---|---|---|---|---|
| 1 | `POST` | `/api/v1/customer/orders/lookup` | Wizard step 1 "Start a return" | [customer-order-lookup.md](customer-order-lookup.md) |
| 2 | `POST` | `/api/v1/customer/orders/selection/validate` | Wizard step 2 "Which items are you returning?" | [customer-item-selection.md](customer-item-selection.md) |
| 3 | `POST` (multipart) | `/api/v1/customer/returns` | Steps 3–4 and review "Submit return" → "Return requested" | [customer-submit-return.md](customer-submit-return.md) |
| – | `GET` | `/actuator/health` | – | Spring Boot health check |

Ready-to-run requests for every scenario: [returnright.http](returnright.http) (IntelliJ HTTP Client or the VS Code REST Client extension).

## Running locally with demo data

From `backend/`, with PostgreSQL running and a `returnright` database created:

```
mvn spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.arguments=--spring.main.lazy-initialization=true
```

- The `dev` profile loads [R__seed_demo_orders.sql](../../backend/src/main/resources/db/seed/R__seed_demo_orders.sql). Flyway re-runs it whenever the file changes, which resets the demo orders and deletes any returns created against them.
- `--spring.main.lazy-initialization=true` is needed until `PaymentGatewayClient` has an implementation; without it the refund service stops the app from starting.
- Database settings come from `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (see `backend/.env.example`). Photos are written to `PHOTO_DIRECTORY` (default `backend/data/photos`).

## Demo orders

| Order | Email | What it tests |
|---|---|---|
| `RR-10482` | `aditi.sharma@example.com` | Figma basket: shirt and mugs returnable, earbuds' 10-day window closed, briefs non-returnable |
| `RR-10577` | `rahul.verma@example.com` | Not delivered yet: nothing returnable |
| `RR-10601` | `priya.nair@example.com` | Happy path: 3 T-shirts (return some), 2 sunscreens, smartwatch inside its 10-day window, perishable mangoes |
| `RR-10655` | `vikram.singh@example.com` | Earlier returns: 1 of 2 shoes left, lamp already returned, speaker returnable again because its return was cancelled |
| `RR-10702` | `meera.iyer@example.com` | Already 3 returns in 30 days: returning the linen trousers is the 4th, so the return stays `REQUESTED` for review |
| `RR-10388` | `arjun.mehta@example.com` | Delivered 45 days ago: every window closed |

### Resetting the demo data

Submitting returns uses up the demo items. To put everything back, run this in pgAdmin or `psql`, then restart the app with the `dev` profile:

```sql
DELETE FROM flyway_schema_history WHERE script = 'R__seed_demo_orders.sql';
```

Item ids follow the order: `<order prefix>-0000-0000-0000-00000000000<line>`, e.g. the first T-shirt line of `RR-10601` is `33333333-0000-0000-0000-000000000001`. The lookup response lists them all.
