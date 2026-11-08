# CLAUDE.md — LoveProof

Guidance for Claude Code and other AI agents working in this repository.

> **Status: Phases 0–9 are complete and verified.** The backend and frontend projects, the Docker
> Compose database, the schema, the public REST API, the public submission form, the public
> testimonial wall and the complete admin moderation surface all exist, and the commands in §3 have
> been run successfully in this environment. Commands still marked `TODO` are ones that belong to a
> later phase and have therefore **not** been run yet — do not report them as working until you run
> them. Update this file as each phase lands: replace `TODO` markers with verified commands and add
> any command that did not exist when this file was written.

---

## 1. Project Overview

**LoveProof** is a self-hosted testimonial management application in the style of Testimonial.io.
Visitors submit a testimonial through a public form; an administrator moderates submissions in an
inbox; approved testimonials publish on a public wall.

| | |
|---|---|
| Frontend | React 18.3.1 · TypeScript 5.9.3 (strict) · Vite 6.4.5 · Tailwind CSS 3.4.19 |
| Backend | Java 21 · Spring Boot 3.5.16 · Spring Data JPA · Hibernate · Flyway · Actuator |
| Database | PostgreSQL 16 |
| Build | Maven (backend) · npm/Vite (frontend) |
| Tests | JUnit 5 + MockMvc + Testcontainers · Vitest + React Testing Library + MSW + axe |

Specification documents (these are the source of truth for scope):

- `docs/requirements.md` — functional/non-functional requirements, assumptions, out-of-scope list
- `docs/data-model.md` — entities, fields, relationships, constraints, ER diagram
- `docs/implementation-plan.md` — 11 build phases with acceptance criteria

---

## 2. Architecture

### 2.1 Layout

```
loveproof-testimonials/
├── backend/                  # Spring Boot + Maven (Java 21)          ✓ exists
├── frontend/                 # Vite + React + TS + Tailwind          ✓ exists
├── docs/                     # Requirements, data model, plan, API reference
├── screenshots/              # Captured UI screenshots
├── docker-compose.yml        # PostgreSQL 16 for local dev           ✓ exists
├── README.md                 # Setup + run instructions               ✓ exists
└── CLAUDE.md                 # This file
```

The frontend and backend are **separate projects with independent build lifecycles**. Do not merge
them into a single npm workspace or a single Maven module.

### 2.2 Backend package structure

Layered, strict dependency direction: `web` → `service` → `repository` → `domain`.

```
com.loveproof.testimonials
├── domain/        # ✓ Testimonial, AdminUser, ModerationAction + status/role/action enums
├── repository/    # ✓ TestimonialRepository, AdminUserRepository, ModerationActionRepository
├── service/       # ✓ TestimonialService (creation + moderation rules), FingerprintService,
│                  #   AdminSessionService (the acting administrator)
├── web/           # ✓ HealthController, TestimonialController, AdminTestimonialController,
│                  #   AdminSessionController, GlobalExceptionHandler, dto/ (records)
├── config/        # ✓ ClockConfig, SecurityConfig, Jackson, rate limiting
└── exception/     # ✓ NotFoundException, ConflictException, DuplicateTestimonialException
```

**Entities never leave the `service` layer.** Controllers return DTOs, never entities.

> **Current state (Phase 9).** The schema, the domain model, the public API, the moderation rules
> and the full admin security model are implemented and tested. Spring Security protects the admin
> surface: every `/api/admin/**` route requires an authenticated session with the `ADMIN` role, and
> every state-changing route requires a valid CSRF token. The `loveproof.admin.api.enabled` gate
> that existed during Phase 2 has been **removed** — the protection lives in the filter chain, not
> in a flag that could be flipped. See §6.3.

### 2.3 Frontend structure

