# Splitwise-Style Expense Splitting App — Project Spec

> This file is the single source of truth for the project. Read it fully before writing code.
> When a decision is not covered here, choose the simplest option consistent with this spec and note it in `docs/DECISIONS.md`.

---

## 1. Purpose and goals

A backend-first expense-splitting application (groups, shared expenses, balances, settlements, debt simplification).

This is a **portfolio project** for a Java developer applying to Software Developer, QA, UI/UX, AI Engineer and Data Governance roles. The code must therefore demonstrate:

- Clean layered architecture and good API design
- Correct money handling and a non-trivial algorithm (debt simplification)
- A serious automated test suite (unit, integration, API) with CI
- Secure authentication and sensible personal-data handling
- Dockerised local setup and a cloud deployment
- Good documentation (README, OpenAPI, ER diagram, data dictionary, testing strategy)

**Non-goals for v1:** real payments, multi-currency support, mobile apps, OCR receipts, social login, pending invitations for unknown emails.

**Resolved decisions (v1):**
- **Single currency: USD, app-wide.** No per-group currency. Multi-currency is a v2 feature; keep the `Money` type currency-aware so v2 is an easy change.
- **Unknown emails are rejected.** Adding a member whose email has no account returns `404` with a clear message. No invitations in v1.

---

## 2. Tech stack (fixed)

| Concern | Choice |
|---|---|
| Language / runtime | Java 21 |
| Framework | Spring Boot 3.x (Web, Data JPA, Validation, Security, Actuator) |
| Database | PostgreSQL 16 |
| Migrations | Flyway (SQL migrations, never `ddl-auto=update`) |
| Auth | Spring Security, stateless JWT (access token; refresh token in a later phase) |
| API docs | springdoc-openapi (Swagger UI at `/swagger-ui.html`) |
| Build | Maven |
| Mapping | MapStruct (or manual mappers; no entities in API responses) |
| Boilerplate | Lombok allowed for getters/builders only; no `@Data` on JPA entities |
| Testing | JUnit 5, Mockito, AssertJ, Testcontainers (PostgreSQL), REST Assured, Spring Security Test |
| Coverage | JaCoCo (report in CI; target 80%+ on service and domain packages) |
| CI | GitHub Actions |
| Containers | Dockerfile (multi-stage) + docker-compose for local dev |
| Hosting (later) | Azure App Service (Free F1) + free PostgreSQL (Neon or Supabase) |

---

## 3. Functional requirements

### 3.1 Users and authentication
- Register with email, display name, password. Email unique (case-insensitive).
- Login returns a JWT access token (short-lived, e.g. 30 min).
- Passwords hashed with BCrypt. Never log or return password hashes.
- `GET /api/users/me`, `PATCH /api/users/me` (display name), `DELETE /api/users/me` (account deletion; see 7. Data handling).

### 3.2 Groups
- Create a group (name, optional description). All amounts are in USD (single app-wide currency in v1).
- Group roles: `OWNER`, `MEMBER`.
- Add members by email. The email must belong to a registered user; unknown emails are rejected with `404 Not Found` (no invitations in v1). Adding someone already in the group returns `409 Conflict`.
- Owner can rename the group, remove members (only if their balance is zero), and delete the group.
- Members can leave a group only when their balance is zero.

### 3.3 Expenses
An expense belongs to a group and has: description, total amount, date, payer (a group member), category (optional), notes (optional), `splitType`, and a list of shares.

Split types:
- `EQUAL` — split among the selected participants equally.
- `EXACT` — each participant has an explicit amount; the sum must equal the total.
- `PERCENTAGE` — each participant has a percentage; percentages must sum to exactly 100.00.

(Stretch: `SHARES` — split by weights, e.g. 2 shares vs 1 share.)

Rules:
- Only group members can create expenses, and all participants and the payer must be group members.
- The payer may or may not be a participant.
- Expenses can be edited and soft-deleted (`deleted_at`). Editing or deleting recomputes balances (balances are always derived, see 5).
- Every create/update/delete writes an activity log entry.

