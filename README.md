# Splitwise-style Expense Splitting API

[![CI](https://github.com/glitchbytez/splitwise/actions/workflows/ci.yml/badge.svg)](https://github.com/glitchbytez/splitwise/actions/workflows/ci.yml)

A backend-first expense-splitting application: groups, shared expenses, derived balances, settlements and debt simplification.
Built with Java 21, Spring Boot 4, PostgreSQL 16 and Flyway, and tested with JUnit 5, Testcontainers and REST Assured.

The full specification is in [`docs/SPEC.md`](docs/SPEC.md). Decisions that are not in the spec are recorded in [`docs/DECISIONS.md`](docs/DECISIONS.md).

## Status

| Phase | Scope | State |
|---|---|---|
| 0 | Foundation: skeleton, Flyway V1 schema, Docker Compose, CI, `Money`, error handling | ✅ Done |
| 1 | Auth and users (register, login, JWT) | ⏳ Next |
| 2 | Groups and members | — |
| 3 | Expenses and split strategies | — |
| 4 | Balances, settlements, debt simplification | — |
| 5 | Hardening and docs | — |
| 6 | Azure deployment | — |

### What exists today
- **Package-by-feature layout:** `auth`, `user`, `group`, `expense`, `settlement`, `balance`, `activity`, `common`.
- **Database schema** (`src/main/resources/db/migration/V1__init.sql`): all tables from the spec. Money is stored as `BIGINT` cents, and check constraints cover positive amounts, USD-only, valid split types and roles, no self-settlements, and lower-case unique emails.
- **`Money` value object:** cents stored in a `long`, currency-aware, overflow-checked, and it rejects more than 2 decimal places instead of rounding.
- **Errors:** every error is an RFC 7807 `application/problem+json` body, and validation errors include a per-field `errors` array.
- **Security baseline:** stateless setup where everything under `/api` requires authentication (401 for now; the JWT login arrives in Phase 1).
- **API docs:** Swagger UI at `/swagger-ui.html` and the OpenAPI document at `/v3/api-docs`.
- **Health:** `/actuator/health` is the only actuator endpoint exposed.

## Running locally

### With Docker (app + database)
```bash
cp .env.example .env        # optional: the compose file has local defaults
docker compose up --build
```
- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- PostgreSQL: `localhost:5433` (db/user/password `splitwise` unless overridden in `.env`)

### From the IDE or command line
Start only the database, then run the app with the default `local` profile:
```bash
docker compose up -d db
./mvnw spring-boot:run
```
Or run against a throwaway Testcontainers database, without Compose:
```bash
./mvnw spring-boot:test-run
```

### Configuration
| Variable | Purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` (default), `prod`; `test` is used by integration tests |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL connection. Required in `prod`; `local` has defaults |
| `JWT_SECRET` | JWT signing key (from Phase 1). Never commit it |

## Tests
```bash
./mvnw verify
```
- `*Test` are fast unit tests (Surefire). `*IT` are integration and API tests that run against a real PostgreSQL in Testcontainers (Failsafe), so they need Docker.
- The coverage report is at `target/site/jacoco/index.html` (unit and integration results merged). CI uploads it as an artifact.

## Project layout
```
src/main/java/com/glitchbytez/splitwise
├── auth/        security configuration (JWT from Phase 1)
├── user/  group/  expense/  settlement/  balance/  activity/
└── common/
    ├── money/   Money value object
    ├── error/   ApiException hierarchy + GlobalExceptionHandler (ProblemDetail)
    ├── web/     PageResponse envelope
    └── config/  OpenAPI configuration
```
