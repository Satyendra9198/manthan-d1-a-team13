# ReturnRight

A retail returns and refunds portal (AI Manthan hackathon, Track A). Customers start a return and print a QR label;
the warehouse scans and grades each unit; finance approves refunds above ₹10,000; refunds go through a mock payment
gateway exactly once and land in a double-entry ledger; managers see return rates, reasons, cycle time and flagged
customers. Every state change is audited.

**Stack:** Java 21 + Spring Boot 4.1.3 (modular monolith), PostgreSQL 18, React 19 + Vite, a mock payment gateway and a
notifier as separate services, Prometheus + Grafana. Everything runs in Docker Compose on one laptop.
Design: [`docs/architecture/`](docs/architecture), [`docs/adr/`](docs/adr), API contract
[`docs/api/contract.md`](docs/api/contract.md). Rules for contributors (and Claude): [`CLAUDE.md`](CLAUDE.md).

## Quick start

Prerequisites (hackathon guide): **Docker Desktop** (WSL 2) and **Git**. For running tests outside Docker:
JDK 21 (Temurin), Node 26.13.1, Python 3.13. No Maven install is needed: each Java service has a Maven wrapper.

```powershell
git clone <repo-url>; cd manthan-d1-a-team13
docker compose up -d --build --wait          # first build takes a few minutes
docker compose run --rm app --spring.profiles.active=seed --seed.as-of=2026-11-20 --seed.reset=true
```

Use **today's date** for `--seed.as-of`. Then open:

| What | URL | Login |
|---|---|---|
| App (customer + staff) | http://localhost:8080 | see below |
| API + Swagger UI | http://localhost:8000/swagger-ui.html | – |
| Grafana dashboard | http://localhost:3000 | `admin` / `ReturnRight#2026` |
| Prometheus | http://localhost:9090 | – |
| pgAdmin (browse the data) | http://localhost:5050 | `admin@returnright.dev` / `ReturnRight#2026` |
| Mock gateway | http://localhost:8100/healthz | admin token `dev-admin-token` |

**Logins:** staff `warehouse1`, `finance1`, `finance2`, `manager1` (password `ReturnRight#2026`). Customers log in
with an order number and its email, e.g. `RR-EDGE-001` + `edge.customer@example.com`. All golden cases (day 30/31,
electronics 10/11, innerwear, the finance threshold, flagged customers, gateway declines) are listed in
[`docs/demo-data.md`](docs/demo-data.md).

### Emails (optional, profile `streaming`)

```powershell
$env:RR_KAFKA_ENABLED='true'; docker compose --profile streaming up -d --build
```

Adds Kafka, the notifier and Mailpit. Customer emails appear at http://localhost:8025. Without
`RR_KAFKA_ENABLED=true` the API keeps events in its outbox and nothing is sent; no data is lost.

### Break the gateway (SRE demo)

```powershell
infra/scripts/chaos.ps1 lost-responses   # also: healthy | slow | flaky | blackhole | declines
```

Refunds that time out or lose their response become `UNKNOWN` (never `FAILED`), and the reconciler or the finance
**Recheck** button finishes them with the same idempotency key, so nobody is paid twice. Watch the success-rate panel
and the `RefundSuccessRateLow` alert in Grafana, then switch back with `healthy`.

## Tests

| Suite | Command | What it proves |
|---|---|---|
| Backend | `cd backend; ./mvnw verify` (needs Docker for Testcontainers) | Eligibility boundaries, transition table = DB trigger, ArchUnit layering, ADR-001 refund scenarios (double click, timeout, lost response, race), ledger is append-only |
| Gateway | `cd gateway; ./mvnw verify` | One payout per key under 50 concurrent requests, key mismatch → 422, fault profiles |
| Notifier | `cd notifier; ./mvnw verify` | Email wording, dedupe, retry on SMTP failure |
| Frontend | `cd frontend; npm ci; npm run typecheck; npm run lint` | Types and lint |
| API end to end | `py -3.13 tests/e2e-api/scenario.py` (stack running, freshly seeded) | Full journey across all four personas |
| Browser end to end | `cd frontend; npx playwright test` (stack running, freshly seeded) | One journey per persona + the two-reviewer race |
| Load | `locust -f tests/load/locustfile.py --headless -u 10 -r 10 -t 30s --host http://localhost:8000` | "Approve storm": no refund paid twice, reconciliation clean |

The API scenario, Playwright and Locust all change data: re-seed between them. CI
([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) runs all of the above except Locust.

No JDK 21 on the laptop? Run any Java suite in a container:

```powershell
docker run --rm -v "${PWD}:/repo" -v //var/run/docker.sock:/var/run/docker.sock `
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal -w /repo/backend eclipse-temurin:21-jdk ./mvnw -B verify
```

## Repository layout

```
backend/    Spring Boot API (modules: returns, refunds, ledger, audit, eligibility, ...), Flyway migrations, seeder
gateway/    mock payment gateway with fault injection (own H2 database)
notifier/   Kafka consumer -> customer emails (profile "streaming")
frontend/   React 19 + Vite SPA for all four personas, Playwright tests
infra/      Postgres roles, Prometheus (+ alert rules), Grafana dashboard, pgAdmin, chaos scripts
tests/      e2e-api (Python scenario), load (Locust)
docs/       architecture, ADRs, API contract, demo data
```

## Troubleshooting (Windows)

- **Port already in use** (5434, 8000, 8080, ...): stop the other service, or change the left-hand port in
  `docker-compose.yml`. Postgres is on **5434** because many laptops run a local Postgres on 5432.
- **`./mvnw: permission denied`** on Linux/macOS: `git update-index --chmod=+x` is already set; re-clone or `chmod +x mvnw`.
- **Docker Hub `TLS handshake timeout`** during a build: a network blip; run the same command again.
- **Seed says "Database already has data"**: add `--seed.reset=true`.
- **Playwright browser download blocked**: `$env:E2E_CHANNEL='msedge'; npx playwright test` uses the installed Edge.