### 3.4 Balances
- Per group: net balance per member (positive = is owed money, negative = owes money).
- Per group: "who owes whom" pairwise view (raw) and a **simplified** view (see 6).
- Per user: overall balance across all groups they belong to.

### 3.5 Settlements
- A settlement records that user A paid user B an amount within a group (cash or off-app payment).
- Settlements adjust balances like an expense in reverse. They can be soft-deleted (undo).
- A settlement cannot be to oneself and the amount must be greater than zero.

### 3.6 Activity feed and notifications
- Activity log per group (expense added/edited/deleted, member added/removed, settlement recorded).
- v1 notifications = in-app activity feed (`GET /api/groups/{id}/activity`, paginated).
- Stretch: email notifications via an `EmailSender` interface (log-only implementation by default, SMTP implementation optional), triggered by an application event listener, not directly by services.

### 3.7 Search and filtering
- List expenses in a group with pagination, sorting (date, amount), and filters (payer, category, date range, text search on description).

---

## 4. Domain and data model

### 4.1 Money rules (critical)
- **Never use `double`/`float`.** Store money as `BIGINT` minor units (cents) in the database and `long` in domain code, wrapped in a small `Money` value object (amount in minor units + currency code, which is always `USD` in v1 but kept in the type so v2 multi-currency is a small change).
- API accepts and returns amounts as a decimal string or number with 2 decimal places (e.g. `"12.50"`); convert at the boundary.
- Equal split remainders: compute `base = total / n` and `remainder = total % n`. Give `+1` minor unit to the first `remainder` participants, ordered deterministically by user id. The sum of shares must always equal the total exactly.
- Percentages stored as integer basis points (`3333` = 33.33%). Percentage shares are computed with floor, and the leftover cents are distributed to the largest fractional remainders (largest remainder method), ties broken by user id.

### 4.2 Tables (Flyway migration `V1__init.sql` onward)

```
users
  id (uuid, pk), email (unique, lower-case), display_name, password_hash,
  created_at, updated_at, deleted_at (nullable; anonymisation on delete)

groups
  id (uuid, pk), name, description, created_by (fk users),
  created_at, updated_at, deleted_at

group_members
  group_id (fk), user_id (fk), role ('OWNER'|'MEMBER'), joined_at, left_at (nullable)
  pk (group_id, user_id)

expenses
  id (uuid, pk), group_id (fk), description, amount_minor (bigint, > 0),
  currency (char(3), default 'USD', check = 'USD' in v1), expense_date (date), paid_by (fk users), category (nullable),
  notes (nullable), split_type ('EQUAL'|'EXACT'|'PERCENTAGE'),
  created_by (fk users), created_at, updated_at, deleted_at (nullable)

expense_shares
  id (uuid, pk), expense_id (fk), user_id (fk), share_minor (bigint, >= 0),
  percentage_bp (int, nullable)
  unique (expense_id, user_id)

settlements
  id (uuid, pk), group_id (fk), from_user (fk), to_user (fk),
  amount_minor (bigint, > 0), settled_at, created_by (fk), deleted_at (nullable)

activity_log
  id (uuid, pk), group_id (fk), actor_id (fk), type (text), entity_type, entity_id,
  payload (jsonb), created_at
```

Constraints and indexes:
- Check constraint: `sum(share_minor) = expenses.amount_minor` is enforced in the service layer and verified by tests (a DB trigger is optional).
- Indexes on `expenses(group_id, expense_date desc)`, `expense_shares(expense_id)`, `settlements(group_id)`, `activity_log(group_id, created_at desc)`, `group_members(user_id)`.

### 4.3 Balance derivation
Balances are **derived, not stored**. For a group, for each member `u`:

```
net[u] = sum(expenses paid by u)
       - sum(shares owed by u)
       + sum(settlements paid by u)     // u paid someone, so u is owed more
       - sum(settlements received by u)
```
(Exclude soft-deleted rows.) The sum of all nets in a group must always be 0 — assert this in tests and in a service-level invariant check.

