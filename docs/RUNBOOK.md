# ReturnRight: Runbook (operate and demo)

For anyone running the stack or presenting it live. Design background: [`LLD.md`](LLD.md). Golden data: [`demo-data.md`](demo-data.md). Status and known traps: [`HANDOFF.md`](HANDOFF.md).
Commands are PowerShell, run from the repository root unless stated.

## 1. Secrets rule

No password, token or key appears in this repository's docs. Every secret lives in the git-ignored `.env` at the repo root. Where this runbook says "password = `NAME`", open `.env` and read the value of that variable.

| Variable in `.env` | Used for |
|---|---|
| `RR_SEED_STAFF_PASSWORD` | Password of every staff account (`warehouse1`, `finance1`, ...) |
| `GRAFANA_PASSWORD` | Grafana user `admin` |
| `PGADMIN_PASSWORD` | pgAdmin user `admin@returnright.dev` |
| `RR_OWNER_DB_PASSWORD`, `RR_APP_DB_PASSWORD`, `RR_READONLY_DB_PASSWORD` | PostgreSQL roles `rr_owner`, `rr_app`, `rr_readonly` (pgAdmin asks for the first and third) |
| `GATEWAY_ADMIN_TOKEN` | Gateway fault API (read automatically by `chaos.ps1`) |
| `RR_REDIS_PASSWORD`, `RR_JWT_SECRET` | Redis and token signing; never typed by a human |

`.env` is created by `infra/scripts/init-env.ps1` (random values; refuses to overwrite an existing file) or by `cp .env.example .env` (dev placeholders). It is git-ignored; never commit it and never paste its values into chat, tickets or slides.

## 2. Prerequisites

- Docker Desktop with WSL 2 and Git.
- Only for running tests outside Docker: JDK 21 (Temurin), Node 26.13.1, Python 3.13. No Maven install is needed (each Java service has `mvnw`).
- About 8 GB for the Docker VM. The core stack fits; the `streaming` profile adds about 1 GB.
- Free host ports: 8080, 8000, 8100, 5434, 6379, 5050, 9090, 3000 (and 8025 for Mailpit).

## 3. First-time setup

```powershell
git clone <repo-url>; cd manthan-d1-a-team13
git checkout feature/returnright-mvp
infra/scripts/init-env.ps1                    # creates .env (or: cp .env.example .env)
docker compose up -d --build --wait           # first build takes a few minutes
docker compose run --rm app --spring.profiles.active=seed --seed.as-of=2026-11-20 --seed.reset=true
```

Use **today's date** (IST) for `--seed.as-of`: delivery dates are relative to it, so the day-30 and day-31 cases only line up on that date [A-02]. The seed run prints reconciliation R1 to R5 at the end; all must be 0.

### Start, stop, reset

| Goal | Command |
|---|---|
| Start | `docker compose up -d --wait` |
| Stop, keep data | `docker compose down` |
| Wipe everything (database, gateway store) | `docker compose down -v` |
| Needed when | You change any `RR_*_DB_PASSWORD` after the first start (roles are created only once), or the DB is in a bad state |
| Re-seed (between demo runs) | the `docker compose run --rm app ... --seed.reset=true` command above |
| Rebuild one service | `docker compose up -d --build app` |
| Include Kafka, notifier, Mailpit | `$env:RR_KAFKA_ENABLED='true'; docker compose --profile streaming up -d --build` |

### Health checks

| Check | URL | Healthy answer |
|---|---|---|
| API liveness | http://localhost:8000/healthz | 200 |
| API readiness | http://localhost:8000/readyz | 200 (database and dependencies reachable) |
| Gateway | http://localhost:8100/healthz | 200 |
| All containers | `docker compose ps` | `healthy` for db, redis, gateway, app |

### URLs

| What | URL | Login |
|---|---|---|
| App (customer and staff) | http://localhost:8080 | see section 4 |
| API and Swagger UI | http://localhost:8000/swagger-ui.html | none |
| Grafana | http://localhost:3000 | `admin` / `GRAFANA_PASSWORD` (anonymous viewing is also on) |
| Prometheus (and `/alerts`) | http://localhost:9090 | none |
| pgAdmin | http://localhost:5050 | `admin@returnright.dev` / `PGADMIN_PASSWORD` |
| Mock gateway | http://localhost:8100/healthz | admin API needs `GATEWAY_ADMIN_TOKEN` |
| Mailpit (streaming profile) | http://localhost:8025 | none |
| PostgreSQL from the host | `localhost:5434` | role `rr_readonly` or `rr_owner` |

## 4. Users for manual end-to-end testing

### Staff (sign in with username and password at http://localhost:8080)

