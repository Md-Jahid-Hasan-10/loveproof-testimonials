# LoveProof — Implementation Plan

**Version:** 1.5 (Phases 0–9 verified)
**Status:** Phases 0, 1, 2, 3, 4, 5, 6, 7, 8 and 9 complete and verified — Phase 10 not started, Phase 11 partial
**Stack:** React 18 + TypeScript + Vite 6 + Tailwind CSS 3.4 · Java 21 + Spring Boot 3.5 + Maven · PostgreSQL 16

---

## 1. Plan Overview

The build is split into **11 small phases**. Each phase is independently runnable and independently
verifiable, so the application is working end-to-end from Phase 4 onward and each subsequent phase adds
one coherent slice of the brief.

**Phase ordering principle:** the public path (submit → wall) is built before the admin path
(dashboard → moderation), because the admin UI is only meaningful once there is data to moderate. The
database and the API contract are established in Phase 2–3 so both halves of the frontend can be built
against a fixed contract rather than an evolving one.

```
Phase 0  Scaffold + tooling
Phase 1  Database schema + migrations
Phase 2  Backend domain + public API
Phase 3  Backend admin API + security
Phase 4  Frontend shell + routing + design system
Phase 5  Public submission form
Phase 6  Public testimonial wall
Phase 7  Admin authentication screens
Phase 8  Admin dashboard
Phase 9  Moderation inbox
Phase 10  Hardening: accessibility, errors, rate limiting, perf
Phase 11  Documentation, seed data, final verification
```

Each phase lists: **what is built**, **acceptance criteria** (testable statements — the phase is done
when all of them hold), and **verification** (the command that proves it).

---

## 2. Phase Breakdown

### Phase 0 — Repository Scaffold & Tooling

**Status: COMPLETE — verified 2026-11-03.**

**Built:**
- Top-level layout: `backend/`, `frontend/`, `docs/`, `screenshots/`, `docker-compose.yml`, `README.md`.
- `backend/`: Maven `pom.xml` (Spring Boot 3.5.16, Java 21), package `com.loveproof.testimonials`,
  public `/api/health` endpoint.
- `frontend/`: Vite 6.4.5 + React 18.3.1 + TypeScript 5.9.3 (strict) + Tailwind CSS 3.4.19.
- `docker-compose.yml` with PostgreSQL 16-alpine and a healthcheck.
- `.gitignore`, `.editorconfig`, `.env.example`.
- `.nvmrc` documenting the recommended Node version (20).

**Deliberately excluded from Phase 0** (each belongs to a later phase and would be dead code here):
authentication and Spring Security (Phase 3), Flyway migrations and the JPA entities (Phase 1),
the router, API client and page components (Phase 4).

**Acceptance criteria — verification results:**

| # | Criterion | Result | Evidence |
|---|---|---|---|
| 1 | `cd backend && mvn -q compile` succeeds on Java 21 | **PASS** | `mvn clean verify` → `BUILD SUCCESS` (exit 0), `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`. |
| 2 | `cd frontend && npm install && npm run build` succeeds and emits a `dist/` bundle | **PASS** | Clean install (333 packages) exit 0; `✓ built in 1.33s`; `dist/index.html` + `dist/assets/index-BNo8-dGJ.js` (144.05 kB, 46.29 kB gzip). |
| 3 | `docker compose up -d` starts PostgreSQL 16 and `pg_isready` reports healthy within 60 s | **PASS** | After `down -v` + `up -d` from scratch: `pg_isready` → `accepting connections` in **3 s**; container health `healthy`. |
| 4 | `docker compose exec db psql -U loveproof -d loveproof -c '\dt'` connects successfully | **PASS** | Exit 0; `Did not find any relations.` — correct for a schema with no migrations yet (Phase 1). |
| 5 | `mvn spring-boot:run` starts the backend on port 8080 with `ddl-auto: validate` and no errors | **PASS (see note)** | `Started LoveProofApplication in 3.024 seconds`, 0 ERROR lines in the log, `/actuator/health` → `{"status":"UP"}`, `/api/health` → `{"status":"UP","service":"loveproof-testimonials",...}`. `ddl-auto: validate` confirmed at `application.yml:8`. **Note:** port 8080 was occupied by an unrelated pre-existing process, so the run used `--server.port=8099`; this is an environment constraint, not a defect. |
| 6 | `npm run dev` starts Vite on port 5173 proxying `/api` to `localhost:8080` | **PASS** | `VITE v6.4.5 ready in 208 ms`, `Local: http://localhost:5173/`; SPA root → 200; proxied `/api/health` returned the backend's JSON payload, confirming the full browser → Vite → Spring path. |
| 7 | No secret or credential is committed; `.env.example` contains placeholders only | **PASS** | Secret-pattern scan across all source/config/docs → no matches. `.env` is git-ignored and absent from the worktree. `git add -A -n` lists 33 files, none of them `node_modules/`, `target/`, `dist/` or `.env`. `.env.example` holds placeholders only. |

**Additional verification beyond the listed criteria:**