---

## 5. Architecture

Layered, package-by-feature:

```
com.<handle>.splitwise
  ├── auth/            (controllers, JWT service, security config, DTOs)
  ├── user/
  ├── group/
  ├── expense/         (controller, service, repository, entity, split strategies)
  ├── settlement/
  ├── balance/         (BalanceService, DebtSimplifier)
  ├── activity/
  ├── common/          (Money, exceptions, error handling, pagination, config)
  └── SplitwiseApplication.java
```

Principles:
- Controllers: thin; validate input (`@Valid`), delegate to services, return DTOs.
- Services: business rules and transactions (`@Transactional`).
- Split logic via a **strategy pattern**: `SplitStrategy` interface with `EqualSplitStrategy`, `ExactSplitStrategy`, `PercentageSplitStrategy`. Pure functions, no Spring dependencies, so they are trivially unit-testable.
- `DebtSimplifier` is a pure class (input: map of user → net balance; output: list of transfers).
- Entities are never exposed in the API. Use request/response DTOs (records).
- Authorisation checks live in services (e.g. `GroupAccessService.requireMember(groupId, userId)`), not scattered in controllers.
- Consistent error format (RFC 7807 `ProblemDetail`) via `@RestControllerAdvice`. Examples: 400 validation, 401 unauthenticated, 403 not a group member, 404 not found, 409 conflict (e.g. email taken, non-zero balance on leave).
- Use optimistic locking (`@Version`) on `expenses` to prevent lost updates.

---

## 6. Debt simplification algorithm

Goal: given net balances, produce a small set of transfers that settles everyone.

Greedy approach (min-cash-flow):
1. Compute `net[u]` for every member (section 4.3). Drop zeros.
2. Split into creditors (net > 0) and debtors (net < 0), each in a max-heap by absolute amount.
3. Repeat: take the largest debtor `d` and the largest creditor `c`; `t = min(|net[d]|, net[c])`; emit transfer `d → c : t`; reduce both; push back any non-zero remainder.
4. Stop when both heaps are empty.

Properties to document and test:
- Produces at most `n - 1` transfers for `n` members with non-zero balances.
- Total transferred equals the sum of positive balances.
- Everyone's net after applying the transfers is exactly 0.
- Deterministic (tie-break by user id) so tests are stable.
- Note in docs: the truly minimal number of transfers is NP-hard (subset-sum flavour); greedy is the standard practical choice. Mention this in the README as an interview talking point.

---

## 7. Security and data handling (Data Governance angle)

- Stateless JWT; secret from environment variable, never committed. Short expiry.
- Passwords: BCrypt strength 10–12; minimum length validation.
- Authorisation: a user can only access groups they belong to. Add tests for every endpoint proving a non-member gets 403/404.
- Rate-limit login attempts (simple in-memory limiter is enough for v1) — stretch.
- Personal data stored: email, display name, password hash, expense data. Document this in `docs/DATA_HANDLING.md`.
- **Account deletion:** anonymise the user (replace email/display name with placeholders, null out the hash) but keep expense rows so group balances stay correct. Document this trade-off.
- Data minimisation: store only what is needed; no analytics trackers.
- Logging: never log passwords, tokens or full emails. Use user ids in logs.
- Keep a `docs/DATA_DICTIONARY.md` (table, column, type, purpose, personal-data flag) and an ER diagram (`docs/er-diagram.png` or Mermaid in the README).

---

## 8. API design

Base path `/api`. JSON only. All endpoints except register/login require `Authorization: Bearer <jwt>`. Lists use `?page=&size=&sort=` and return a page envelope (`content`, `page`, `size`, `totalElements`).