Password for all staff accounts = `RR_SEED_STAFF_PASSWORD` from `.env`.

| Username | Role | Display name | Email |
|---|---|---|---|
| `warehouse1` | Warehouse agent | Arjun Patil | `warehouse1@returnright.dev` |
| `warehouse2` | Warehouse agent | Meera Iyer | `warehouse2@returnright.dev` |
| `finance1` | Finance reviewer | Kavita Rao | `finance1@returnright.dev` |
| `finance2` | Finance reviewer | Sandeep Joshi | `finance2@returnright.dev` |
| `manager1` | Operations manager | Farhan Qureshi | `manager1@returnright.dev` |
| `admin` | Admin (may act for any staff role, not for customers) | Platform Admin | `admin@returnright.dev` |

Staff accounts lock for 15 minutes after 5 failed logins.

### Customers (no password: order number plus the email on the order)

| Customer | Email | Orders | What it demonstrates |
|---|---|---|---|
| Asha Verma | `edge.customer@example.com` | `RR-EDGE-001` to `RR-EDGE-017` | Eligibility boundaries and routing rules (table below) |
| Rohan Mehta | `flag.near@example.com` | `RR-FLAG-101` to `104` | 3 returns in 30 days; the next submit flags him [A-14] |
| Priya Nair | `flag.cancelled@example.com` | `RR-FLAG-201` to `204` | 2 returns plus 1 cancelled; cancelled counts, so the next submit flags |
| Vikram Singh | `flag.already@example.com` | `RR-FLAG-301` | Already flagged, a ₹799 item still goes to finance |
| Neha Kapoor | `demo.customer@example.com` | `RR-DEMO-101` to `105` | Seeded returns in every stage for the staff walkthrough; she is flagged (5 returns in 30 days) |

Orders used in this runbook:

| Order | Scenario | Expected |
|---|---|---|
| `RR-EDGE-001` | Apparel delivered exactly 30 days ago | Eligible, "last day" message |
| `RR-EDGE-002` | Apparel delivered 31 days ago | `RETURN_WINDOW_EXPIRED` |
| `RR-EDGE-003` / `004` | Electronics 10 / 11 days ago | Eligible / expired |
| `RR-EDGE-008` | Mixed basket: jeans plus innerwear | Jeans selectable, innerwear disabled with the reason |
| `RR-EDGE-009` | Laptop above ₹10,000 | After inspection goes to finance |
| `RR-EDGE-010` | Running shoes ₹11,999, graded C gives ₹6,000 | Auto refund (threshold applies after grading) [A-12] |
| `RR-EDGE-012` | One item at exactly ₹10,000.00 | Auto refund (rule is strictly greater) |
| `RR-EDGE-016` | Payment reference ends `-DECLINE` | Gateway declines, `REFUND_FAILED`, finance retries as store credit |
| `RR-DEMO-101` | `PENDING_FINANCE`, tablet above ₹10,000 | Finance approval, reviewer race |
| `RR-DEMO-102` | `RECEIVED`, 2 items | Inspection |
| `RR-DEMO-103` | `REFUND_FAILED` (card declined) | Retry as store credit (T17) |
| `RR-DEMO-104` | `PENDING_FINANCE`, ₹24,999 graded C gives ₹12,499.50 | Still above ₹10,000 after grading |
| `RR-DEMO-105` | `IN_TRANSIT`, 2 shirts | Scan at the warehouse station |
| `RR-FLAG-104` | Rohan Mehta, bed set | Submit triggers the flag |

Full list: [`demo-data.md`](demo-data.md).

### Admin tools

| Tool | Login |
|---|---|
| pgAdmin | `admin@returnright.dev` / `PGADMIN_PASSWORD`. Two servers are preconfigured: "read-only (rr_readonly)" and "owner (rr_owner)"; pgAdmin asks for the DB role password (`RR_READONLY_DB_PASSWORD` or `RR_OWNER_DB_PASSWORD`) on first connect |
| Grafana | `admin` / `GRAFANA_PASSWORD` |

## 5. Demo script (about 15 minutes)

Preparation (before the audience arrives): fresh `.env`, `docker compose up -d --build --wait`, seed with today's date, `infra/scripts/chaos.ps1 healthy`. Open these tabs: app (customer, normal window), app (staff, private window 1), app (staff, private window 2 for the reviewer race), Grafana, pgAdmin. Staff sessions are per browser profile, so use separate private windows or browsers for two staff users. Optional: start the `streaming` profile first and open Mailpit.

### Act 1: Customer (about 3 min)