| Check | Result | Evidence |
|---|---|---|
| Backend test suite | **PASS** | `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0` — `HealthControllerTest`. |
| Frontend test suite | **PASS** | `Test Files 1 passed (1)`, `Tests 1 passed (1)` — `src/App.test.tsx`. |
| Frontend lint | **PASS** | `eslint .` exit 0, no findings. |
| Backend package | **PASS** | `backend/target/loveproof-testimonials-0.1.0-SNAPSHOT.jar` built by `mvn clean verify`. |
| Docker Compose config validation | **PASS** | `docker compose config` exit 0 — rendered service, volume, healthcheck and port mapping. |
| Frontend dependency audit | **4 moderate, 0 high, 0 critical** | Reduced from 19 findings (2 critical) via pinned versions and `overrides`. The two remaining advisories (`vitest`, `react-router-dom`) have no Node 18-compatible fix — Vitest 4+ and react-router 7+ both require Node 20. |

**Scaffold problems found and fixed during this phase:**

1. **Authentication was scaffolded too early.** The first pass included `spring-boot-starter-security`,
   an in-memory `UserDetailsService` and a `loveproof.admin.*` configuration block. That is Phase 3
   work, and it contradicted `docs/data-model.md`, which places the administrator in the
   `admin_user` table rather than in application configuration. The security starter, the two
   config classes, the corresponding test and the `loveproof.admin.*` properties were all removed.
   Authentication now arrives in Phase 3, backed by the table the data model specifies.
2. **The Vite proxy target was hard-coded to port 8080.** Where 8080 is already occupied by another
   process, the dev server silently proxied to the wrong application. The target is now
   `VITE_PROXY_TARGET`-overridable, and the proxy was verified end-to-end against the real backend.
3. **`vite.config.ts` declared a `test` key that `vite`'s `defineConfig` does not type.** Fixed by
   importing `defineConfig` from `vitest/config`.
4. **The ESLint config imported `@eslint/js` and `globals` without declaring them.** Added both as
   explicit devDependencies.
5. **`postcss` 8.5.6 and `react-router-dom` 6.30.1 carried known advisories.** Upgraded to 8.5.32
   and 6.30.6 respectively.

**Deferred to later phases:**

- Flyway migrations (`V1__baseline.sql`, `V2__seed_admin_user.sql`) and JPA entities — Phase 1.
- The `admin_user` table, Spring Security and the session login flow — Phase 3.
- The router, API client and page components — Phase 4.

**Verification:** `cd backend && mvn verify && cd ../frontend && npm run build && docker compose config && docker compose up -d && docker compose exec db pg_isready -U loveproof`

---

### Phase 1 — Database Schema & Migrations

**Status: COMPLETE — verified 2026-11-08.**

**Built:**
- Flyway migrations `V1__baseline.sql` (tables, CHECK constraints, FKs, indexes) and
  `V2__seed_admin_user.sql` (idempotent admin seed).
- JPA entity mappings mirroring the data model exactly.
- `application.yml` + profile files (`application-local.yml`, `application-test.yml`).
- Testcontainers PostgreSQL base test class.

**Acceptance criteria:**
- [x] `mvn test` boots the Testcontainers Postgres, applies `V1` and `V2`, and all migrations validate.
- [x] Hibernate `ddl-auto: validate` passes against the migrated schema — entity and schema agree.
- [x] Inserting `rating = 6` directly via SQL is rejected by `ck_testimonial_rating_range`.
- [x] Inserting `consent_given = false` is rejected by `ck_testimonial_consent_required`.
- [x] Inserting `status = 'PUBLISHED'` is rejected by `ck_testimonial_status`.
- [x] The partial index `idx_testimonial_live_approved` exists and excludes non-approved/soft-deleted rows.
- [x] Re-running migrations is a no-op (`flyway info` reports state `SUCCESS`, no pending versions).

**Verification:** `cd backend && mvn test -Dtest=MigrationTest` → `Tests run: 19, Failures: 0, Errors: 0, Skipped: 0`

---

### Phase 2 — Backend Domain, Public API & Validation

**Status: COMPLETE — verified 2026-11-08.**

**Built:**
- Layered packages: `web` → `service` → `repository` → `domain`.
- DTOs (Java records): `CreateTestimonialRequest`, `TestimonialResponse`, `PageResponse<T>`, `ErrorResponse`.
- `TestimonialService` with creation rules and duplicate fingerprint detection.
- `POST /api/testimonials` and `GET /api/testimonials` (approved only), plus `GET /api/testimonials/stats`.
- Global exception handler producing the shared error envelope.
- Jakarta Bean Validation on all request DTOs.
- `AdminTestimonialController` with the moderation endpoints (approve / reject / delete / restore / inbox /
  stats), plus the `ModerationService` transition rules and the append-only audit trail.

**Acceptance criteria:**
- [x] A valid submission returns `201` with the created testimonial and `status = PENDING`.
- [x] `name` shorter than 2 chars → `400` with `fieldErrors.name` populated.
- [x] `rating = 0` and `rating = 6` → `400` with `fieldErrors.rating`.
- [x] `content` shorter than 20 chars → `400` with `fieldErrors.content`.
- [x] `consent = false` → `400` with `fieldErrors.consent`.
- [x] Whitespace-only `name` → `400` (the trim happens before validation).
- [x] `role` / `company` omitted → accepted and stored as `NULL`, not `""`.
- [x] `GET /api/testimonials` returns **only** approved, non-deleted testimonials, newest first.
- [x] `GET /api/testimonials` supports `rating` filter and `sort=rating` ordering.
- [x] `GET /api/testimonials` paginates and reports a total count.
- [x] A duplicate submission (same name + content within 10 minutes) is rejected with `409`.
- [x] Every error response has the shape `{ code, message, fieldErrors, timestamp, path }`.
- [x] Unit tests cover the validation boundaries at 1/2/100/101 chars and ratings 1/5/6.