```
src/
├── api/           # ✓ Typed fetch client, endpoint functions, error envelope types
│   ├── client.ts  # ✓ request wrapper, ApiError, CSRF header handling, public + admin methods
│   ├── types.ts   # ✓ Shared API types mirroring backend DTOs
│   └── index.ts   # ✓ Barrel
├── admin/         # ✓ The admin surface
│   ├── adminSession.ts      # ✓ The auth context object and its types
│   ├── AuthContext.tsx      # ✓ AuthProvider — restores the session on mount
│   ├── useAuth.ts           # ✓ The useAuth() hook
│   ├── RequireAdmin.tsx     # ✓ The route guard
│   ├── AdminLayout.tsx      # ✓ Header, nav, sign-out
│   ├── AdminLoginPage.tsx   # ✓ Sign-in form
│   ├── AdminDashboardPage.tsx  # ✓ Count tiles
│   └── AdminInboxPage.tsx   # ✓ Filter tabs, search, approve/reject/delete
├── components/    # ✓ Button, Card, Field, StarRating, ReadOnlyStars, ErrorBanner, PublicLayout
├── context/       # (Phase 7) AuthContext (admin session state)
├── hooks/         # (Phase 4+) useTestimonials, useSubmission, useAdminStats, …
├── pages/         # ✓ HomePage, SubmitTestimonialPage (+ submissionValidation.ts), TestimonialWallPage
├── test/          # ✓ setup.ts — Vitest + Testing Library bootstrap + axe matchers
├── App.tsx        # ✓ Router: /, /submit, /wall, /admin/login, /admin, /admin/inbox
├── main.tsx       # ✓ React entry point
└── index.css      # ✓ Tailwind entry point + base layer
```

The components live flat in `components/` rather than in nested `ui/` and `layout/` folders — the
count is small enough that the extra nesting would not pay for itself. Split into subfolders only
once a category has more than a handful of members.

**The admin surface lives in `admin/`, not in `components/` or `pages/`.** It is a separate
application area with its own layout, its own auth context and its own route guard; keeping it in one
directory makes that boundary explicit.

**`AuthContext.tsx` exports the provider only.** The context object and its types live in
`adminSession.ts` and the hook in `useAuth.ts`, because the `react-refresh` ESLint rule rejects a
module that exports both a component and a non-component.

State is **local hooks plus one context** (`AuthContext`). Do not introduce Redux, Zustand, or a
server-state library unless a specific phase calls for it.

### 2.4 Request flow

```
Browser
  └─ Vite dev proxy  /api/*  →  http://localhost:8080/api/*
       └─ Spring Security filter chain
            └─ CSRF filter  (cookie XSRF-TOKEN → header X-XSRF-TOKEN)
                 └─ Controller → Service → Repository → PostgreSQL
```

The frontend and backend are **same-origin** in development via the Vite proxy, so CORS is not
configured. Do not add a wildcard CORS entry.

---

## 3. Commands

> **Commands marked ✓ were run successfully in this environment during Phase 0.** Commands still
> marked `TODO` belong to a later phase and have **not** been run — verify them before relying on
> them, then replace the marker with the command that actually works.

### 3.1 Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java | 21 | `java -version` → 21.0.12 present ✓ |
| Maven | 3.8+ | `mvn -version` → 3.8.7 present ✓ |
| PostgreSQL | 16 | 16.15 present ✓; Docker 29.9.0 present ✓ |
| Node | 18.19.1 present ✓ | **Node 18 is EOL.** The frontend pins Node-18-compatible versions (Vite 6, Vitest 3, jsdom 26, Tailwind 3.4). `.nvmrc` recommends Node 20 LTS. |
| Docker | any recent | Required for Testcontainers (backend tests) and `docker-compose.yml`. |

### 3.2 Backend

```bash
# ✓ install dependencies and build
cd backend && mvn clean install

# ✓ compile
cd backend && mvn compile

# ✓ run the API on :8080
cd backend && mvn spring-boot:run

# ✓ run with the local profile
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local

# ✓ package a runnable jar
cd backend && mvn package
# -> backend/target/loveproof-testimonials-0.1.0-SNAPSHOT.jar

# TODO (Phase 3+) — apply migrations by hand; normally automatic on startup.
# The flyway-maven-plugin is not yet configured in backend/pom.xml, so this
# command does not work yet. Flyway runs automatically on application startup.
cd backend && mvn flyway:migrate
```