### Auth and users
- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/users/me`
- `PATCH /api/users/me`
- `DELETE /api/users/me`

### Groups
- `POST /api/groups`
- `GET /api/groups` (groups the caller belongs to)
- `GET /api/groups/{groupId}`
- `PATCH /api/groups/{groupId}`
- `DELETE /api/groups/{groupId}`
- `POST /api/groups/{groupId}/members` (body: email)
- `DELETE /api/groups/{groupId}/members/{userId}`

### Expenses
- `POST /api/groups/{groupId}/expenses`
- `GET /api/groups/{groupId}/expenses` (filters, pagination)
- `GET /api/groups/{groupId}/expenses/{expenseId}`
- `PUT /api/groups/{groupId}/expenses/{expenseId}`
- `DELETE /api/groups/{groupId}/expenses/{expenseId}` (soft delete)

Create-expense request example:
```json
{
  "description": "Dinner",
  "amount": "90.00",
  "date": "2026-10-10",
  "paidBy": "<user-uuid>",
  "category": "FOOD",
  "splitType": "PERCENTAGE",
  "participants": [
    { "userId": "<uuid-a>", "percentage": "50.00" },
    { "userId": "<uuid-b>", "percentage": "30.00" },
    { "userId": "<uuid-c>", "percentage": "20.00" }
  ]
}
```
For `EQUAL`: `participants` has only `userId`. For `EXACT`: each has `amount`.

### Balances and settlements
- `GET /api/groups/{groupId}/balances` (net per member)
- `GET /api/groups/{groupId}/balances/simplified` (suggested transfers)
- `POST /api/groups/{groupId}/settlements`
- `GET /api/groups/{groupId}/settlements`
- `DELETE /api/groups/{groupId}/settlements/{settlementId}`
- `GET /api/users/me/balance` (overall across groups)

### Activity
- `GET /api/groups/{groupId}/activity`

Every endpoint must be documented in OpenAPI with request/response schemas and error responses (use springdoc annotations sparingly; DTO records and `@Operation` summaries are enough).

---

## 9. Testing strategy (a core portfolio feature)

Test pyramid:

1. **Unit tests (fast, no Spring):**
   - Each `SplitStrategy`: normal cases, remainder distribution, single participant, large amounts, invalid inputs (percentages not summing to 100, exact amounts not matching total, duplicates, negative values).
   - `DebtSimplifier`: empty, already settled, two people, three-person chain, circular debts, many members; property-style checks (sum of nets is 0, transfers ≤ n−1, applying transfers zeroes everyone). Consider jqwik for property-based tests (optional).
   - `Money` value object.
2. **Service tests (Mockito):** business rules, permissions, error paths.
3. **Integration tests (Spring Boot Test + Testcontainers PostgreSQL):** repositories, Flyway migrations apply cleanly, transactions, soft deletes, query filters.
4. **API tests (REST Assured or MockMvc):** full request → response flows per endpoint, auth required, non-member forbidden, validation errors return a ProblemDetail body.
5. **End-to-end scenario test:** register 3 users → create group → add expenses of each split type → record settlement → assert balances and simplified transfers.

Also maintain:
- `docs/TESTING.md`: strategy, how to run tests, coverage numbers, list of edge cases covered, and 2–3 sample bug reports (found-and-fixed examples, written in a QA style: steps, expected, actual, severity).
- A Postman collection exported to `postman/` for manual and exploratory testing.
- JaCoCo report uploaded as a CI artifact; coverage badge in the README.

---

## 10. CI/CD and deployment

- **GitHub Actions** `ci.yml`: on push and PR — set up Java 21, cache Maven, run `mvn verify` (unit + integration with Testcontainers), publish JaCoCo report. Badge in README.
- Optional second workflow: build Docker image and push to GitHub Container Registry on tags.
- **Docker:** multi-stage Dockerfile (build with Maven image, run on a slim JRE image, non-root user). `docker-compose.yml` runs the app and PostgreSQL with a healthcheck; `.env.example` documents the variables.
- **Config:** `application.yml` plus profiles (`local`, `test`, `prod`). Secrets from environment variables only (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`).
- **Azure deployment (late phase):** App Service F1 with the container or a JAR, a free external PostgreSQL (Neon/Supabase), configuration through App Service settings. Document the steps and the live URL in the README. Note: free tiers cold-start, so mention that in the README and check the demo before sharing it.
- Spring Actuator `health` endpoint exposed; everything else locked down.