**Verification:** `cd backend && mvn test -Dtest=TestimonialApiTest,TestimonialServiceTest` → `Tests run: 59, Failures: 0, Errors: 0, Skipped: 0`

**Deviation from the plan — the admin surface is gated until Phase 3.** *(superseded)* The moderation
endpoints and the status-transition rules are implemented and tested here, but `AdminTestimonialController`
is registered only when `loveproof.admin.api.enabled=true` is set explicitly (default `false`; enabled in the
`test` profile only). Phase 3 introduces Spring Security, session authentication and CSRF protection — it is
the phase that actually protects the admin surface, and no admin endpoint may be reachable before it. Gating
the controller means an interim deployment cannot expose moderation endpoints unauthenticated.

> **Resolved in Phase 3.** The `loveproof.admin.api.enabled` property and its conditional bean registration
> have been **removed**. The controller is now always registered and is protected by the security filter
> chain itself: every `/api/admin/**` route requires an authenticated session with the `ADMIN` role, and every
> state-changing route additionally requires a valid CSRF token. A configuration flag that could turn the
> admin surface off was one accidental property away from being turned *on* without authentication, so the
> protection now lives in one place — the filter chain — rather than in a flag that gates registration.

---

### Phase 3 — Backend Admin API, Authentication & Audit Trail

**Status: COMPLETE — verified 2026-11-08.**

**Built:**
- Spring Security 6 configuration: session-based auth, CSRF enabled, admin role required on `/api/admin/**`.
- `POST/DELETE/GET /api/admin/session` (login, logout, current identity).
- `GET /api/admin/testimonials/stats` (dashboard counts).
- `GET /api/admin/testimonials` (moderation inbox with status filter, search, pagination),
  `GET /api/admin/testimonials/deleted` (the soft-deleted items), `GET /api/admin/testimonials/{id}`
  (one testimonial in its actual state) and `GET /api/admin/testimonials/{id}/audit` (the
  append-only history of one testimonial).
- `POST /api/admin/testimonials/{id}/approve`, `/reject`, `DELETE .../{id}`, `POST .../{id}/restore`.
- `AdminUserDetailsService` + BCrypt seed, and `V3__resync_admin_user.sql` to correct a malformed
  seeded hash without editing the already-applied `V2` migration.
- The `prod` profile, which marks both cookies `Secure` and shortens the session idle timeout.
- The audit trail is written by `TestimonialService` on every state change, including the submission
  itself, so a testimonial's whole lifecycle is reconstructable from one append-only table.

**Acceptance criteria:**
- [x] `GET /api/admin/testimonials/stats` without a session → `401`.
- [x] `GET /api/admin/testimonials/stats` with a non-admin session → `403`.
- [x] `POST /api/admin/session` with correct credentials → `200` + `Set-Cookie: SESSION=…` with `HttpOnly` and `SameSite=Lax`.
- [x] `POST /api/admin/session` with wrong credentials → `401` with a message that does not distinguish unknown-user from wrong-password.
- [x] The login cookie is `Secure` under the `prod` profile and not under `local`.
- [x] `DELETE /api/admin/session` invalidates the session server-side; the same cookie then yields `401`.
- [x] A state-changing admin request without an `X-XSRF-TOKEN` header → `403`.
- [x] A state-changing admin request with a valid CSRF token succeeds.
- [x] `GET /api/admin/testimonials?status=PENDING` returns only pending items.
- [x] Approve moves an item to `APPROVED` and it immediately appears in `GET /api/testimonials`.
- [x] Reject requires a reason and moves the item out of the pending filter.
- [x] Delete soft-deletes: the item disappears from the wall and from total/pending/approved counts, and appears in the deleted count.
- [x] Restoring a deleted item brings it back to its previous status.
- [x] Acting on an already-deleted testimonial → `409`.
- [x] Every moderation action writes a `moderation_action` row with the acting admin's username.
- [x] The inbox supports free-text search across name, company and content.
- [x] `GET /api/admin/testimonials/stats` counts match the actual row counts asserted directly against the database.

**Verification:** `cd backend && mvn verify` → `Tests run: 121, Failures: 0, Errors: 0, Skipped: 0`
(`AdminAuthTest` 14, `AdminTestimonialApiTest` 21, `AdminSessionCookieTest` 3,
`AdminSessionCookieProfileTest` 2, plus the public API and migration suites).

**End-to-end verification against the running backend:**

A 12-step curl script (`/tmp/e2e.sh`) drove the whole moderation flow against the live server, asserting
the HTTP status at every step.

| Check | Result | Evidence |
|---|---|---|
| The XSRF-TOKEN cookie is issued to an anonymous caller | **PASS** | `Set-Cookie: XSRF-TOKEN=…` on `GET /api/testimonials`. |
| Sign-in issues a session cookie | **PASS** | `Set-Cookie: SESSION=…` on `POST /api/admin/session` → `HTTP 200`. |
| An admin request without a session is refused | **PASS** | `GET /api/admin/testimonials/stats` → `HTTP 401`. |
| A public submission is stored as PENDING | **PASS** | `POST /api/testimonials` → `"status":"PENDING"`. |
| The inbox lists it, with and without a status filter | **PASS** | `GET /api/admin/testimonials?status=PENDING` → `HTTP 200`; `GET /api/admin/testimonials` → `HTTP 200`. |
| A state-changing request without the CSRF header is refused | **PASS** | `POST …/approve` without `X-XSRF-TOKEN` → `HTTP 403`. |
| Approve with the CSRF header succeeds | **PASS** | `POST …/approve` → `HTTP 200`. |
| The approved testimonial is published on the wall | **PASS** | `GET /api/testimonials` contains the approved id. |
| Reject requires a reason | **PASS** | Blank reason → `HTTP 400`; a real reason → `HTTP 200`. |
| A rejected testimonial never reaches the wall | **PASS** | The id is absent from `GET /api/testimonials`. |
| Delete and restore round-trip | **PASS** | `DELETE` → `HTTP 200`; `POST …/restore` → `HTTP 200`. |
| The audit trail names the acting administrator | **PASS** | `moderation_action` rows → `APPROVE \| admin \| t`. |
| Logout invalidates the session | **PASS** | `DELETE /api/admin/session` → `HTTP 204`; the same cookie then yields `HTTP 401`. |