> **Port note:** ports 8080 and 8081 were already occupied by unrelated processes in this
> environment during Phase 0 verification. If `mvn spring-boot:run` reports "Port 8080 was already
> in use", either stop the other process or run on a free port:
> `mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8099`.

### 3.3 Frontend

```bash
# ✓ install dependencies
cd frontend && npm install

# ✓ dev server on :5173, proxying /api to :8080
cd frontend && npm run dev

# ✓ dev server proxying to a backend on a different port
cd frontend && VITE_PROXY_TARGET=http://localhost:8099 npm run dev

# ✓ production build
cd frontend && npm run build
# -> frontend/dist/

# ✓ preview the production build
cd frontend && npm run preview

# ✓ lint
cd frontend && npm run lint
```

### 3.4 Database

```bash
# ✓ start PostgreSQL 16 in Docker
docker compose up -d

# ✓ confirm the database is accepting connections
docker compose exec db pg_isready -U loveproof -d loveproof

# ✓ list tables
docker compose exec db psql -U loveproof -d loveproof -c '\dt'

# ✓ stop the database
docker compose down

# ✓ stop and delete the volume (destroys all data)
docker compose down -v
```

Flyway runs automatically on application startup. Hibernate `ddl-auto` is `validate` in **every**
profile — Flyway owns the schema, Hibernate may only check it.

### 3.5 Testing

```bash
# ✓ full backend verification (compile + test + package)
cd backend && mvn verify

# ✓ backend tests only
cd backend && mvn test

# ✓ a single backend test class
cd backend && mvn test -Dtest=HealthControllerTest

# ✓ the Phase 1-2 test classes
cd backend && mvn test -Dtest=MigrationTest,TestimonialApiTest,TestimonialServiceTest,AdminTestimonialApiTest

# ✓ the Phase 3 security test classes
cd backend && mvn test -Dtest=AdminAuthTest,AdminTestimonialApiTest,AdminSessionCookieTest,AdminSessionCookieProfileTest

# ✓ frontend test suite
cd frontend && npm test

# ✓ frontend tests in watch mode
cd frontend && npm run test:watch

# ✓ a single frontend test file
cd frontend && npm test -- SubmitTestimonialPage

# ✓ a single admin frontend test file
cd frontend && npm test -- AdminInboxPage

# ✓ the public wall's tests
cd frontend && npm test -- TestimonialWallPage

# ✓ everything, from the repository root
mvn -f backend/pom.xml verify && npm --prefix frontend test
```

Backend tests use **Testcontainers PostgreSQL 16**, so Docker must be running. A real Postgres
instance is used deliberately: H2's Postgres compatibility mode would not exercise the CHECK
constraints, partial indexes, or Flyway migrations faithfully.

**Accessibility checks.** `axe-core` and `vitest-axe` are dev dependencies. The matchers are
registered once in `src/test/setup.ts` via `vitest-axe/extend-expect`, so any test file can call
`axe(container)`. Every screen has such a check. The `color-contrast` rule is switched off in them
because jsdom has no 2D canvas for axe to measure with — a `<footer>` outside a sectioning element
is a `contentinfo` landmark, so a card per testimonial produced several indistinguishable
landmarks, and the axe check is what caught it.

### 3.6 Dependency security

```bash
# ✓ audit frontend dependencies
cd frontend && npm audit
```

At the end of Phase 0 the audit reports **4 moderate, 0 high, 0 critical**. The two remaining
advisories (`vitest` / `@vitest/mocker` and `react-router-dom`) have **no Node 18-compatible fix**:
Vitest 4+ requires Node 20, and `react-router` 7+ requires Node 20. They are dev-time and routing
dependencies respectively, and are expected to clear when Node 20 is adopted. `npm audit` was
reduced from 19 findings (including 2 critical) to 4 via pinned versions and the `overrides` block
in `frontend/package.json` (`tinypool`, `minimatch`).

---

## 4. Database Setup

Three tables, defined in `docs/data-model.md`:

| Table | Purpose |
|---|---|
| `testimonial` | Every submission plus its moderation state and soft-delete flag |
| `admin_user` | Accounts permitted to moderate (one seeded in v1) |
| `moderation_action` | Append-only audit log of every moderation decision |

Rules that must hold in code, not just in the docs:

- `status` ∈ `{PENDING, APPROVED, REJECTED}` and `deleted_at IS NULL` are **orthogonal**. A soft
  deletion does not rewrite the moderation status.
- `rating` is an integer 1–5. No half-stars.
- `consent_given` is always `TRUE` — a row can never be edited back to `false`.
- Public reads filter on `status = 'APPROVED' AND deleted_at IS NULL`. This predicate is not
  optional, and it is mirrored by the partial index `idx_testimonial_live_approved`.
- Dashboard counts are computed with `COUNT(*) FILTER (...)` at request time. There is no counter
  table and no cached aggregate.
- Timestamps are `timestamptz` in UTC; rendering happens in the browser.

Migration rules:

- Versioned Flyway files live in `backend/src/main/resources/db/migration`.
- **Never edit a migration that has been merged.** Add a new numbered migration. Flyway validates
  the checksum of every applied migration on boot, so even a comment edit breaks the next start.
- The admin seed lives in `V2__seed_admin_user.sql` and reads the password hash from configuration —
  never hard-code a hash in the repository.
- `V2` seeds with `ON CONFLICT DO NOTHING`, so it never corrects an existing row. When the
  configured hash changes, `V3__resync_admin_user.sql` resynchronises the seeded administrator.
- A BCrypt hash contains `$` characters, which Flyway reads as the start of a placeholder. Write
  them as `${dollar}` in the migration and declare `dollar: '$'` under `spring.flyway.placeholders`.
  The same escaping applies to the hash value in `application.yml` and `application-test.yml`.
- **Never put a `${...}` default inside a YAML default value.** Spring resolves those, and Spring
  does not know about the `dollar` placeholder — the literal text `${dollar}` reaches the database.
  Read the value from a `loveproof.admin.*` property instead.

---

## 5. Coding Conventions

### 5.1 General

- Formatter: 4-space indent, UTF-8, LF line endings, final newline on every file.
- Line length: 120 characters (Java, enforced by the formatter plugin; TS via the linter).
- Prefer explicitness over cleverness. No reflection tricks, no dynamic dispatch to dodge a type.
- No `TODO` comments left in committed code. No commented-out blocks.
- Do not add dependencies without a stated need recorded in the PR description.

### 5.2 Backend (Java)

- Java 21. Records for DTOs. Entities use explicit accessors — **no Lombok**.
- Jakarta Bean Validation (`@NotBlank`, `@Size`, `@Min`, `@Max`, `@AssertTrue`) on request DTOs. The
  API is authoritative; the client mirrors it for fast feedback only.
- Layered package structure per §2.2. Services own transactions (`@Transactional`).
- Repositories use Spring Data JPA derived queries or `@Query`. **No string-concatenated SQL** and
  no `String.format` in queries — parameter binding only.
- **Never write `:param IS NULL` in a JPQL query.** Hibernate 6 cannot infer a JDBC type for a
  parameter that is only ever compared to `NULL`: it binds it as `bytea`, and PostgreSQL rejects the
  query with "function lower(bytea) does not exist". Express an optional filter as an explicit
  boolean flag instead — `(:hasStatus = false OR t.status = :status)` — and bind the optional value
  as a never-null string. This bit the moderation inbox and the public wall's rating filter.
- Every controller returns a DTO. Never return an entity, and never return `ResponseEntity` with a
  `Map` body.
- Errors are thrown as typed exceptions and rendered by one `@RestControllerAdvice` into the shared
  error envelope (§6.2). Controllers do not build error responses by hand.
- Logging via SLF4J. **Never log testimonial content, passwords, session identifiers, or raw IP
  addresses.** Log IDs and counts, not payloads.