---

## 11. Build order and acceptance criteria

Work in small vertical slices. Each phase ends with passing tests and a commit. Do not start a phase until the previous one is green.

**Phase 0 — Foundation**
- Project skeleton, Maven config, package structure, Flyway `V1`, Docker Compose, CI pipeline running an empty-but-green build, `Money` value object, error handling base.
- ✅ Done when: `docker compose up` starts the app and DB; CI is green; Swagger UI loads.

**Phase 1 — Auth and users**
- Register, login, JWT filter, `/users/me`.
- ✅ Done when: protected endpoints return 401 without a token; tests cover register/login success and failure.

**Phase 2 — Groups and members**
- Group CRUD, membership rules, access service.
- ✅ Done when: non-members cannot see a group; owner rules enforced; tests prove it.

**Phase 3 — Expenses and split strategies**
- Three split strategies, expense CRUD, soft delete, validation, activity log entries.
- ✅ Done when: shares always sum to the total; every strategy has thorough unit tests; filters and pagination work.

**Phase 4 — Balances, settlements, debt simplification**
- Balance derivation, settlements, `DebtSimplifier`, endpoints.
- ✅ Done when: nets sum to zero in all tests; simplified transfers settle everyone; end-to-end scenario test passes.

**Phase 5 — Hardening and docs**
- Error format polish, OpenAPI polish, JaCoCo gate (fail build below 75%), `README.md`, `TESTING.md`, `DATA_HANDLING.md`, `DATA_DICTIONARY.md`, ER diagram, Postman collection.
- ✅ Done when: a stranger can clone, run, and understand the project in 10 minutes.

**Phase 6 — Deployment**
- Azure deployment, live demo link in the README.

**Phase 7 — Portfolio extensions (separate branches, after the core is solid)**
- **UI:** small front end (React or similar) with login, groups, add expense, balances; accessibility checks (Lighthouse/axe) with scores in the README; Figma wireframes.
- **AI feature:** "ask about your expenses" natural-language endpoint, or a separate Python RAG service. Keep it isolated behind an interface so the core app does not depend on it.
- **Email notifications** via the `EmailSender` interface.
- **Refresh tokens**, `SHARES` split type.

**v2 ideas (not in scope now):** multi-currency groups with exchange rates, invitations for unknown emails, recurring expenses, receipt attachments.

---

## 12. Coding standards (instructions for the agent)

- Java 21 features welcome (records for DTOs, switch expressions, pattern matching) where they improve clarity.
- Constructor injection only. No field injection.
- Keep methods small and names intention-revealing. Prefer immutable objects.
- No business logic in controllers or entities beyond simple invariants.
- Every public service method that changes data is `@Transactional`.
- No `System.out.println`; use SLF4J.
- No TODOs left without an entry in `docs/DECISIONS.md` or a GitHub issue.
- Write the tests in the same change as the code. Do not ship a feature without tests.
- Commit messages: imperative, concise, one logical change per commit (e.g. `Add percentage split strategy with largest-remainder rounding`).
- Do not add dependencies beyond section 2 without noting why in `docs/DECISIONS.md`.
- After each phase, update the README (features implemented, how to run) and summarise what changed and what is next.

---

## 13. Definition of done for the whole project

- [ ] All Phase 0–5 acceptance criteria met; CI green with badge
- [ ] Coverage ≥ 80% on `service`, `expense` strategy and `balance` packages
- [ ] README with architecture diagram, run instructions, live demo link, and the debt simplification explanation
- [ ] OpenAPI docs complete and Swagger UI reachable
- [ ] `TESTING.md`, `DATA_HANDLING.md`, `DATA_DICTIONARY.md`, ER diagram present
- [ ] Postman collection included
- [ ] Deployed to Azure with a working demo (or clear instructions if deployment is paused)