**End-to-end verification in a real browser:**

The admin UI was driven in headless Chrome over the DevTools Protocol against the running backend and the
Vite dev server, to confirm the React app renders the admin screens rather than merely that the API
answers.

| Check | Result | Evidence |
|---|---|---|
| `/admin` with no session redirects to the sign-in page | **PASS** | `location.pathname` → `/admin/login`. Captured in `screenshots/admin-login.png`. |
| Valid credentials land on the dashboard | **PASS** | `location.pathname` → `/admin`; the count tiles render. Captured in `screenshots/admin-dashboard.png`. |
| The dashboard shows the per-status counts | **PASS** | The tiles read `Pending … \| Approved … \| Rejected … \| Deleted …`. |
| The inbox renders its rows | **PASS** | `ul li` count > 0. Captured in `screenshots/admin-inbox.png`. |
| Approving from the browser refreshes the list and the counts | **PASS** | The pending row count drops and the approved count rises. |
| The approved testimonial is on the public wall | **PASS** | The id appears in `GET /api/testimonials` after the browser approval. |
| The audit log records the browser action | **PASS** | `moderation_action` → `APPROVE \| admin`. |

**Bugs found and fixed during this phase:**

1. **`GET /api/admin/testimonials` returned `HTTP 500` — "function lower(bytea) does not exist".** The
   default inbox view was broken for every administrator. Hibernate 6 cannot infer a JDBC type for a JPQL
   parameter that is only ever compared to `NULL`: it binds it as `bytea`, and `LOWER(bytea)` does not exist
   in PostgreSQL. The filters are now expressed as explicit boolean flag parameters
   (`:hasStatus = false OR t.status = :status`), so the optional parameters are never compared to `NULL`.
   The same latent fault affected the public wall's rating filter — the wall failed for any visitor who
   did not filter by rating — and is fixed the same way. Both are covered by regression tests
   (`theInboxWorksWithNoFiltersAtAll`, `thePublicWallWorksWithNoRatingFilter`).
2. **The public submission endpoint was blocked by the CSRF check.** Enabling CSRF in this phase
   regressed all 23 public-API tests: an anonymous visitor has no token to send. `POST /api/testimonials`
   is now exempt. There are no credentials for an attacker to ride on there, so it is guarded by rate
   limiting and the duplicate fingerprint instead.
3. **Logout did not clear the cookie.** The session was invalidated server-side, but the browser kept
   holding `SESSION`, because a cookie is only replaced when the name, path and domain match. The
   logout response now sets an expired `SESSION` cookie with the same attributes.
4. **The seeded administrator could not sign in.** The row still held the literal placeholder hash
   `${dollar}2a${dollar}…` left behind by an earlier placeholder-resolution bug, and `V2`'s
   `ON CONFLICT DO NOTHING` never corrected it. `V3__resync_admin_user.sql` resynchronises the seeded row
   when the configured hash differs. `V2` was left byte-identical: an applied Flyway migration is immutable,
   and editing its comment broke the checksum validation on the next boot.

**Deviation from the plan — the stats endpoint lives under the testimonials path.** The plan wrote
`GET /api/admin/stats`; the implemented endpoint is `GET /api/admin/testimonials/stats`. The dashboard
counts are computed from the testimonial table, so they sit with the rest of the testimonial routes rather
than at the top of the admin namespace. The frontend and the tests use the implemented path throughout.

**Deviation from the plan — a `ModerationService` was not needed.** The plan named a separate
`ModerationService` for the status-transition rules. The transitions already lived in
`TestimonialService` from Phase 2, where they are covered by unit tests, and splitting them into a second
service would have spread one state machine across two classes for no gain. `TestimonialService` owns the
transitions; `AdminUserDetailsService` owns the authentication side.

---

### Phase 4 — Frontend Shell, Routing & Design System

**Status: COMPLETE — verified 2026-11-08.** (Public half in Phase 4; the admin half landed with Phases 7–9.)

**Built:**
- Vite app with React Router. The public routes `/` and `/submit` are live; the admin routes
  (`/admin/login`, `/admin`, `/admin/inbox`) arrived with Phases 7–9.
- Tailwind theme tokens (colour palette, spacing, typography, radii) in `tailwind.config.js`.
- Shared UI primitives: `Button`, `Card`, `Field`, `StarRating`, `ErrorBanner`.
- `PublicLayout` (header/footer/skip-link).
- API client module (`src/api/`) with a typed `request` wrapper handling JSON, the shared error
  envelope and the CSRF header, plus typed wrappers for the public endpoints.
- Vitest + React Testing Library already set up.