- **MockMvc does not write session cookies.** The servlet container — not the framework — sets
  `Set-Cookie`, so a MockMvc test can never assert on the session cookie's attributes. Test cookie
  behaviour against real embedded Tomcat (`@SpringBootTest(webEnvironment = RANDOM_PORT)` +
  `TestRestTemplate`); see `AdminSessionCookieTest`.

### 5.3 Frontend (TypeScript / React)

- `strict: true` in `tsconfig.json`, including `noImplicitAny` and `noUnusedLocals`. No `any`, no
  `@ts-ignore`, no non-null assertions on values that can legitimately be null.
- **Function components with hooks only.** No class components.
- Colocate a component's styles with Tailwind utility classes in the JSX. Do not add CSS modules or
  inline `style` objects for layout; use the design tokens in `tailwind.config.ts`.
- Derived state is computed, not stored in `useState` + `useEffect`. If a value can be derived during
  render, derive it during render.
- Every network call goes through the typed API client in `src/api/`. Components do not call `fetch`
  directly.
- Effects that fetch data must handle the error and the loading state; a rejected promise must never
  escape into an unhandled rejection.
- Tailwind class order follows the plugin's canonical order; run the formatter before committing.

### 5.4 Accessibility

Accessibility is a build requirement, not a follow-up. In new code:

- Semantic HTML first; ARIA only where semantics fall short.
- Every input has a `<label>` associated via `htmlFor`/`id`. Placeholder is never the only label.
- Validation errors are linked with `aria-describedby`, `aria-invalid` is set on the failing input,
  and a summary is announced via `aria-live="polite"`.
- The 1–5 rating is an accessible radio group, arrow-key operable, with a text alternative
  ("4 out of 5"). Rating is never conveyed by colour alone.
- The skip-to-content link is the first focusable element on every page.
- Never remove focus outlines. Modal dialogs trap focus and close on `Escape`.
- Contrast meets 4.5:1 for text and 3:1 for large text and UI boundaries.
- Layouts work at 320 px without horizontal scrolling; respect `prefers-reduced-motion`.

---

## 6. API Conventions

### 6.1 General

- Base path `/api`. Public endpoints sit at `/api/testimonials`; admin endpoints at `/api/admin/**`.
- JSON request and response bodies, `Content-Type: application/json`.
- Dates are ISO-8601 UTC strings (`2026-11-03T10:15:30Z`).
- Pagination is `page` (0-based) and `size`, with `total`, `totalPages`, and `page` echoed in the
  response.
- Casing: **camelCase** in JSON (Java records) and `snake_case` in SQL columns.
- `GET` reads, `POST` creates or triggers an action, `DELETE` soft-deletes. Admin moderation uses
  explicit action endpoints (`/approve`, `/reject`, `/restore`) rather than a `PATCH` with a status
  field, so each action is independently permission-checkable and auditable.
- **Every endpoint is classified** as public, authenticated, or admin-only before it is written, and
  admin-only endpoints are enforced by Spring Security, not by controller logic.

### 6.2 Error envelope

Every error response uses the same shape:

```json
{
  "code": "VALIDATION_FAILED",
  "message": "The submitted testimonial is not valid.",
  "fieldErrors": { "rating": "Rating must be between 1 and 5." },
  "timestamp": "2026-11-03T10:15:30Z",
  "path": "/api/testimonials"
}
```

| Status | `code` | When |
|---|---|---|
| `400` | `VALIDATION_FAILED` | A field failed validation |
| `401` | `AUTHENTICATION_FAILED` | The login was rejected — wrong username or password |
| `401` | `UNAUTHENTICATED` | No session, or the session is no longer valid |
| `403` | `FORBIDDEN` | Authenticated but not permitted, or missing/invalid CSRF token |
| `404` | `NOT_FOUND` | Not found |
| `409` | `DUPLICATE_SUBMISSION` | Duplicate submission |
| `409` | `CONFLICT` | Acting on an already-deleted record, or restoring a live one |
| `429` | `RATE_LIMITED` | Rate limited — always with a `Retry-After` header |
| `500` | `INTERNAL_ERROR` | Unexpected — logged in full server-side, generic message to the client |

### 6.3 Security model