| # | Who | Do | Expected | Proves |
|---|---|---|---|---|
| 1 | Customer | At http://localhost:8080 enter order `RR-EDGE-001` and `edge.customer@example.com`, open the order | Unit shown as eligible with a "last day" message and days remaining | Day 30 inclusive in IST [A-02] |
| 2 | Customer | Open `RR-EDGE-002` | Unit not selectable, reason says the return window expired | Day 31 rejected; same engine as the DB trigger [BR-9] |
| 3 | Customer | Open `RR-EDGE-008` | Jeans selectable, innerwear disabled with a clear reason | Mixed basket: only eligible units can be chosen [A-06] |
| 4 | Customer | Open `RR-EDGE-010` (running shoes), start a return, pick a reason, keep refund method "original payment", confirm | Return created, status Approved, timeline shows Requested then Approved | T1 and T2 in one transaction; auto approval [A-08] |
| 5 | Customer | Open the label ("View label") | Printable label with QR, RMA number and, under the QR, the QR token text. Click "Print label" to show the print layout | QR label issued, valid 14 days [A-19] |

Keep the label page open. Copy the QR token (or note the RMA number).

### Act 2: Warehouse (about 3 min)

| # | Who | Do | Expected | Proves |
|---|---|---|---|---|
| 6 | `warehouse1` | Sign in, land on the scan station, paste the QR token (or type the RMA number) and press Enter | Success: return received | T4; scan accepts token or RMA |
| 7 | `warehouse1` | Paste the same token again | Harmless "already received" result, no error, no duplicate | Idempotent scan [F-06] |
| 8 | `warehouse1` | Open the return, choose Accept and grade C for the shoes | Preview shows refund of ₹6,000 (50 percent of ₹11,999, half rounds up) | Grade C rule [A-10], money in paise |
| 9 | `warehouse1` | Click Complete inspection, then "Complete and route" | Return moves straight to Refunded; no finance step | ROUTE picks T12 because ₹6,000 is not above ₹10,000: the threshold applies after grading [A-12]. Refund executed after commit, ledger posted, T15 |
| 10 | Customer | Refresh the return page | Timeline shows Refunded with the amount | Customer-facing timeline mapping |

If you need a second return to inspect, use `RR-DEMO-105` (shows `IN_TRANSIT`): sign in as `demo.customer@example.com` with order `RR-DEMO-105`, open its label for the token, scan as `warehouse1`. `RR-DEMO-102` is already `RECEIVED` and can be inspected directly.

### Act 3: Finance (about 4 min)

| # | Who | Do | Expected | Proves |
|---|---|---|---|---|
| 11 | `finance1` (window 1) and `finance2` (window 2) | Both open the finance queue and then the return for `RR-DEMO-101` | Both see PENDING_FINANCE, amount above ₹10,000, reason Amount (and Flagged: Neha is flagged) | T11 routing, risk badges |
| 12 | `finance1` then `finance2` | `finance1` clicks "Approve refund" and confirms. Then `finance2` (still on the old page) approves | `finance1`: refund processed, status Refunded. `finance2`: toast "This return was updated by someone else - reloaded" (HTTP 409) | Row lock plus `If-Match`; exactly one wins [F-04] |
| 13 | `finance1` | Open `RR-DEMO-104` | Grade C item, approved amount ₹12,499.50, still above ₹10,000 so it waits for finance | Threshold after grading; paise arithmetic |
| 14 | `finance1` | Open `RR-DEMO-103` (state `REFUND_FAILED`), click "Retry refund", choose "Switch to store credit", confirm | Refund succeeds instantly, return Refunded, no gateway call | T17; store credit is ledger-only [A-16] |

Leave `RR-DEMO-104` unapproved for Act 4.

### Act 4: Break the gateway (about 3 min)

| # | Who | Do | Expected | Proves |
|---|---|---|---|---|
| 15 | Presenter | `infra/scripts/chaos.ps1 lost-responses` | Gateway reports the active profile | Fault injection |
| 16 | `finance1` | Open `RR-DEMO-104`, click "Approve refund", confirm | Toast "Checking with payment gateway..." and a banner saying the outcome is not final; return stays `REFUND_PENDING`; the attempt shows `UNKNOWN` with a "Recheck with gateway" button | Timeout or lost response is `UNKNOWN`, never `FAILED`; no human retry button [ADR-001, F-03] |
| 17 | Presenter | Wait about 15 to 30 seconds without clicking anything | The page refreshes by itself and the return becomes Refunded | The reconciler asks the gateway with the same key and finishes the refund [F-03] |
| 18 | Presenter | `curl.exe -s -H "X-Admin-Token: <value of GATEWAY_ADMIN_TOKEN>" http://localhost:8100/admin/refunds` (read the token from `.env`; do not show it) or pgAdmin, or the manager reconciliation page | Exactly one gateway payout for that refund; reconciliation all green | Exactly-once payout |
| 19 | Presenter | `infra/scripts/chaos.ps1 healthy` | Back to normal | |