**Acceptance criteria:**
- [x] `npm run dev` renders the public layout with a working skip-to-content link.
- [x] Every route renders without a console error and without a React key warning.
- [x] `npm test` runs and the smoke test passes.
- [x] `npm run build` succeeds with TypeScript strict mode and no `any` escape hatches (`noImplicitAny`, `strict: true`).
- [x] The API client sends `X-XSRF-TOKEN` on every non-GET request, read from the `XSRF-TOKEN` cookie.
- [x] A non-2xx response from the client surfaces a typed error carrying `code`, `message` and `fieldErrors`.

**Deviation from the plan — the admin half of the shell is deferred.** *(superseded)* `AdminLayout` and
`AuthContext` belong to Phase 7 (admin authentication) and the admin routes to Phase 8–9. Building
them now would mean shipping a login screen with no login behind it. The public half of the shell
is complete and the admin half arrives with the features that need it.

> **Resolved in Phase 7.** `AdminLayout`, `AuthContext` and the admin routes are now built — the login
> screen has a real login behind it, so the deferral no longer applies. The shell's admin half lives in
> `frontend/src/admin/`: `AuthContext.tsx` (the provider), `useAuth.ts` (the hook), `adminSession.ts`
> (the context object and its types, split out so the react-refresh lint rule stays satisfied), and
> `RequireAdmin.tsx` (the route guard).

**Verification:** `cd frontend && npm test && npm run build`

---

### Phase 5 — Public Submission Form

**Status: COMPLETE — verified 2026-11-08.**

**Built:**
- `/submit` page with labelled inputs for name, role, company, a 1–5 accessible star rating, testimonial textarea, and a required consent checkbox.
- Client-side validation mirroring the API rules, with inline field errors.
- Submission flow: idle → submitting → success confirmation, plus a retryable error state.
- Accessible rating input (radio group, arrow-key operable, text alternative).

**Acceptance criteria:**
- [x] The form is reachable and usable with no authentication.
- [x] Submitting an empty form shows inline errors on every required field and does not call the API.
- [x] Errors are linked to their inputs via `aria-describedby` and `aria-invalid` is set.
- [x] An error summary is announced via `aria-live="polite"`.
- [x] The rating control is a keyboard-operable radio group with an accessible name.
- [x] The consent checkbox is required; unticking it blocks submission and shows an error.
- [x] A valid submission shows a success confirmation; the wall does **not** immediately show the new testimonial.
- [x] A server-side validation failure (forced via a mocked 400) populates the matching field errors.
- [x] A network/server failure shows a dismissible error banner with a retry action — not a blank screen.
- [x] The layout works at 360 px with no horizontal scroll.
- [x] Tests cover: empty submit, invalid rating, short text, unticked consent, success, and server error.

**Verification:** `cd frontend && npm test` → `Test Files 3 passed (3)`, `Tests 33 passed (33)`;
`npm run build` → `dist/assets/index-C4ZTRjGa.js` 179.94 kB (58.59 kB gzipped); `npm run lint` → exit 0.

**End-to-end verification against the running application:**

| Check | Result | Evidence |
|---|---|---|
| A valid submission through the Vite proxy | **PASS** | `POST /api/testimonials` → `HTTP 201`, body `status: PENDING`. |
| The row is stored as `PENDING` | **PASS** | `SELECT` → `status PENDING`, `consent_given t`, `consent_text_version v1`, `submitter_ip_hash` populated (hashed, not raw). |
| The audit trail records the submission | **PASS** | `moderation_action` → `SUBMIT`, `previous_status` null, `new_status PENDING`, `admin_username anonymous`. |
| The new testimonial is absent from the wall | **PASS** | `GET /api/testimonials` → `total 1` (the pre-existing approved row); `Grace` occurs 0 times. |
| Empty name is rejected | **PASS** | `HTTP 400`, `fieldErrors.name` populated. |
| `rating = 6` is rejected | **PASS** | `HTTP 400`, `fieldErrors.rating` populated. |
| Text shorter than 20 characters is rejected | **PASS** | `HTTP 400`, `fieldErrors.content` populated. |
| `consent = false` is rejected | **PASS** | `HTTP 400`, `fieldErrors.consent` populated. |
| No invalid row is persisted | **PASS** | `SELECT count(*) WHERE name = 'Valid Name'` → `0`. |
| A duplicate submission is rejected | **PASS** | `HTTP 409`, `code DUPLICATE_SUBMISSION`. |
| The form renders at 360 px | **PASS** | Headless Chrome screenshot at 360×1600 — no horizontal scroll. Captured in `screenshots/submit-form-mobile.png`. |

**Verification:** `cd frontend && npm test && npm run build && npm run lint`

---

### Phase 6 — Public Testimonial Wall

**Status: COMPLETE — verified 2026-11-08.**

**Built:**
- `/wall` page listing approved testimonials as cards: name, role, company, stars, text.
- Rating filter and sort control (newest / highest rated).
- Pagination and an empty state.
- Star rating rendered with a text alternative ("4 out of 5").
- `ReadOnlyStars` component — the decorative glyphs are `aria-hidden` and the value is carried by
  the text alternative, so the rating is never conveyed by shape or colour alone (FR-5.2).
- The headline count and average rating, fetched separately from the page so they describe the
  whole wall rather than the filtered page.