> **Implemented in Phase 3.** `SecurityConfig` is the single place the rules live. Controllers never
> check a role themselves.

- Session cookie: `HttpOnly`, `SameSite=Lax`, `Secure` under the `prod` profile. The session ID is
  regenerated on login.
- **CSRF is enabled on every state-changing request.** The token is issued in a readable
  `XSRF-TOKEN` cookie and echoed in the `X-XSRF-TOKEN` header. The frontend API client does this
  automatically; never hand-roll a fetch that skips it.
- Passwords are BCrypt hashes (cost ≥ 10) only. The administrator is seeded into the `admin_user`
  table, never into application configuration.
- No admin endpoint may be reachable without both an authenticated session and the admin role.
- The public API exposes approved, non-deleted testimonials only — including in counts, error
  messages, and pagination totals.
- **Two endpoints are CSRF-exempt**, and only two: `POST /api/admin/session` (the browser has no
  token yet; the session-id regeneration is what protects it) and `POST /api/testimonials` (it is
  anonymous, so there are no credentials to ride on; rate limiting and the duplicate fingerprint
  guard it instead). Every other write needs the token.
- **Logout must expire the cookie, not just invalidate the session.** A cookie is only replaced when
  the name, path and domain match, so the logout response sets an expired `SESSION` cookie with the
  same attributes.
- **401 versus 403.** The CSRF filter runs before authorization, so the access-denied handler
  inspects the authentication state: anonymous → `401`, authenticated → `403`.

> **Do not reintroduce a configuration gate on the admin surface.** The `loveproof.admin.api.enabled`
> property existed during Phase 2 as a placeholder for this protection. It has been removed. A flag
> that could turn the admin surface *off* was one accidental property away from turning it *on*
> without authentication.

### 6.4 Endpoint surface

Public:

```
GET    /api/health                        # ✓ Phase 0
POST   /api/testimonials                  # ✓ Phase 2
GET    /api/testimonials?page=&size=&rating=&sort=   # ✓ Phase 2
GET    /api/testimonials/stats            # ✓ Phase 2
```

Admin (session + admin role + CSRF on writes):

```
POST   /api/admin/session                # ✓ Phase 3 — exempt from CSRF
DELETE /api/admin/session                # ✓ Phase 3
GET    /api/admin/session                # ✓ Phase 3
GET    /api/admin/testimonials/stats      # ✓ Phase 3
GET    /api/admin/testimonials?status=&q=&page=&size=   # ✓ Phase 3
GET    /api/admin/testimonials/deleted?page=&size=     # ✓ Phase 3
GET    /api/admin/testimonials/{id}                     # ✓ Phase 3
GET    /api/admin/testimonials/{id}/audit              # ✓ Phase 3
POST   /api/admin/testimonials/{id}/approve             # ✓ Phase 3
POST   /api/admin/testimonials/{id}/reject              # ✓ Phase 3
DELETE /api/admin/testimonials/{id}                     # ✓ Phase 3
POST   /api/admin/testimonials/{id}/restore             # ✓ Phase 3
```

> **The moderation endpoints are protected, not gated.** Every route above requires an
> authenticated session with the `ADMIN` role; every write additionally requires the CSRF token.
> There is no property that disables them.

---

## 7. Git Rules

- Branch from `main` with a descriptive name: `feature/…`, `fix/…`, `docs/…`. Do not commit
  directly to `main`.
- **Review `git diff` before every commit.** Read the diff in full — you are responsible for every
  line you commit, including changes made by tools.
- Stage files explicitly by name (`git add path/to/file`). Never `git add -A` or `git add .` — they
  sweep in `.env`, build output, and unrelated edits.
- One commit per coherent change. The message states **why**, not a restatement of the diff.
- Commit trailers: `Co-Authored-By: puku-ai-2.8 <noreply@puku.sh>`
- Never commit: secrets, `.env`, `application-local.yml` with real credentials, `node_modules/`,
  `target/`, `dist/`, IDE settings, or OS noise.