The `lost-responses` profile drops about half of the gateway responses (deterministic with `FAULT_SEED`). If step 16 settles immediately, the response was not lost on that call: repeat with another pending return (the seeded history contains more `PENDING_FINANCE` returns in the queue) until you see the `UNKNOWN` state.

Observability (optional, 1 min): run `infra/scripts/chaos.ps1 flaky`, approve or let a few refunds run, then in Grafana ("ReturnRight - Operations") watch the refund success rate and gateway panels; in Prometheus `/alerts` `RefundSuccessRateLow` goes pending then firing after about 2 minutes below 90 percent. Then `chaos.ps1 healthy`. This alert behaviour has not yet been observed end to end (`HANDOFF.md` item 4), so rehearse it first.

### Act 5: Manager and data (about 3 min)

| # | Who | Do | Expected | Proves |
|---|---|---|---|---|
| 20 | `manager1` | Open the dashboard | KPI tiles, return rate by category, top reasons, cycle time, daily trend | Analytics |
| 21 | Customer `flag.near@example.com` | Sign in with order `RR-FLAG-104`, return the bed set | Return created normally | A flag is review-only [A-13] |
| 22 | `manager1` | Open Flagged customers | Rohan Mehta now listed (4 returns in 30 days); mention the clear-flag action with a mandatory note [A-15] | Flag rule [A-14] |
| 23 | `warehouse1` | Scan the new return's token and complete inspection (grade A) | Return lands in the finance queue although the amount is small | Flagged customers always go to finance review [A-13, T11] |
| 24 | `manager1` | Audit log: search by the return id (copy from the URL) | Every transition with actor, role, from and to state, correlation id | Audit in the same transaction as each change |
| 25 | `manager1` | Reconciliation page | R1, R2, R4, R5 all with 0 mismatches (R3 not implemented) | Ledger agrees with refunds |
| 26 | Presenter | pgAdmin: connect to "owner (rr_owner)", browse `returns`, `refunds`, `refund_attempts`, `ledger_entries` | Real rows: one refund per return, key like `rr-<refund id>-1`, balanced entries | Data model |
| 27 | Presenter | In the query tool run `UPDATE ledger_entries SET amount_paise = amount_paise + 1 WHERE id = (SELECT min(id) FROM ledger_entries);` | Error: `ledger_entries is append-only (UPDATE rejected)` even as the schema owner | Immutable ledger [BR-19] |

Optional (streaming profile): after Act 2 and 3, open http://localhost:8025. Customer emails for approved, received, refunded and rejected returns appear (about 650 in a full run). Without `RR_KAFKA_ENABLED=true` nothing is sent and nothing is lost: events wait in `outbox_events`.

### Between demo runs

Golden cases are consumed by a run (so are the API scenario, Playwright and Locust). Re-seed before every run:

```powershell
infra/scripts/chaos.ps1 healthy
docker compose run --rm app --spring.profiles.active=seed --seed.as-of=<today YYYY-MM-DD> --seed.reset=true
```

`--seed.reset=true` also clears the Redis rate-limit and lockout counters, which fixes a customer lookup stuck on 429 or a locked staff account.

## 6. Operations

### Logs and metrics

| Need | Command or place |
|---|---|
| Follow API logs | `docker compose logs -f app` (every line carries `[cid:<correlation id>]`; the UI shows the same id as "Ref" in error toasts) |
| Gateway, DB, others | `docker compose logs -f gateway` (or `db`, `web`, `notifier`) |
| Metrics | http://localhost:8000/actuator/prometheus, http://localhost:9090, Grafana dashboard |
| Alerts | http://localhost:9090/alerts (no Alertmanager on purpose) |

### Alerts and what to do

| Alert | Meaning | Action |
|---|---|---|
| `RefundSuccessRateLow` | Success rate below 90 percent for 2 min | Check the gateway panels and `infra/scripts/chaos.ps1 status`. If it is a fault profile, set `healthy`. Do not retry `UNKNOWN` attempts by hand; the reconciler resolves them |
| `RefundReconciliationStuck` | An attempt has been `UNKNOWN` for more than 15 min | Open the return in the finance UI and press "Recheck with gateway" (calls the gateway with the same key). If it stays stuck check the gateway health and the app logs for `reconcile ... failed`. The alert text mentions `docs/runbooks/refund-unknown.md`, which does not exist in the repo |
| `LedgerReconciliationMismatch` | R1, R2 or R4 above 0 | Treat as a possible double payment or missing posting. Open the manager reconciliation page for sample ids, check `refund_attempts` and `ledger_transactions`. Corrections are `REVERSAL` transactions through `LedgerService.reverse`, never an `UPDATE` |