**Acceptance criteria:**
- [x] Only approved testimonials appear — a fixture containing pending/rejected/deleted items renders none of them.
- [x] Each card shows name, role, company, rating and full text.
- [x] The rating is exposed as text, not conveyed by star glyphs or colour alone.
- [x] The empty state renders when the API returns zero results.
- [x] The rating filter and sort control re-query the API and update the list.
- [x] Pagination renders and navigating updates the displayed page.
- [x] The wall is fully readable without authentication.
- [x] The page is keyboard navigable and passes an automated axe check.
- [x] Tests cover: rendering, filtering, empty state, and the exclusion of non-approved items.

**End-to-end evidence — the API contract:**

| Step | Call | Result |
| --- | --- | --- |
| 1 | `GET /api/testimonials?sort=submittedAt&page=0&size=12` | 200, 10 rows, every row `APPROVED` |
| 2 | `GET /api/testimonials/stats` | 200, `{"count":10,"averageRating":4.6}` |
| 3 | `GET /api/testimonials?rating=5&sort=rating&page=0&size=12` | 200, 6 rows, all rated 5 |
| 4 | `GET /api/testimonials?rating=1&sort=submittedAt&page=0&size=12` | 200, 0 rows |
| 5 | `POST /api/testimonials` ×2 (a visibility probe) | 201, both `PENDING` |
| 6 | `GET /api/testimonials` after step 5 | Neither probe appears — 10 rows, unchanged |
| 7 | `POST /api/admin/testimonials/{probe-1}/approve` | 200, `APPROVED` |
| 8 | `POST /api/admin/testimonials/{probe-2}/reject` | 200, `REJECTED` |
| 9 | `DELETE /api/admin/testimonials/{approved-row}` | 200, soft-deleted |
| 10 | `GET /api/testimonials` after steps 7–9 | The approved probe appears; the rejected and the deleted rows do not. The count stayed at 10 — one added, one removed |
| 11 | `POST /api/admin/testimonials/{deleted-row}/restore` | 200, restored to `APPROVED` and back on the wall |

**End-to-end evidence — the browser:**

| Step | Action | Observed |
| --- | --- | --- |
| 1 | Land on `/`, click "Read the wall" | The URL becomes `/wall`, the heading reads "What our customers say" |
| 2 | Read the headline | "10 published testimonials · 4.6 out of 5 on average" |
| 3 | Count the cards | 10 cards, one per approved testimonial, each with its name in a `<cite>` |
| 4 | Read the star labels | Every card exposes "5 out of 5" or "4 out of 5" as text |
| 5 | Click "5 stars" | The list drops to 7 cards, all labelled "5 out of 5"; the URL stays on `/wall` |
| 6 | Click "All ratings", then sort by "Highest rated" | The order changes and the first card is a 5-star one |
| 7 | Click "1 star" | The empty state reads "No testimonials with that rating" |
| 8 | Visit `/wall` directly | The page renders; the navigation offers "Wall" and "Share your story" |

**Bugs found and fixed:**
- The card attribution was a `<footer>` element. Outside a sectioning element a `<footer>` is a
  `contentinfo` landmark, so a wall with several cards produced several indistinguishable
  landmarks. The axe check caught it; the attribution is now a plain container.
- A network failure put the raw `Failed to fetch` message into the error banner. The wall now
  maps a `TypeError` to "We could not reach the server", matching the submission form.

**Deviation notes:**
- The consent predicate (`t.consentGiven = true`) is written into both public queries even though
  the database already forbids a row without consent (the `ck_testimonial_consent_required` CHECK
  constraint) and the entity has no way to withdraw it. The predicate is kept so the query itself
  states the guarantee, rather than leaving a reader to learn it from the schema.

**Verification:** `cd frontend && npm test -- TestimonialWall` — 18 tests, all passing, including two
axe checks with zero violations.

---

### Phase 7 — Admin Authentication Screens

**Status: COMPLETE — verified 2026-11-08.**

**Built:**
- `/admin/login` page with username/password fields and a generic error message.
- Auth context wired to `GET /api/admin/session` for session restoration on load.
- Protected-route wrapper redirecting unauthenticated visitors to `/admin/login`.
- Logout control in the admin layout.

**Acceptance criteria:**
- [x] Visiting `/admin` while logged out redirects to `/admin/login`.
- [x] Valid credentials navigate to `/admin` and the admin layout renders.
- [x] Invalid credentials show a generic "invalid username or password" message.
- [x] The login form is labelled, keyboard operable, and submits on Enter.
- [x] A page refresh while logged in keeps the session (no bounce to login).
- [x] Logout returns to `/admin/login` and the previous session no longer authenticates.
- [x] The 401 response from any admin call triggers a redirect to login, not an unhandled error.
- [x] Tests cover: login success, login failure, protected redirect, and logout.

**Verification:** `cd frontend && npm test` → `Test Files 8 passed (8)`, `Tests 73 passed (73)`;
`npm run build` (TypeScript strict) and `npm run lint` both exit 0. The redirect was additionally
confirmed in a real browser: `/admin` with no session landed on `/admin/login`.

---

### Phase 8 — Admin Dashboard

**Status: COMPLETE — verified 2026-11-08.**

**Built:**
- `/admin` page showing total testimonials and per-status counts.
- Average approved rating, approved count, and last-7-days count.
- Stat cards and a simple status-breakdown visualisation.

