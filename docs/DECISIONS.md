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

## Phase 1 — Auth and users

### D11. JWTs via Spring Security's OAuth2 resource server, signed with HS256
Tokens are validated by `spring-boot-starter-security-oauth2-resource-server` (Nimbus JOSE under the hood) and issued with its `JwtEncoder`. This stays inside the Spring Security stack the spec mandates, so there is no hand-written token filter and no extra JWT library such as jjwt. The API both issues and validates its own tokens, so a symmetric HS256 key from `JWT_SECRET` is enough. The app refuses to start if the secret is shorter than 32 bytes.
- Claims: `iss`, `sub` (the user id), `iat`, `exp` and `jti`. There is deliberately no email or name, because a JWT is only signed, not encrypted.
- The decoder checks the signature, expiry (with 60 s clock skew) and issuer. Tokens with `alg: none` are rejected.
- Access tokens last 30 minutes (`app.jwt.access-token-ttl`). Refresh tokens are a Phase 7 item.
- 401 responses carry the RFC 6750 `WWW-Authenticate: Bearer ...` header plus the usual `ProblemDetail` body. Spring Security 7 also serves RFC 9728 metadata at `/.well-known/oauth-protected-resource` and links to it from that header. It holds no secrets, so it is left on.

### D12. Login does not reveal which emails are registered
An unknown email and a wrong password both return `401 "Invalid email or password"`. When the email is unknown, the password is still compared against a dummy BCrypt hash, so response time doesn't give it away either. Registration does reveal that an email is taken (`409`), as the spec requires. Rate limiting login is a stretch goal (spec section 7).

### D13. Password rules
Passwords must be at least 8 characters and at most 72 **bytes**. BCrypt ignores everything after 72 bytes, and Spring Security 7 throws on longer input, so the limit is checked in UTF-8 bytes, not characters. BCrypt strength is 12. Request records and `TokenResponse` override `toString()` so passwords and tokens can't reach a log line, and `User.toString()` prints only the id.

### D14. `@CurrentUserId` instead of passing `Jwt` around
Controllers receive the caller's id as `@CurrentUserId UUID userId`, resolved from the validated token, and the parameter is hidden from the OpenAPI docs. A signed token whose subject is not a UUID is rejected with 401.

### D15. Tokens of deleted accounts
A JWT stays valid until it expires, so every "who is calling" lookup goes through `findByIdAndDeletedAtIsNull`. After account deletion the old token gets `404 User not found` on `/api/users/me`, and login fails because the password hash has been removed. A deny-list for revoked tokens isn't worth it with a 30-minute lifetime.

### D16. Account deletion does not yet check balances (follow-up for Phase 4)
`DELETE /api/users/me` anonymises the account immediately. Once groups and balances exist, deletion should be blocked (409) while the user has a non-zero balance in any group, the same rule as leaving a group (spec 3.2). This must be added in Phase 4, when balances are derived.

### D17. No Lombok yet
The `User` entity has a handful of hand-written getters, which is less than the cost of an annotation processor. Lombok can be introduced when entities grow (spec section 2 allows it for getters only).
