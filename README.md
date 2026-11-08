# LoveProof

A self-hosted testimonial management application. Visitors submit testimonials through a public
form, an administrator moderates them in an inbox, and approved testimonials publish on a public
wall.

| | |
|---|---|
| Frontend | React 18 · TypeScript (strict) · Vite 6 · Tailwind CSS 3.4 |
| Backend | Java 21 · Spring Boot 3.5 · Spring Data JPA · Flyway · Actuator |
| Database | PostgreSQL 16 |

> **Status: Phases 0–9 complete and verified.** The database schema, the domain model, the public
> REST API, the public submission form, the public testimonial wall and the complete admin
> moderation surface — protected access, dashboard counts, approval, rejection, deletion and the
> audit trail — are in place and tested (121 backend tests, 97 frontend tests, 0 failures). The
> remaining hardening phase lands as defined in
> [`docs/implementation-plan.md`](docs/implementation-plan.md).

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java | 21 | Required by the backend build. |
| Maven | 3.8+ | Backend build tool. |
| Node.js | 18.18+ (20 recommended) | Frontend build tool. See [Node version](#node-version-note). |
| Docker | any recent | Runs PostgreSQL 16 and the backend's Testcontainers suite. |
| Docker Compose | v2+ | Included with modern Docker Desktop / Engine. |

---

## Quick start

```bash
# 1. Start PostgreSQL 16
docker compose up -d

# 2. Start the API on http://localhost:8080
cd backend && mvn spring-boot:run

# 3. Start the SPA on http://localhost:5173 (separate terminal)
cd frontend && npm install && npm run dev
```

Then open <http://localhost:5173>.

To copy the environment template first:

```bash
cp .env.example .env
```

The admin area is at <http://localhost:5173/admin>. Sign in with the seeded development credentials
`admin` / `loveproof` — the password is `loveproof` for local development only and must be
overridden in any shared environment (see [Configuration](#configuration)).

### Screenshots

| Screen | Image |
|---|---|
| Public submission form | [`screenshots/submit-form.png`](./screenshots/submit-form.png) |
| Submission form at 360 px | [`screenshots/submit-form-mobile.png`](./screenshots/submit-form-mobile.png) |
| Public testimonial wall | [`screenshots/wall.png`](./screenshots/wall.png) |
| Wall filtered to five stars | [`screenshots/wall-filtered.png`](./screenshots/wall-filtered.png) |
| Wall empty state | [`screenshots/wall-empty.png`](./screenshots/wall-empty.png) |
| Admin sign in | [`screenshots/admin-login.png`](./screenshots/admin-login.png) |
| Admin dashboard | [`screenshots/admin-dashboard.png`](./screenshots/admin-dashboard.png) |
| Moderation inbox | [`screenshots/admin-inbox.png`](./screenshots/admin-inbox.png) |

---

## Repository layout

```
loveproof-testimonials/
├── backend/                  # Spring Boot + Maven API
│   └── src/main/
│       ├── java/com/loveproof/testimonials/
│       │   ├── LoveProofApplication.java
│       │   ├── web/          # ✓ Health, Testimonial, AdminTestimonial, AdminSession controllers,
│       │   │                 #   GlobalExceptionHandler, dto/ records
│       │   ├── domain/       # ✓ JPA entities and enums
│       │   ├── repository/   # ✓ Spring Data repositories
│       │   ├── service/      # ✓ Business rules, moderation transitions, fingerprinting,
│       │   │                 #   AdminSessionService (the acting administrator)
│       │   ├── config/       # ✓ ClockConfig, SecurityConfig, Jackson, rate limiting
│       │   └── exception/    # ✓ Domain exception types
│       └── resources/
│           ├── application.yml
│           ├── application-local.yml
│           ├── application-prod.yml   # ✓ Secure session cookie
│           └── db/migration/ # ✓ Flyway migrations (V1 baseline, V2 admin seed, V3 admin resync)
├── frontend/                 # Vite + React + TS SPA
│   └── src/
│       ├── api/              # ✓ Typed API client + error-envelope handling, public + admin methods
│       ├── admin/            # ✓ AuthContext, useAuth, RequireAdmin, AdminLayout,
│       │                     #   AdminLoginPage, AdminDashboardPage, AdminInboxPage
│       ├── components/       # ✓ PublicLayout, Button, Card, Field, StarRating, ErrorBanner
│       ├── context/          # (Phase 7) AuthContext
│       ├── hooks/            # (Phase 4+) Data hooks
│       └── pages/            # ✓ HomePage, SubmitTestimonialPage (+ validation module)
├── docs/                     # Requirements, data model, plan
├── screenshots/              # Captured UI screenshots
├── docker-compose.yml        # PostgreSQL 16 for local development
└── .env.example              # Environment template (placeholders only)
```

---

## Commands

### Backend (`cd backend`)

| Command | Purpose |
|---|---|
| `mvn compile` | Compile the sources. |
| `mvn spring-boot:run` | Run the API on port 8080. |
| `mvn spring-boot:run -Dspring-boot.run.profiles=local` | Run with the local profile (verbose logging). |
| `mvn package` | Build a runnable jar into `target/`. |
| `mvn test` | Run the backend test suite. |
| `mvn verify` | Full verification: compile, test, package. |

### Frontend (`cd frontend`)

| Command | Purpose |
|---|---|
| `npm install` | Install dependencies. |
| `npm run dev` | Dev server on port 5173, proxying `/api` to `localhost:8080`. |
| `npm run dev` (alt. port) | `VITE_PROXY_TARGET=http://localhost:8099 npm run dev` — proxy to a backend on another port. |
| `npm run build` | Type-check and produce a production bundle in `dist/`. |
| `npm run preview` | Serve the production bundle locally. |
| `npm run lint` | Run ESLint. |
| `npm test` | Run the test suite once. |
| `npm run test:watch` | Run the test suite in watch mode. |

### Database

| Command | Purpose |
|---|---|
| `docker compose up -d` | Start PostgreSQL 16. |
| `docker compose exec db pg_isready -U loveproof -d loveproof` | Check the database is accepting connections. |
| `docker compose exec db psql -U loveproof -d loveproof -c '\dt'` | List tables. |
| `docker compose down` | Stop the database. |
| `docker compose down -v` | Stop the database and delete its volume. |

Flyway applies migrations automatically when the backend starts. Hibernate runs with
`ddl-auto: validate` in every profile — Flyway owns the schema.

---

## Configuration

All configuration is externalised through environment variables with local defaults.

| Variable | Default | Purpose |
|---|---|---|
| `POSTGRES_DB` | `loveproof` | Database name. |
| `POSTGRES_USER` | `loveproof` | Database user. |
| `POSTGRES_PASSWORD` | `loveproof` | Database password. |
| `POSTGRES_PORT` | `5432` | Host port for PostgreSQL. |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/loveproof` | JDBC URL. |
| `SPRING_DATASOURCE_USERNAME` | `loveproof` | Application datasource user. |
| `SPRING_DATASOURCE_PASSWORD` | `loveproof` | Application datasource password. |
| `SERVER_PORT` | `8080` | HTTP port for the API. |
| `VITE_PROXY_TARGET` | `http://localhost:8080` | Backend address the Vite dev server proxies `/api` to. |
| `LOVEPROOF_ADMIN_USERNAME` | `admin` | Username seeded into `admin_user` by the `V2` migration. |
| `LOVEPROOF_ADMIN_DISPLAY_NAME` | `LoveProof Admin` | Display name shown for the seeded administrator. |
| `LOVEPROOF_ADMIN_ROLE` | `ADMIN` | Role of the seeded administrator (`ADMIN` or `MODERATOR`). |
| `LOVEPROOF_ADMIN_PASSWORD_HASH` | dev-only BCrypt hash | BCrypt hash (cost 10) of the seeded admin's password. Never a plaintext secret. |
| `LOVEPROOF_CONSENT_TEXT_VERSION` | `v1` | Identifier of the consent wording shown at submission time. |
| `LOVEPROOF_IP_HASH_SALT` | `loveproof-local-salt` | Salt for hashing client IPs. Change it per environment. |
| `LOVEPROOF_SECURE_COOKIES` | `false` | Marks the session and CSRF cookies `Secure`. Leave `false` for local HTTP; the `prod` profile sets it to `true` regardless. |

> The seeded administrator's default password is `loveproof`. It exists for local development only
> — override `LOVEPROOF_ADMIN_PASSWORD_HASH` with a BCrypt hash of your own in any shared
> environment. The `V3` migration resynchronises the seeded row whenever the configured hash
> differs from the stored one.

---

## API

### Public

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/health` | Service status. |
| `GET` | `/actuator/health` | Spring Boot operational health, including liveness/readiness probes. |
| `POST` | `/api/testimonials` | Submit a testimonial. Validated; stored as `PENDING`. |
| `GET` | `/api/testimonials` | The public wall. Paginated, filterable by rating, sortable. |
| `GET` | `/api/testimonials/stats` | Public count and average rating of approved testimonials. |

### Admin — requires a session with the `ADMIN` role

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/admin/session` | Sign in. Sets the `SESSION` cookie. Exempt from CSRF. |
| `GET` | `/api/admin/session` | The signed-in administrator's identity. |
| `DELETE` | `/api/admin/session` | Sign out. Invalidates the session and expires the cookie. |
| `GET` | `/api/admin/testimonials/stats` | Dashboard counts (total, pending, approved, rejected, deleted, last 7 days, average rating). |
| `GET` | `/api/admin/testimonials` | Moderation inbox. Filter by status, free-text search, paginated. |
| `GET` | `/api/admin/testimonials/deleted` | Soft-deleted testimonials, available for restore. |
| `GET` | `/api/admin/testimonials/{id}` | A single testimonial, including its moderation state. |
| `GET` | `/api/admin/testimonials/{id}/audit` | The append-only moderation history for one testimonial. |
| `POST` | `/api/admin/testimonials/{id}/approve` | Approve a pending testimonial. |
| `POST` | `/api/admin/testimonials/{id}/reject` | Reject a testimonial. A reason is mandatory. |
| `DELETE` | `/api/admin/testimonials/{id}` | Soft-delete a testimonial. |
| `POST` | `/api/admin/testimonials/{id}/restore` | Restore a soft-deleted testimonial. |

Every state-changing request needs the CSRF token: send the `XSRF-TOKEN` cookie's value back in the
`X-XSRF-TOKEN` header. See [Security model](#security-model).

Errors use one envelope for every failure: `{ code, message, fieldErrors, timestamp, path }`.

---

## Testing

```bash
cd backend && mvn verify      # backend: unit, slice and integration tests
cd frontend && npm test       # frontend: component and unit tests
cd frontend && npm run lint   # frontend: ESLint
cd frontend && npm run build  # frontend: type-check + production bundle
```

The backend suite runs against a **real PostgreSQL 16 instance via Testcontainers**, so Flyway
migrations, CHECK constraints and partial indexes are exercised for real rather than against an
emulated database. **Docker must be running** for `mvn test`.

---

## Security model

The admin surface is protected by the Spring Security filter chain. There is no configuration flag
that can switch it off: the routes are always registered, and every one of them requires an
authenticated session with the `ADMIN` role.

**Sessions.** The administrator signs in at `POST /api/admin/session`. The password is checked
against the BCrypt hash in `admin_user`, the session id is regenerated on authentication, and the
browser receives a `SESSION` cookie that is `HttpOnly` and `SameSite=Lax`. The frontend never needs
to read it, and `SameSite=Lax` keeps it off cross-site POSTs — the classic CSRF delivery route.
`DELETE /api/admin/session` invalidates the session server-side *and* expires the cookie, so the
browser does not keep a dead session id around.

**CSRF.** The token is issued in a readable `XSRF-TOKEN` cookie and must be echoed in the
`X-XSRF-TOKEN` header on every state-changing request. The SPA's API client does this
automatically. Two endpoints are exempt: the login request (the browser has no token yet — it is
protected by the session-id regeneration instead) and the public submission endpoint (it is
anonymous, so there are no credentials for an attacker to ride on; it is guarded by rate limiting
and the duplicate-submission fingerprint instead).

**Status codes.** The CSRF filter runs *before* authorization, so the access-denied handler
inspects the authentication state to pick the right code: an anonymous request gets `401`, an
authenticated request without a valid token gets `403`. Both answer with the shared JSON error
envelope rather than an HTML login redirect.

**Cookies in production.** The `prod` profile marks both the session cookie and the CSRF cookie
`Secure`, so they travel over HTTPS only. A production deployment must therefore sit behind a
TLS-terminating proxy — over plain HTTP the `Secure` cookie is never sent and every administrator
is silently locked out.

**Passwords.** Stored as BCrypt hashes (cost ≥ 10) only. No plaintext secret is committed; the
seeded development password is documented above and must be overridden in any shared environment.

**Same origin.** The frontend and backend are same-origin in development via the Vite proxy, so no
CORS configuration is required.

Public data hygiene:

- Client IP addresses are stored only as a salted SHA-256 hash — never raw, never logged.
- Testimonial content is plain text and is never rendered as HTML.
- Consent is recorded per submission (`consent_given`, `consent_text_version`) and the database
  forbids withdrawing it by editing the row.
- The public API only ever returns approved, non-deleted testimonials. Pending, rejected and
  soft-deleted rows are unreachable without an admin session.

**Audit trail.** Every moderation action — and the original submission — is written to the
append-only `moderation_action` table, attributed to the administrator whose session made the
request. The trail is readable at `GET /api/admin/testimonials/{id}/audit`.

Full details: [`CLAUDE.md`](CLAUDE.md) §6.

---

## Node version note

The frontend is pinned to versions that support **Node 18** (Vite 6, Vitest 3, jsdom 26,
Tailwind 3.4) so it builds on the runtime available in this environment. Node 18 is end-of-life;
`.nvmrc` specifies Node 20 as the recommended version. Upgrading to Node 20+ would allow newer
Vite/Vitest/Router releases and clear the remaining `npm audit` advisories, which currently have no
Node 18-compatible fix.

---

## Documentation

| Document | Contents |
|---|---|
| [`docs/requirements.md`](docs/requirements.md) | Functional and non-functional requirements, assumptions, out-of-scope list. |
| [`docs/data-model.md`](docs/data-model.md) | Entities, fields, relationships, constraints, ER diagram. |
| [`docs/api.md`](docs/api.md) | The full API reference — every endpoint with example requests and responses. |
| [`docs/implementation-plan.md`](docs/implementation-plan.md) | Build phases with acceptance criteria. |
| [`CLAUDE.md`](CLAUDE.md) | Architecture, commands, conventions, workflow rules. |

---

## License

Course submission — not licensed for redistribution.