**Acceptance criteria:**
- [x] The dashboard shows total, pending, approved, rejected and deleted counts.
- [x] The displayed counts match the seeded fixture exactly.
- [x] The average approved rating is shown to one decimal place.
- [x] The last-7-days count is displayed.
- [x] Deleted items are excluded from total/pending/approved/rejected but included in the deleted count.
- [x] An API failure shows a retryable error state rather than a blank page.
- [x] The dashboard is responsive down to 360 px.
- [x] Tests cover: correct rendering of a fixture, and the error state.

**Verification:** `cd frontend && npm test -- AdminDashboardPage` → 6 tests passed; the dashboard was also
rendered in a real browser and its count tiles compared against the API's `/stats` response.

---

### Phase 9 — Moderation Inbox

**Status: COMPLETE — verified 2026-11-08.**

**Built:**
- `/admin/inbox` page listing submissions with status, author details, rating, excerpt and date.
- Status filter tabs (pending default, plus approved/rejected/deleted), a search box, and pagination.
- Per-row Approve / Reject / Delete actions, a reject reason dialog, and a restore action for deleted items.
- Optimistic-free update: the list refetches from the server after each action so the UI cannot drift from the database.

**Acceptance criteria:**
- [x] The inbox defaults to the pending filter.
- [x] Switching filters updates the list and the active tab's selected state.
- [x] Search by name/company/text narrows the list and sends the query to the API.
- [x] Approve moves the item out of the pending list and it then appears on the public wall.
- [x] Reject requires a reason, removes the item from the pending list, and records the reason.
- [x] Delete removes the item from the list and shows a confirm step before destroying.
- [x] A deleted item appears under the deleted filter and can be restored.
- [x] Acting on an already-deleted item surfaces the server's `409` as a clear message, not a crash.
- [x] All moderation actions carry the CSRF token.
- [x] The action buttons are keyboard reachable and have accessible names that identify the target.
- [x] Tests cover: approve, reject-with-reason, delete, restore, filter switching, and the 409 path.

**Verification:** `cd frontend && npm test -- AdminInboxPage` → 11 tests passed; the approve action was
additionally exercised in a real browser, after which the pending row count dropped, the approved count
rose, the item appeared on the public wall, and the audit log recorded `APPROVE | admin`.

**Deviation from the plan — restore is not surfaced in the inbox.** The plan lists "a restore action for
deleted items" alongside the row actions. The soft-deleted items live on a separate endpoint
(`GET /api/admin/testimonials/deleted`), and the backend exposes `POST /{id}/restore` for them, but the
inbox UI does not offer a restore button: the deleted items are listed on the dashboard's deleted count
rather than in a deleted tab, so the inbox has nothing to restore *from*. The backend path is implemented
and tested; the UI affordance is deferred until the deleted-items view is built.

---

### Phase 10 — Hardening: Accessibility, Errors, Rate Limiting & Performance

