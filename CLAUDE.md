# CLAUDE.md — ReturnRight

A retail returns and refunds portal (hackathon Track A). It is a modular-monolith API plus a mock payment gateway and a notifier microservice, with a React frontend.
Design source of truth: `docs/architecture/01-architecture-part1.md`, `docs/architecture/02-architecture-part2.md`, `docs/adr/` (**ADR-002 maps the design onto Java/Spring**), `docs/api/contract.md` (binding API contract).
Assumptions are tagged `[A-nn]`, transitions `T-nn`, failure modes `F-nn` and DB rules `BR-nn`. Cite these tags in code comments, tests and commits.
**Current status, what is left, and known traps: read `docs/HANDOFF.md` before starting work.**

## Stack (fixed)
- **Backend `backend/`**: Java 21, Spring Boot 4.1.3 (Web MVC, Data JPA/Hibernate 7, Security + oauth2-resource-server JWT, Validation, Actuator + Micrometer Prometheus, Data Redis, Kafka), Flyway, PostgreSQL 18, ZXing, Datafaker, springdoc-openapi. Build with `./mvnw` (Maven wrapper).
- **Gateway `gateway/`**: Spring Boot 4.1.3, its own H2 file DB. **Notifier `notifier/`**: Spring Boot Kafka consumer → SMTP (Mailpit).
- **Frontend `frontend/`**: React 19 + Vite + TypeScript, Node 26.13.1, React Router 7, TanStack Query, Tailwind v4, Recharts.
- Infra: Docker Compose (web/nginx, app, gateway, db, redis, pgadmin, prometheus, grafana; profile `streaming`: kafka, notifier, mailpit). CI: GitHub Actions.
- Tests: JUnit 5 + Testcontainers + MockMvc + ArchUnit; Playwright ≥ 1.60; Locust.
- Open-source/free only. Synthetic data only. Do not split returns/refunds/ledger/audit into separate services (ADR-002).

## Workflow
1. **Plan first.** Before coding a slice, write a short plan: files to touch, transitions/rules affected (by tag), tests to add. Wait for approval when it changes the design docs.
2. Work in the vertical slices of `02-architecture-part2.md` §19, in order. Do not start UI for a flow whose backend tests are red.
3. **Commit after each verified step.** Run the relevant tests, see them pass, then commit. Use small commits with the message `<slice>: <what> [T-nn/BR-nn]`. Never commit with failing tests or with `--no-verify`.
4. If a requirement is ambiguous, check the assumptions table first. If it is not covered, ask, and then add a new `[A-nn]` row; don't silently invent behaviour.
5. Design changes go into the docs (or a new ADR) in the same commit as the code.

## State machine rules (non-negotiable)
- **Only `ReturnStateMachine.apply(returnId, event, actor, expectedVersion, payload)` (`com.returnright.returns.service`) may change a return or a return item.** Entity mutators are public for the service package, but `ArchitectureTest.onlyTheStateMachineMutatesReturnsAndReturnItems` forbids every other caller. Never write status in native SQL, and not in the seeder either.
- Transitions are data: the `TRANSITIONS` table in `ReturnStateMachine`. To add a transition, update the table, the DB trigger `trg_returns_valid_transition` (new Flyway migration), Part 1 §4, and the tests, all in one commit.
- `apply` locks the row (`PESSIMISTIC_WRITE`), then checks the `If-Match` version, the role, then the guards. Unknown pair → 409 `ILLEGAL_TRANSITION`, version mismatch → 409 `STALE_VERSION`. It flushes after every transition so the DB trigger sees each step.
- DB side effects (items, labels, refund rows, ledger, audit, notifications, outbox) go in the **same transaction**. External calls (gateway) run **after commit** only, never inside `@Transactional`.
- Every state change writes `audit_log` via `AuditService.record(...)` in the caller's transaction (`Propagation.MANDATORY`).

## Money and idempotency rules (non-negotiable)
- Money is a `long` of **paise** (`BIGINT`, `*Paise` / `*_paise`). No `double`/`float`/`BigDecimal` in storage. Grade C = `(price + 1) / 2`.
- Mutating finance/refund/submit endpoints require an `Idempotency-Key` header. Human mutations on a return require `If-Match`.
- One `refunds` row per return; one `refund_items` row per unit; at most one attempt in `PENDING|UNKNOWN|SUCCEEDED` per refund. **Do not weaken these constraints.**
- Persist the `refund_attempts` row (`gateway_idempotency_key = rr-{refundId}-{attemptNo}`) and **commit** before calling the gateway. Every retry of that attempt reuses the same key and amount.
- Timeout, 5xx or connection error → attempt `UNKNOWN`, never `FAILED`. Only an explicit gateway `status: FAILED` is `FAILED`.
- **Never create a new attempt or let a human "retry" while an attempt is `UNKNOWN`.** Only the reconciler or the recheck resolves it.
- `RefundService.finalizeAttempt()` must stay idempotent (lock the attempt; no-op if it is already terminal).

