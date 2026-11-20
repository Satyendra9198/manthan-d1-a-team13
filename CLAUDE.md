# CLAUDE.md — ReturnRight

A retail returns and refunds portal (hackathon Track A). It is a modular monolith (FastAPI + React) plus a separate mock payment gateway.
Design source of truth: `docs/architecture/01-architecture-part1.md`, `docs/architecture/02-architecture-part2.md`, `docs/adr/`.
Assumptions are tagged `[A-nn]`, transitions `T-nn`, failure modes `F-nn` and DB rules `BR-nn`. Cite these tags in code comments, tests and commits.

## Stack (fixed, do not change)
- Backend: Python 3.13, FastAPI, SQLAlchemy 2 (sync, `Mapped[]` style), psycopg 3, Alembic, pydantic v2, httpx, PyJWT, argon2-cffi, segno, prometheus-client.
- Frontend: React 19 + Vite + TypeScript, Node 26.13.1, React Router 7, TanStack Query, Recharts. The API client is generated from FastAPI OpenAPI.
- PostgreSQL 18 in Docker. Gateway: separate FastAPI service with its own SQLite store.
- Tests: pytest, Playwright ≥ 1.60, Locust. Deploy: Docker Compose (app, db, gateway, prometheus, grafana). CI: GitHub Actions.
- Do NOT add Kafka, Redis, Celery, Kubernetes or extra services. Open-source/free only. Synthetic data only.

## Workflow
1. **Plan first.** Before coding a slice, write a short plan: files to touch, transitions/rules affected (by tag), tests to add. Wait for approval when it changes the design docs.
2. Work in the vertical slices of `02-architecture-part2.md` §19, in order. Do not start UI for a flow whose backend tests are red.
3. **Commit after each verified step.** Run the relevant tests, see them pass, then commit. Use small commits with the message `<slice>: <what> [T-nn/BR-nn]`. Never commit with failing tests or with `--no-verify`.
4. If a requirement is ambiguous, check the assumptions table first. If it is not covered, ask, and then add a new `[A-nn]` row; don't silently invent behaviour.
5. Design changes go into the docs (or a new ADR) in the same commit as the code.

## State machine rules (non-negotiable)
- **Only `returns.service.apply_event(session, return_id, event, actor, expected_version=..., payload=...)` may change `returns.status` or `return_items.status`.** Never assign `.status` anywhere else, never in raw SQL, and not in seed scripts either.
- Transitions are data: the `TRANSITIONS[(state, event)]` table. To add a transition, update the table, the DB trigger `trg_returns_valid_transition`, the diagram/table in Part 1 §4, and the tests, all in one commit.
- `apply_event` locks the row (`SELECT … FOR UPDATE`), checks `If-Match` version, role, then guards. Unknown pair → 409 `ILLEGAL_TRANSITION`, version mismatch → 409 `STALE_VERSION`.
- DB side effects (items, labels, refund rows, ledger, audit, notifications) go in the **same transaction**. External calls (gateway, file writes) run **after commit** only.
- Every state change writes `audit_log` in the same transaction via `audit.record()`. `audit.record` never commits on its own.

## Money and idempotency rules (non-negotiable)
- Money is an integer **paise** (`int`/`BIGINT`, `_paise` suffix). No floats or `Decimal` in storage. Grade C = `(price + 1) // 2`.
- Mutating finance/refund/submit endpoints require an `Idempotency-Key` header. Human mutations on a return require `If-Match`.
- One `refunds` row per return; one `refund_items` row per unit; at most one attempt in `PENDING|UNKNOWN|SUCCEEDED` per refund. **Do not weaken these constraints.**
- Persist the `refund_attempts` row (with `gateway_idempotency_key = rr-{refund_id}-{attempt_no}`) **before** calling the gateway. Every retry of that attempt reuses the same key and amount.
- Timeout, 5xx or connection error → attempt `UNKNOWN`, never `FAILED`. Only an explicit gateway `status: FAILED` is `FAILED`.
- **Never create a new attempt or let a human "retry" while an attempt is `UNKNOWN`.** Only the reconciler or the recheck resolves it.
- `finalize_attempt()` must stay idempotent (lock the attempt; no-op if it is already terminal).

## Ledger rules
- **Never UPDATE or DELETE ledger rows** (or audit rows). Corrections are a `REVERSAL` transaction plus a new posting.
- Write only through `ledger.post(<template>)`. Never build entries by hand. Every transaction must balance (deferred trigger).
- Post only on `SUCCEEDED`, once per attempt (`refund_attempt_id` UNIQUE).

## Time rules
- Never call `datetime.now()`/`date.today()` in domain code. Use the injected `Clock`. Tests use `FixedClock`.
- Store `timestamptz` in UTC. Business-day rules use `Asia/Kolkata` calendar dates (`[A-02]`: day 30 inclusive, day 31 rejected).
- `returns.created_at` comes from the same `Clock` value used for eligibility.

## Coding conventions
- Module layering (enforced by import-linter): `core` ← domain modules ← `returns` ← `refunds` ← `inspection`/`finance` ← `api`/`jobs`. No upward imports. `eligibility` imports nothing but the stdlib.
- Each module contains `models.py`, `schemas.py`, `service.py`, and an optional `router.py`. Routers are thin: parse, call the service, map the DTO.
- Errors: raise `AppError(code, message, status, details)`. The handler renders `{"error":{code,message,details,correlation_id}}`. Never return a stack trace.
- Customer-facing text for eligibility comes only from `eligibility/messages.py`.
- Python: ruff (lint + format), mypy `--strict` on `app/`, type hints everywhere. TS: `strict: true`, ESLint, no `any`.
- Migrations: Alembic only, one per change, and never edit an applied migration. Business-rule constraints keep their `BR-nn` comment.
- Use the `rr_` prefix for Prometheus metrics. Use route templates, never raw paths or IDs, as labels.
- No secrets in git. `.env.example` holds dev defaults only. Line endings are LF (see `.gitattributes`).

## Testing expectations
- **Unit** (`tests/unit`): eligibility boundaries (IST day 30/31, electronics 10/11, 23:59:59 vs 00:00 IST), the transition table (every legal pair and a sample of illegal ones), money rounding, ledger templates.
- **Integration** (`tests/integration`, real Postgres + real gateway): every `BR-nn` constraint rejects bad data; concurrency (two threads approve → one wins); the refund scenarios from ADR-001 (double click, timeout + retry, lost response, crash before call, crash after success) under gateway fault profiles; reconciliation R1–R5 = 0 after each test.
- **API** (`tests/api`): RBAC matrix, error envelope, `Idempotency-Key` replay/mismatch/in-progress, `If-Match`.
- **E2E** (Playwright): one journey per persona, plus the two-reviewer race.
- A new transition, endpoint or business rule is not done until it has tests at the right level. A bug fix starts with a failing test.
- Commands (run inside containers if the host lacks deps):
  - `docker compose up -d --build` · `docker compose run --rm app alembic upgrade head`
  - `docker compose run --rm app python -m app.seed --as-of <YYYY-MM-DD> --reset`
  - `docker compose run --rm app pytest -q` · `cd frontend && npx playwright test`
  - Gateway chaos: `infra/scripts/chaos.ps1 <healthy|slow|flaky|lost-responses|blackhole|declines>`