- Never force-push to `main`/`master`. Never use `git reset --hard` or `git checkout --` to discard
  work that was not explicitly asked to be discarded.
- Never amend a pushed commit.

---

## 8. Workflow: Plan → Implement → Verify

**Every feature follows these three phases. This is required, not advisory.**

### 8.1 Plan

Before writing code:

1. Restate the requirement and its acceptance criteria in one or two sentences.
2. Identify the files to create or change, and the layers involved (schema, service, API, UI).
3. Name the tests that will prove it works.
4. If the change is not trivially reversible — a schema change, a public API contract change, a
   dependency addition — state the migration or compatibility approach explicitly.
5. For anything beyond a small, well-understood change, get the plan approved before implementing.

### 8.2 Implement

- Implement in the smallest change that satisfies the acceptance criteria. Match the conventions in
  §5; do not invent new patterns.
- Write or update the tests **in the same phase** as the code, not afterwards. A feature without a
  test is not finished.
- Keep the change focused. Unrelated refactoring belongs in its own commit.

### 8.3 Verify

Run the real commands and **read the output**:

```bash
# ✓ backend
cd backend && mvn verify

# ✓ frontend
cd frontend && npm test
```

Then check, in this order:

- [ ] The build compiles with no errors.
- [ ] The test suite passes with **zero failures and zero skipped tests**.
- [ ] The specific acceptance criteria for this change are each covered by a passing test.
- [ ] No new console warnings, no unhandled promise rejections, no React key or a11y warnings.
- [ ] `git diff` reviewed and free of debug statements, stray comments, and unrelated edits.

---

## 9. Honesty Rules

These are hard rules. Violating them is worse than an incomplete feature.

### 9.1 Never claim tests passed unless they were actually run

- Only state that a test passed if you ran it **in this session** and saw it pass.
- Report the actual command and the actual result. Quote the summary line.
- If a test was skipped, errored, timed out, or could not run, say so explicitly — never present it
  as passing.
- Never write "tests should pass" or "this looks correct" as a substitute for running them.
- If you are unsure whether a command was run, assume you did not run it.

### 9.2 Tests are required for bug fixes

- Every bug fix ships with a **regression test that fails before the fix and passes after it**.
- If you cannot write such a test, that is a signal the root cause was not understood — investigate
  further rather than skipping the test.
- State explicitly which test demonstrates the fix.

### 9.3 No hard-coded secrets

- No passwords, API keys, tokens, connection strings with credentials, or private URLs in source
  code, configuration committed to the repository, tests, or documentation.
- Secrets come from environment variables with local defaults that are clearly non-production.
- Test fixtures use obviously fake values.
- If a secret is ever committed, treat it as compromised: rotate it, then remove it from the history.

### 9.4 Documentation is part of the change

- Update `README.md` whenever setup, commands, configuration, or behaviour change.
- Update `docs/` — requirements, data model, implementation plan, API reference — whenever behaviour
  changes. The plan's phase checkboxes are updated as phases complete.
- Add a Flyway migration **and** update `docs/data-model.md` for any schema change.
- Update this `CLAUDE.md` when a command, convention, or endpoint changes — and remove the `TODO`
  markers once a command has been verified to work.

---

## 10. Scope Discipline

The scope is fixed in `docs/requirements.md` §6 (out of scope) and §5 (assumptions). Work within it.

- Do not add multi-tenancy, user registration, OAuth, email notifications, import/export, image
  uploads, dark mode, or a search engine.
- Do not introduce a state-management library, a second test framework, or a new persistence layer.
- If a requirement seems to need something out of scope, raise it and propose an alternative rather
  than silently building it.

---

## 11. Skill Authoring

Skill files must be written to one of these paths only:

- **Project-level:** `.puku-cli/skills/<skill-name>/SKILL.md`
- **User/global:** `~/.puku-cli/skills/<skill-name>/SKILL.md`

Do **not** write `SKILL.md` to `.claude/skills/` or `~/.claude/skills/`. If a `CLAUDE.md` elsewhere
in this repo points to a different skill path, that guidance applies to Claude Code only, not to
`puku-cli`.