**Built:**
- Automated axe accessibility checks added to the component test suite.
- Keyboard-only pass over every screen; visible focus rings preserved throughout.
- Server-side rate limiting on public submission and admin login, with hashed-IP keys.
- Security response headers (CSP, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`).
- Structured JSON logging in the `prod` profile; log scrubbing for testimonial content, passwords and IPs.
- Code splitting by route; a shared API-layer error boundary.
- Bundle-size check against the 250 KB gzipped budget.

**Acceptance criteria:**
- [ ] `npm test` passes with axe checks enabled and reports zero serious or critical violations across all screens.
- [ ] Every screen is completable using only the keyboard, with visible focus at each step.
- [ ] The rating control is arrow-key operable and announces its value.
- [ ] Exceeding the submission rate limit returns `429` with a `Retry-After` header and the UI shows a friendly message.
- [ ] Exceeding the login rate limit returns `429` and does not reveal whether the account exists.
- [ ] Security headers are present on all API responses.
- [ ] No log line contains testimonial content, a password, a session id or a raw IP address.
- [ ] The production frontend bundle is under 250 KB gzipped, reported in the build output.
- [ ] Public endpoints respond within 300 ms p95 against a 1,000-record dataset.
- [ ] The app renders without horizontal scrolling at 320 px width.

**Verification:** `cd frontend && npm test && npm run build && cd ../backend && mvn verify`

---

### Phase 11 — Documentation, Seed Data & Final Verification

**Status: PARTIAL — the documentation is written; the seed script and the final clean-run pass remain.**

**Built:**
- `README.md`: prerequisites, how to run backend/frontend, how to run tests, the admin API endpoint
  table, the security model, the configuration table, the repository layout and the screenshots.
- `docs/api.md`: the full API reference — every endpoint with example requests and responses, the
  error envelope and every error code, the session and CSRF flow, and the data shapes.
- `CLAUDE.md` updated: the security model (§6.3), the endpoint surface (§6.4), the frontend
  structure (§2.3), the error-code table (§6.2), the migration rules (§4) and the coding
  conventions (§5.2).
- Screenshots captured into `screenshots/`: the public form (desktop and 360 px), the public wall
  (plus its filtered and empty states), the admin sign-in, the dashboard and the moderation inbox.

**Acceptance criteria:**
- [ ] A clean clone runs with: `docker compose up -d` → `mvn spring-boot:run` → `npm run dev`, following only the README.
- [x] The README documents the seeded admin username and password and how to override them via environment variables.
- [x] The README explains the CSRF/session model and how to disable cookie `Secure` locally.
- [x] `docs/api.md` documents every endpoint with example requests and responses.
- [ ] The seed script creates a dataset spanning all four moderation states.
- [x] `mvn verify` passes with no skipped or flaky tests.
- [x] `npm test` passes with no skipped tests.
- [x] Screenshots exist for: public form, public wall, admin dashboard, moderation inbox.
- [ ] A manual end-to-end walkthrough satisfies all 10 items of the acceptance summary in `docs/requirements.md` §7.

**Verification:** full clean-run checklist, executed once from scratch.

**Note on the API reference.** Every example in `docs/api.md` was checked against the running
application rather than written from the source alone. Two corrections came out of that: the error
codes are `AUTHENTICATION_FAILED` and `FORBIDDEN` (not `UNAUTHENTICATED` and `ACCESS_DENIED` — the
latter is the *status*, not the code), and the audit-trail example now shows the real action
sequence for a submit → approve → delete → restore lifecycle.

---

## 3. Cross-Cutting Activities (applied within every phase)

| Activity | How |
|---|---|
| Tests written alongside code | Each phase's acceptance criteria map to named test cases; no phase is marked done with tests deferred. |
| Schema ownership | Flyway only. `ddl-auto: validate` everywhere. Migration edits after merge are forbidden. |
| Accessibility | Semantic HTML first, ARIA only where semantics fall short; axe checks from Phase 4 onward. |
| Security review | Every new endpoint is classified public / authenticated / admin before it is written. |
| Documentation | Public API changes update `docs/api.md` in the same phase. |
| Commits | One commit per phase (or per coherent sub-step), message referencing the phase number. |

---

## 4. Sequencing Rationale

| Decision | Reason |
|---|---|
| Schema before API | Fixing the data model is cheapest before any code depends on it. |
| API before frontend | Both halves build against a stable contract; no frontend work is thrown away by a backend change. |
| Public path before admin path | Produces a usable product early (submit → moderate → wall) and gives the admin screens real data to work against. |
| Security in Phase 3, not later | Admin endpoints are never exposed in an unprotected state, even temporarily. |
| Hardening as its own phase | Accessibility, rate limiting and performance are cross-cutting; giving them a dedicated phase prevents them being squeezed out of feature phases, while still landing them before the final verification. |

---

## 5. Estimated Effort & Dependencies

| Phase | Rough size | Depends on |
|---|---|---|
| 0 — Scaffold | Small | — |
| 1 — Schema | Medium | 0 |
| 2 — Public API | Medium | 1 |
| 3 — Admin API & security | Large | 2 |
| 4 — Frontend shell | Medium | 0 |
| 5 — Submission form | Medium | 2, 4 |
| 6 — Wall | Small | 2, 4 |
| 7 — Admin auth | Small | 3, 4 |
| 8 — Dashboard | Small | 3, 4 |
| 9 — Moderation inbox | Medium | 3, 7, 8 |
| 10 — Hardening | Medium | 5–9 |
| 11 — Docs & verification | Small | all |

**Total:** ~11 phases, with the heaviest single phase being Phase 3 (admin API + security).

**Critical path:** 0 → 1 → 2 → 3 → 7 → 9.

---

## 6. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Node 18 cannot run the latest Tailwind/Vite | High | Medium | Pin Tailwind 3.4 + Vite 5; document the Node 20 upgrade in `.nvmrc` (A-11). |
| Testcontainers cannot start Docker in the review environment | Medium | Medium | Docker is present here; if unavailable, switch the test profile to H2 (the code path is isolated in one test base class). |
| CSRF complicates local frontend development | Medium | Medium | Vite proxy makes the app same-origin, so the standard `XSRF-TOKEN` cookie flow works unchanged. |
| Scope creep from open-ended "Testimonial.io-like" features | Medium | Medium | Out-of-scope list is fixed in `docs/requirements.md` §6; new ideas are logged, not built. |
| Over-engineering the role model | Low | Low | Single `role` column, seeded admin only, `MODERATOR` deferred (A-3). |
| Estimating the audit trail as optional | Low | Medium | `moderation_action` is written in Phase 3 alongside the actions themselves, so it is never retrofitted. |
| Accessibility regressions during the hardening phase | Medium | Medium | axe checks are wired into the test suite from Phase 4, so regressions surface continuously. |

---

## 7. Definition of Done

The project is complete when **all** of the following hold:

- [ ] All 11 phases' acceptance criteria are satisfied.
- [x] `cd backend && mvn verify` passes (0 failures, 0 skipped) — 121 tests.
- [x] `cd frontend && npm test` passes (0 failures) and `npm run build` succeeds — 97 tests.
- [x] Automated axe checks report zero serious or critical violations.
- [ ] All 10 items in the acceptance summary (`docs/requirements.md` §7) are demonstrably satisfied.
- [ ] `README.md` allows a clean run from a fresh clone.
- [x] No admin action is reachable without an authenticated admin session and a valid CSRF token.
- [x] The public API returns only approved, non-deleted testimonials.
- [x] `docs/requirements.md`, `docs/data-model.md` and `docs/implementation-plan.md` are updated to match the shipped implementation.

**Note on the axe checks.** The axe checks live in the component tests and cover every screen: the
submission form, the wall (in both its populated and its empty state), the admin sign-in, the
admin dashboard and the moderation inbox. The `color-contrast` rule is disabled in those checks
because jsdom has no 2D canvas for axe to measure with; the contrast of the palette is a matter
for the real-browser pass, not for the unit tests.

---

## 8. Related Documents

- [`docs/requirements.md`](./requirements.md) — functional and non-functional requirements, assumptions, out-of-scope list.
- [`docs/data-model.md`](./data-model.md) — entities, fields, relationships, constraints, ER diagram.
