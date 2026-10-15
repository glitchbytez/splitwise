# Splitwise-style Expense Splitting API

[![CI](https://github.com/glitchbytez/splitwise/actions/workflows/ci.yml/badge.svg)](https://github.com/glitchbytez/splitwise/actions/workflows/ci.yml)

A backend-first expense-splitting application: groups, shared expenses, derived balances, settlements and debt simplification.
Built with Java 21, Spring Boot 4, PostgreSQL 16 and Flyway, and tested with JUnit 5, Testcontainers and REST Assured.

The full specification is in [`docs/SPEC.md`](docs/SPEC.md). Decisions that are not in the spec are recorded in [`docs/DECISIONS.md`](docs/DECISIONS.md).

## Status

| Phase | Scope | State |
|---|---|---|
| 0 | Foundation: skeleton, Flyway V1 schema, Docker Compose, CI, `Money`, error handling | ✅ Done |
| 1 | Auth and users (register, login, JWT) | ✅ Done |
| 2 | Groups and members | ⏳ Next |
| 3 | Expenses and split strategies | — |
| 4 | Balances, settlements, debt simplification | — |
| 5 | Hardening and docs | — |
| 6 | Azure deployment | — |

### What exists today
- **Package-by-feature layout:** `auth`, `user`, `group`, `expense`, `settlement`, `balance`, `activity`, `common`.
- **Database schema** (`src/main/resources/db/migration/V1__init.sql`): all tables from the spec. Money is stored as `BIGINT` cents, and check constraints cover positive amounts, USD-only, valid split types and roles, no self-settlements, and lower-case unique emails.
- **`Money` value object:** cents stored in a `long`, currency-aware, overflow-checked, and it rejects more than 2 decimal places instead of rounding.
- **Errors:** every error is an RFC 7807 `application/problem+json` body, and validation errors include a per-field `errors` array.
- **Auth:** register and log in to get a 30-minute JWT access token (HS256). Every other `/api` endpoint requires `Authorization: Bearer <token>` and returns 401 without a valid one.
- **Users:** view and update your own profile, or delete your account. Deletion anonymises the account: email, name and password hash are removed, but expense history is kept.
- **Personal data:** passwords are stored only as BCrypt hashes (strength 12) and are never returned or logged. Logs reference user ids only. Login gives the same response for an unknown email and a wrong password.
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

### Try it
```bash
curl -s -H 'Content-Type: application/json' localhost:8080/api/auth/register \
  -d '{"email":"alice@example.com","displayName":"Alice","password":"correct-horse"}'
TOKEN=$(curl -s -H 'Content-Type: application/json' localhost:8080/api/auth/login \
  -d '{"email":"alice@example.com","password":"correct-horse"}' | jq -r .accessToken)
curl -s -H "Authorization: Bearer $TOKEN" localhost:8080/api/users/me
```
In Swagger UI, call `/api/auth/login`, then click **Authorize** and paste the `accessToken`.

### API so far
| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | public | Create an account (`201`, `409` if the email is taken) |
| POST | `/api/auth/login` | public | Get an access token (`401` on bad credentials) |
| GET | `/api/users/me` | bearer | My profile |
| PATCH | `/api/users/me` | bearer | Change display name |
| DELETE | `/api/users/me` | bearer | Delete (anonymise) my account |

### Configuration
| Variable | Purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` (default), `prod`; `test` is used by integration tests |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | PostgreSQL connection. Required in `prod`; `local` has defaults |
| `JWT_SECRET` | JWT signing key, at least 32 bytes (`openssl rand -base64 48`). Required in `prod`; `local` and Compose fall back to a development-only value. Never commit it |

## Tests
```bash
./mvnw verify
```
- `*Test` are fast unit tests (Surefire). `*IT` are integration and API tests that run against a real PostgreSQL in Testcontainers (Failsafe), so they need Docker.
- The coverage report is at `target/site/jacoco/index.html` (unit and integration results merged). CI uploads it as an artifact.

## Project layout
```
src/main/java/com/glitchbytez/splitwise
├── auth/        register/login, JWT issuing + validation, security config, @CurrentUserId
├── user/        User entity, profile endpoints, account deletion (anonymisation)
├── group/  expense/  settlement/  balance/  activity/   (upcoming phases)
└── common/
    ├── money/   Money value object
    ├── error/   ApiException hierarchy + GlobalExceptionHandler (ProblemDetail)
    ├── web/     PageResponse envelope
    └── config/  OpenAPI configuration
```
