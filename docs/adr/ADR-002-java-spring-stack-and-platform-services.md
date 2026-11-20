# ADR-002: Java/Spring Boot stack and added platform services

- **Status:** Accepted (2026-11-20). **Supersedes** the Python/FastAPI stack named in Architecture Parts 1–2, `docs/api/contract.md` (stack-neutral, unchanged) and the first CLAUDE.md draft.
- **Deciders:** ReturnRight team

## Context
The team chose **Java 21 + Spring Boot 4.1.x** as the backend. That is an allowed Track A option in the event prerequisites guide, and JDK 21 (Temurin) is preinstalled on every laptop. The team also asked for a production-grade feel. Within the hackathon's free/open-source and single-laptop limits, that means Redis, an event stream (Kafka), pgAdmin for browsing the mock data, and a microservice split where it adds real value.

The domain design does **not** change: the state machine (T1–T17), failure modes (F-01…F-20), DB rules (BR-1…BR-20), ledger, ADR-001 idempotency and the API contract all stay as written. Only the implementation technology and the platform services change.

## Decision

### Technology mapping
| Concern | Was (Parts 1–2) | Now |
|---|---|---|
| Language/runtime | Python 3.13 | **Java 21 (Temurin)** |
| Web framework | FastAPI | **Spring Boot 4.1.3**, Spring Web MVC |
| ORM / DB access | SQLAlchemy 2 + psycopg 3 | **Spring Data JPA (Hibernate 7)**, PostgreSQL JDBC; native SQL for analytics |
| Migrations | Alembic | **Flyway** (`backend/src/main/resources/db/migration`) |
| Pessimistic lock | `SELECT … FOR UPDATE` | `@Lock(PESSIMISTIC_WRITE)` repository method, same SQL |
| Unit of work | `with session.begin()` | `@Transactional` / `TransactionTemplate` (gateway call outside the txn) |
| Validation | pydantic | Jakarta Bean Validation on request `record`s |
| Auth | PyJWT + argon2-cffi | **Spring Security** (stateless, `oauth2-resource-server` JWT HS256 via Nimbus) + `Argon2PasswordEncoder` (BouncyCastle) |
| Metrics | prometheus-client | **Micrometer + Actuator** `/actuator/prometheus` (metric names keep the `rr_` prefix) |
| Background jobs | asyncio loops | `@Scheduled` + `pg_try_advisory_lock` (single runner) |
| QR codes | segno | **ZXing** core (SVG rendered from the bit matrix) |
| Seed data | Faker | **Datafaker** (`net.datafaker`), deterministic seed, run via `--spring.profiles.active=seed` |
| Module layering check | import-linter | **ArchUnit** tests |
| Tests | pytest | **JUnit 5**, Spring Boot Test, **Testcontainers** (Postgres), MockMvc. Playwright and Locust unchanged. |
| API docs | FastAPI OpenAPI | **springdoc-openapi** `/v3/api-docs`, Swagger UI at `/swagger-ui.html` (dev profile) |
| Mock gateway | FastAPI + SQLite | **Spring Boot** service with its own **H2 file DB** (still a separate store and container) |
| DB enums | native PG enums | `text` columns + `CHECK (col IN (...))` constraints. They enforce the same rules with far less JPA friction. |

### Platform services added (Docker Compose)
| Service | Purpose | Always on? |
|---|---|---|
| `web` (nginx) | Serves the React build, reverse-proxies `/api` to `app`, security headers | yes |
| `app` | ReturnRight API (modular monolith) | yes |
| `gateway` | Mock payment gateway microservice with fault injection | yes |
| `db` | PostgreSQL 18 | yes |
| `redis` | Login lockout, rate limiting, JWT revocation (logout). **Not** on the money path: if Redis is down, the app degrades to in-memory fallbacks. | yes |
| `pgadmin` | Browse the seeded mock data (preconfigured server, read-only role available) | yes |
| `prometheus`, `grafana` | Metrics, alert rules, dashboards | yes |
| `kafka` (KRaft, single node) | Domain events from the **transactional outbox** (`outbox_events`), published by a relay job | profile `streaming` |
| `notifier` | Spring Boot microservice that consumes `returnright.events` and sends customer emails over SMTP | profile `streaming` |
| `mailpit` | Local SMTP catcher with a web UI, so the emails can be demoed | profile `streaming` |

**Microservice boundary rule:** only things that are naturally separate systems become services (payment gateway, notifications). Returns, refunds, ledger and audit stay **in one process and one database**, because their invariants depend on single-transaction atomicity (ADR-001). Splitting them would need sagas, which buy nothing in a hackathon and add double-pay risk.

**Kafka is optional by design.** The outbox row is written in the same transaction as the state change. Kafka delivery is at-least-once and the notifier dedupes on the event id, so turning Kafka off loses no data; events just stay unpublished.

## Consequences
- The Docker Desktop VM on the demo laptop has 8 GB. The core profile (web, app, gateway, db, redis, pgadmin, prometheus, grafana) fits comfortably. `streaming` adds ~1 GB (Kafka heap capped at 512 MB).
- Maven Wrapper (`./mvnw`) in each service, so no Maven install is needed.
- Architecture Parts 1–2 still describe the domain correctly. Wherever they mention a Python library, read the mapping above.