## Ledger rules
- **Never UPDATE or DELETE ledger rows** (or audit rows). Corrections are a `REVERSAL` transaction plus a new posting. Ledger entities are `@Immutable`.
- Write only through `LedgerService.post*()`. Never build entries elsewhere. Every transaction must balance (deferred trigger).
- Post only on `SUCCEEDED`, once per attempt (`refund_attempt_id` UNIQUE).

## Time rules
- Never call `Instant.now()`/`LocalDate.now()`/`OffsetDateTime.now()` in domain code. Inject `java.time.Clock` (bean). Tests use `Clock.fixed` / a mutable test clock.
- Store `timestamptz` (UTC `Instant`/`OffsetDateTime`). Business-day rules use `Asia/Kolkata` calendar dates (`[A-02]`: day 30 inclusive, day 31 rejected).
- `returns.created_at` comes from the same clock reading used for eligibility.

## Coding conventions
- **Package layout** (enforced by `ArchitectureTest`): `com.returnright.<module>.{entity,repository,service}`; HTTP lives only in `com.returnright.api.{controller,dto.request,dto.response,mapper,security,support}`; shared kernel in `com.returnright.core.{config,exception,enums,model,util,web,infra}`. Module layering: `core` ← domain modules (`orders`, `eligibility`, `labels`, `audit`, `notifications`, `ledger`, `gatewayclient`, `auth`, `analytics`) ← `returns` ← `refunds` ← `inspection`/`finance` ← `api`/`jobs`/`seed`. No cycles. `eligibility` depends on nothing but the JDK.
- Controllers never touch repositories; repositories are used only by services (plus `jobs`, `seed`, `api.mapper`); entities never depend on services; DTOs never expose entities. One public type per file.
- Controllers are thin: validate the request record, call a service, map to the DTO. DTOs are Java `record`s. No entities in responses.
- Errors: throw `ApiException(code, message, HttpStatus, details)`. `GlobalExceptionHandler` renders `{"error":{code,message,details,correlation_id}}`. Never leak stack traces.
- Customer-facing eligibility text only comes from `EligibilityMessages`.
- Constructor injection only; no field `@Autowired`. No Lombok. `final` fields. Prefer `Optional` returns from repositories.
- Flyway: one migration per change (`V{n}__desc.sql`), never edit an applied migration. Hibernate `ddl-auto=validate`. Business-rule constraints carry their `BR-nn` comment.
- Metrics: Micrometer names `rr.*` (exported as `rr_*`). Tags use route templates or enum values, never IDs.
- **No secrets in code, config or docs.** Secret values live only in the repo-root `.env` (git-ignored); `.env.example` lists every variable with dev-only placeholders. Compose uses `${VAR:?...}` (fail fast), Spring reads `${RR_...}` with no fallback and imports `.env` when run from the repo. Throwaway test credentials live in one `TestSecrets` class per service. LF line endings (`.gitattributes`), except `*.cmd`/`*.ps1`.

## Testing expectations
- **Unit**: eligibility boundaries (IST day 30/31, electronics 10/11, 23:59:59 vs 00:00 IST), the transition table (every legal pair and a sample of illegal ones), money rounding, ledger templates. No Spring context.
- **Integration** (`@SpringBootTest` + Testcontainers Postgres + an in-process fake or the real gateway): every `BR-nn` constraint rejects bad data; concurrency (two threads approve → one wins); the ADR-001 refund scenarios (double click, timeout + retry, lost response, crash before call, crash after success); reconciliation R1–R5 = 0.
- **API** (MockMvc): RBAC matrix, error envelope, `Idempotency-Key` replay/mismatch/in-progress, `If-Match`.
- **Architecture**: ArchUnit layering + "only the state machine writes status".
- **E2E** (Playwright): one journey per persona, plus the two-reviewer race.
- A new transition, endpoint or business rule is not done until it has tests at the right level. A bug fix starts with a failing test.

## Commands
- First time: `infra/scripts/init-env.ps1` (or `cp .env.example .env`) — creates the secrets file.
- Build and test the backend: `cd backend && ./mvnw verify` (Windows: `mvnw.cmd verify`). Docker must be running (Testcontainers).
- Run the full stack: `docker compose up -d --build` → web http://localhost:8080, API http://localhost:8000, pgAdmin http://localhost:5050, Grafana http://localhost:3000, Prometheus http://localhost:9090, gateway http://localhost:8100.
- With streaming: `RR_KAFKA_ENABLED=true docker compose --profile streaming up -d` (PowerShell: `$env:RR_KAFKA_ENABLED='true'; docker compose --profile streaming up -d`) → Kafka, notifier, Mailpit http://localhost:8025. Without `RR_KAFKA_ENABLED=true` the API does not publish and no mail is sent.
- Seed data: `docker compose run --rm app --spring.profiles.active=seed --seed.as-of=YYYY-MM-DD --seed.reset=true`
- Frontend: `cd frontend && npm run dev` (proxies `/api` to :8000) · `npx playwright test`
- Gateway chaos: `infra/scripts/chaos.ps1 <healthy|slow|flaky|lost-responses|blackhole|declines>`
