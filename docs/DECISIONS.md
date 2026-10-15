# Decisions log

Decisions not fully covered by `SPEC.md`, or where the implementation deviates from it. Newest last.

## Phase 0 — Foundation

### D1. Spring Boot 4.1 instead of 3.x
The spec lists Spring Boot 3.x, but the project was generated with Spring Boot 4.1.1 and open-source support for the 3.x line ended in mid-2026. We keep 4.1.1 (Spring Framework 7, Spring Security 7, Jackson 3, Hibernate 7). Consequences:
- Starters are modular: `spring-boot-starter-webmvc` (not `-web`), `spring-boot-starter-flyway` is required for Flyway auto-configuration, and test slices come from `spring-boot-starter-*-test` companions.
- springdoc-openapi 3.x (the Boot 4 line) and Testcontainers 2.x (`testcontainers-postgresql`).
- REST Assured 6.x, which is not managed by the Boot BOM, so its version is pinned in `pom.xml`.

### D2. Dependencies deferred, not dropped
Lombok and MapStruct are allowed by the spec but nothing in Phase 0 needs them. They will be added in the first phase that uses them (entities/mappers) rather than shipping unused dependencies.

### D3. `version` column on `expenses`
The spec asks for optimistic locking on expenses but the table sketch has no column for it. `V1__init.sql` adds `version bigint not null default 0` for JPA `@Version`.

### D4. No separate index on `expense_shares(expense_id)`
The unique constraint `(expense_id, user_id)` creates a B-tree index whose leading column is `expense_id`, so PostgreSQL already uses it for lookups by expense. A second index would only slow down writes. `FlywayMigrationIT` asserts the unique index exists.

### D5. Schema details chosen in V1
- UUID primary keys default to `gen_random_uuid()` (built into PostgreSQL 13+), so rows can be inserted from SQL as well as from JPA.
- Timestamps are `timestamptz`.
- Emails are enforced lower-case by a check constraint (`ck_users_email_lower_case`) plus a unique constraint, which gives case-insensitive uniqueness without the `citext` extension. The service layer lower-cases emails before saving.
- `users.password_hash` is nullable so account deletion can clear it (anonymisation, spec section 7).
- `group_members` uses the composite PK `(group_id, user_id)` from the spec. If someone leaves and is re-added, the existing row is reactivated (`left_at` cleared) rather than a new row inserted.
- `expense_shares.expense_id` uses `on delete cascade`. Expenses are only ever soft-deleted, but shares are replaced when an expense is edited, and the cascade keeps that safe.
- Enumerations (`role`, `split_type`) are `varchar` plus `check` constraints rather than PostgreSQL enum types, which are awkward to change in later migrations.

### D6. Security baseline in Phase 0
Spring Security is configured from the start: stateless, CSRF/form/basic login disabled, and only `/swagger-ui*`, `/v3/api-docs*` and `/actuator/health*` are public. Boot's default in-memory user (which logs a generated password) is excluded. 401/403 responses from the filter chain go through the same `@RestControllerAdvice` as all other errors, so every error body is an RFC 7807 `ProblemDetail`. The JWT filter and the public `/api/auth/**` endpoints come in Phase 1.

### D7. Profiles and default profile
`application.yml` reads `DB_URL`/`DB_USER`/`DB_PASSWORD` with no defaults. The `local` profile (the default when no profile is set) provides defaults that match `docker-compose.yml`. `prod` adds no defaults, so a missing variable fails fast at startup. `test` is used by integration tests, where Testcontainers supplies the datasource through `@ServiceConnection`.

### D8. Compose publishes PostgreSQL on host port 5433
Many developer machines already run PostgreSQL on 5432, and that would make `docker compose up` fail. The container still listens on 5432 internally. The host port defaults to 5433 and can be changed with `DB_PORT`.

### D9. Test split: Surefire for `*Test`, Failsafe for `*IT`
Fast unit tests run in the `test` phase. Spring Boot + Testcontainers tests are named `*IT` and run in `integration-test`. JaCoCo records both and merges them into one report at `target/site/jacoco/`. The coverage gate is added in Phase 5, as the spec says.

### D10. `Money` keeps the currency's fraction digits and never rounds
`Money.parse("12.345")` is rejected instead of rounded. Silent rounding at the API boundary would make shares stop summing to the total. Arithmetic uses `Math.*Exact`, so overflow throws an exception instead of wrapping around.