### Common problems

| Symptom | Fix |
|---|---|
| Port 5432 or 5433 already used | Expected: Postgres is published on **5434** to avoid local installs. For other ports stop the conflicting program or change the left side of the port mapping in `docker-compose.yml` |
| `TLS handshake timeout` or proxy errors from Docker Hub while building | Network blip; run the same command again |
| Error about timezone `Asia/Calcutta` from PostgreSQL | The JVM alias is rejected by PG. The app sets the JVM zone to UTC (`-Duser.timezone=UTC`, set in `ReturnRightApplication`); make sure you run the compose app or `mvnw` from the repo and not a custom JVM launch |
| `429 RATE_LIMITED` on customer login | Limits: 5 lookups per minute per IP and 10 failed lookups per hour per order [A-29]. Wait `retry_after_seconds` or re-seed with `--seed.reset=true` |
| Staff login says account locked (423) | 5 failures lock for 15 minutes; re-seed with `--seed.reset=true` |
| Compose says a variable is not set | `.env` missing or incomplete: run `infra/scripts/init-env.ps1` or `cp .env.example .env` |
| App cannot connect to DB after editing `.env` DB passwords | Roles are created once: `docker compose down -v`, then up and re-seed (this wipes data) |
| "Database already has data" from the seeder | Add `--seed.reset=true` |
| Day-30 and day-31 cases look wrong | You seeded with an old `--seed.as-of`; re-seed with today's IST date |
| Compose project clash | The project name is `returnright`; stop any other compose project using that name |
| `./mvnw: permission denied` (Linux, macOS) | `chmod +x mvnw` |
| Playwright browser download blocked | `$env:E2E_CHANNEL='msedge'; npx playwright test` |
| Emails missing in Mailpit | The stack was not started with `RR_KAFKA_ENABLED=true` and `--profile streaming` |

### Running the tests

All suites need Docker running (Testcontainers for the backend). Re-seed between the API scenario, Playwright and Locust because they consume golden cases.

| Suite | Command (repo root) |
|---|---|
| Backend unit, integration, ArchUnit | `cd backend; .\mvnw.cmd verify` |
| Gateway | `cd gateway; .\mvnw.cmd verify` |
| Notifier | `cd notifier; .\mvnw.cmd verify` |
| Frontend types and lint | `cd frontend; npm ci; npm run typecheck; npm run lint` |
| API scenario (stack up, freshly seeded) | `py -3.13 tests/e2e-api/scenario.py` |
| Browser journeys (stack up, freshly seeded) | `cd frontend; npx playwright test` |
| Load "approve storm" | `locust -f tests/load/locustfile.py --headless -u 10 -r 10 -t 30s --host http://localhost:8000` (or the `locustio/locust:2.32.0` container, see the file header) |

No JDK 21 on the machine? Run a Java suite in a container:

```powershell
docker run --rm -v "${PWD}:/repo" -v //var/run/docker.sock:/var/run/docker.sock `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -w /repo/backend eclipse-temurin:21-jdk ./mvnw -B verify
```

(In Git Bash prefix with `MSYS_NO_PATHCONV=1`. The backend integration test takes about 2 minutes.)

### CI

`.github/workflows/ci.yml` runs on pushes to `main`, `dev` and `feature/**` and on pull requests: `java` (backend, gateway, notifier with `mvnw -B verify`), `frontend` (typecheck, lint, build), and `e2e` (compose up with `cp .env.example .env`, seed, API scenario, re-seed, Playwright). Locust is not in CI. The workflow has not yet run on GitHub; check its first run and fix environment-specific issues (Playwright browser install, `compose --wait`).

## 7. Demo-day checklist

- [ ] `.env` exists (not committed) and `docker compose ps` shows everything healthy
- [ ] Seeded today with `--seed.reset=true`; seed output shows R1 to R5 = 0
- [ ] `infra/scripts/chaos.ps1 healthy`
- [ ] Two private windows ready for `finance1` and `finance2`
- [ ] Grafana dashboard loads; Prometheus `/alerts` loads
- [ ] pgAdmin owner server connects (password from `RR_OWNER_DB_PASSWORD`)
- [ ] Rehearsed the `lost-responses` step once (it is probabilistic) and the `flaky` alert once
- [ ] Optional: streaming profile up and Mailpit open
