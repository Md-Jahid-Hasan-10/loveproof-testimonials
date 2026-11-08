# LoveProof

A self-hosted, multi-tenant testimonial collection SaaS.

A customer signs up, creates a **Space** — a page with its own public URL that collects
testimonials — and curates what comes in. Each Space can be embedded on the customer's own website.
Plans are enforced on the server, and billing runs through Stripe.

The original single-tenant wall and its moderation inbox still exist alongside it; the two surfaces
share the database and the deployment but not their sessions.

| | |
|---|---|
| Frontend | React 18 · TypeScript (strict) · Vite 6 · Tailwind CSS 3.4 |
| Backend | Java 21 · Spring Boot 3.5 · Spring Data JPA · Flyway · Actuator |
| Database | PostgreSQL 16 |

> **Status: the whole PRD build order is complete and verified.** Accounts and email verification,
> the dashboard shell and Spaces management, the creation wizard, the public submission page, the
> curation inbox, the Wall of Love and the embed generator, Stripe billing with plan-limit
> enforcement, and the dashboard analytics. **361 backend tests, 222 frontend tests, 0 failures.**

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

Three terminals. The database, then the API, then the SPA.

```bash
# 1. Start PostgreSQL 16 (first time only, copy the env template)
cp .env.example .env
docker compose up -d

# 2. Start the API on http://localhost:8080
cd backend && mvn spring-boot:run

# 3. Start the SPA on http://localhost:5173 (separate terminal)
cd frontend && npm install && npm run dev
```

Then open <http://localhost:5173>.

Flyway applies the migrations automatically when the backend starts, so there is no separate
migration step. Hibernate runs with `ddl-auto: validate` in every profile — Flyway owns the schema.

### Where to go

| Surface | URL | Sign in with |
|---|---|---|
| **Customer app** | <http://localhost:5173/app> | Register a new account at `/app/register` |
| Admin moderation | <http://localhost:5173/admin> | `admin` / `loveproof` (seeded, development only) |
| Public wall | <http://localhost:5173/wall> | — |
| Public submission | <http://localhost:5173/submit> | — |
| Health check | <http://localhost:8080/api/health> | — |

### A five-minute walkthrough

This is the whole product, end to end, with no Stripe account and no email server needed.

1. **Register an account.** Open <http://localhost:5173/app/register> and sign up. You land on the
   dashboard at `/app`, which is empty.
2. **Create a Space.** Click **New Space**, give it a title and a slug. The slug becomes the public
   URL: `http://localhost:5173/s/<your-slug>`.
3. **Submit a testimonial as a visitor.** Open that URL **in a private window** — you want to be
   signed out, so you are the visitor rather than the owner. Fill in the form and submit it.
4. **Curate it.** Back in your own window, open the Space and click **Testimonials**. The
   submission is there, pending. Approve it, or edit the content and rating first.
5. **Embed it.** Click **Wall of Love** on the Space page. Copy the snippet and paste it into a
   `<script>` tag on any page; the widget renders the approved testimonials there.
6. **Check the analytics.** The dashboard shows your Space count, the testimonials collected, the
   unique submitters and a per-day chart, with a range switch between 7 days, 30, 90 and a year.
7. **Try the plan limits.** A Free account may have 3 Spaces and 100 testimonials per Space. Try to
   create a fourth, and the API refuses with `409 PLAN_LIMIT_REACHED` — the interface hides the
   button, but the server is what actually enforces it.

The original single-tenant wall is still there at `/wall`, with the moderator's inbox at `/admin`.

### Trying the billing flow

Billing needs real Stripe keys; everything else works without them. With the keys set:

```bash
export LOVEPROOF_STRIPE_SECRET_KEY=sk_test_…
export LOVEPROOF_STRIPE_WEBHOOK_SECRET=whsec_…
export LOVEPROOF_STRIPE_PREMIUM_PRICE_ID=price_…
```

1. Go to `/app/billing` and click **Upgrade to Premium**. The browser is sent to Stripe Checkout.
2. Pay. The browser returns to `/app/billing?checkout=success`, which shows *Confirming your
   upgrade…* and polls until the webhook lands. **The redirect itself never changes the plan** —
   only the webhook does.
3. In another terminal, forward Stripe's events to the local webhook:

   ```bash
   stripe listen --forward-to localhost:8080/api/billing/webhook
   ```

4. Click **Cancel subscription**. The plan **stays Premium** and the page shows the end date with a
   **Resume subscription** button — cancelling at period end is not a downgrade.
5. Click **Manage payment details** for the Stripe Billing Portal.

Without the keys, the billing page still shows both plans but the upgrade answers
`503 BILLING_NOT_CONFIGURED`, and the webhook refuses every delivery with `400`.

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
│       │   │                 #   AppAuth/AppSpace/AppSpaceEmbed/AppSpaceInbox/AppBilling/AppDashboard
│       │   │                 #   controllers, PublicSpace/PublicEmbed, StripeWebhookController,
│       │   │                 #   EmbedScriptController, GlobalExceptionHandler, dto/ records
│       │   ├── domain/       # ✓ JPA entities and enums (AppUser, Space, SpaceTestimonial,
│       │   │                 #   SpaceEmbedConfig, StripeEvent, Plan, SubscriptionStatus, …)
│       │   ├── repository/   # ✓ Spring Data repositories
│       │   ├── service/      # ✓ Business rules, moderation transitions, fingerprinting,
│       │   │                 #   SpaceService/SpaceInboxService/SpaceSubmissionService,
│       │   │                 #   EmbedService, PlanCatalog/PlanLimits, BillingService,
│       │   │                 #   CheckoutService, WebhookService, StripeSignatureVerifier,
│       │   │                 #   HttpStripeGateway, SubscriptionReconcil(er|ationJob),
│       │   │                 #   DashboardAnalyticsService, AuthService/AuthTokenService
│       │   ├── config/       # ✓ ClockConfig, SecurityConfig (admin chain), AppSecurityConfig
│       │   │                 #   (customer chain), BillingSecurityConfig (webhook chain),
│       │   │                 #   StripeProperties, SecurityHeadersConfig, SchedulingConfig
│       │   └── exception/    # ✓ Domain exception types
│       └── resources/
│           ├── application.yml
│           ├── application-local.yml
│           ├── application-prod.yml   # ✓ Secure session cookie
│           └── db/migration/ # ✓ Flyway migrations (V1 baseline … V7 billing and plan limits)
├── frontend/                 # Vite + React + TS SPA
│   └── src/
│       ├── api/              # ✓ Typed API client + error-envelope handling; public, admin,
│       │                     #   customer, Space, embed, billing and dashboard methods
│       ├── admin/            # ✓ AuthContext, useAuth, RequireAdmin, AdminLayout,
│       │                     #   AdminLoginPage, AdminDashboardPage, AdminInboxPage
│       ├── app/              # ✓ The customer surface — CustomerAuthContext, RequireCustomer,
│       │                     #   AppLayout, DashboardPage (+ analytics panel and chart),
│       │                     #   SpaceWizardPage, SpaceDetailPage, SpaceInboxPage,
│       │                     #   SpaceEmbedPage, BillingPage, SettingsPage,
│       │                     #   CustomerSignInPage, CustomerRegisterPage
│       ├── components/       # ✓ PublicLayout, Button, Card, Field, StarRating, ErrorBanner
│       ├── hooks/            # ✓ Data hooks
│       └── pages/            # ✓ HomePage, SubmitTestimonialPage (+ validation module)
├── docs/                     # Requirements, data model, plan, PRD, API reference
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

> The backend needs Java 21 on the path. If `mvn` reports a release version the
> JVM cannot produce, another JDK is shadowing it — check `java -version` and
> `JAVA_HOME`.

### Frontend (`cd frontend`)

| Command | Purpose |
|---|---|
| `npm install` | Install dependencies. |
| `npm run dev` | Dev server on port 5173, proxying `/api` to `localhost:8080`. |
| `npm run dev` (alt. port) | `VITE_PROXY_TARGET=http://localhost:8099 npm run dev` — proxy to a backend on another port. |
| `npm run build` | Type-check and produce a production bundle in `dist/`. |
| `npm run build:bundle` | Build the bundle without the type-check or the budget check. |
| `npm run preview` | Serve the production bundle locally. |
| `npm run lint` | Run ESLint. |
| `npm test` | Run the test suite once. |
| `npm run test:watch` | Run the test suite in watch mode. |

> If port 5173 is already taken, Vite offers the next free port — but the
> **public Space URLs** then use that port, and `LOVEPROOF_APP_BASE_URL` must
> match it or the embed snippet points at the wrong origin. Set the variable
> and restart the backend if Vite moves.

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
| `LOVEPROOF_APP_BASE_URL` | `http://localhost:5173` | The public origin of the installation. The embed script and embed data are served from it, so it must match the host that actually serves the backend. |
| `LOVEPROOF_ADMIN_USERNAME` | `admin` | Username seeded into `admin_user` by the `V2` migration. |
| `LOVEPROOF_ADMIN_DISPLAY_NAME` | `LoveProof Admin` | Display name shown for the seeded administrator. |
| `LOVEPROOF_ADMIN_ROLE` | `ADMIN` | Role of the seeded administrator (`ADMIN` or `MODERATOR`). |
| `LOVEPROOF_ADMIN_PASSWORD_HASH` | dev-only BCrypt hash | BCrypt hash (cost 10) of the seeded admin's password. Never a plaintext secret. |
| `LOVEPROOF_CONSENT_TEXT_VERSION` | `v1` | Identifier of the consent wording shown at submission time. |
| `LOVEPROOF_IP_HASH_SALT` | `loveproof-local-salt` | Salt for hashing client IPs. Change it per environment. |
| `LOVEPROOF_TRUST_FORWARDED_FOR` | `false` | Whether `X-Forwarded-For` may be trusted. Leave `false` unless the deployment really sits behind a proxy that sets it: a client that can set it can otherwise choose its own rate-limit bucket. |
| `LOVEPROOF_RATE_LIMIT_SUBMIT_LIMIT` | `5` | Submissions allowed per client per submission window. |
| `LOVEPROOF_RATE_LIMIT_SUBMIT_PERIOD_SECONDS` | `3600` | The submission window, in seconds. |
| `LOVEPROOF_RATE_LIMIT_LOGIN_LIMIT` | `10` | Sign-in attempts allowed per client per login window. |
| `LOVEPROOF_RATE_LIMIT_LOGIN_PERIOD_SECONDS` | `300` | The login window, in seconds. |
| `LOVEPROOF_SECURE_COOKIES` | `false` | Marks the session and CSRF cookies `Secure`. Leave `false` for local HTTP; the `prod` profile sets it to `true` regardless. |
| `LOVEPROOF_STRIPE_SECRET_KEY` | *(empty)* | The Stripe secret API key. Without it the billing endpoints answer `503 BILLING_NOT_CONFIGURED`. Use a restricted key, never the account's full-access secret. |
| `LOVEPROOF_STRIPE_WEBHOOK_SECRET` | *(empty)* | The signing secret of the webhook endpoint. Without it the webhook refuses every delivery with `400 INVALID_WEBHOOK_SIGNATURE`. |
| `LOVEPROOF_STRIPE_PREMIUM_PRICE_ID` | *(empty)* | The `price_…` identifier of the Premium plan in your own Stripe catalogue. |
| `LOVEPROOF_STRIPE_API_BASE_URL` | `https://api.stripe.com` | The Stripe API host. Point it at a local Stripe mock if you have one. |
| `LOVEPROOF_BILLING_RECONCILE_INTERVAL_MS` | `3600000` | How often the reconciliation job re-checks live subscriptions. The webhook is the primary mechanism; this is the backstop for a delivery that never arrived. |
| `LOVEPROOF_BILLING_RECONCILE_INITIAL_DELAY_MS` | `60000` | How long the job waits before its first run, so a freshly started deployment does not immediately call Stripe for every subscription. |
| `LOVEPROOF_MAIL_SMTP_HOST` | *(empty)* | The SMTP host. **Unset by default**, in which case the verification and password-reset emails are **logged** rather than sent — the link is in the backend's output. |
| `LOVEPROOF_MAIL_FROM` | `no-reply@localhost` | The address the outgoing mail comes from. |

### Optional integrations

Both are off until their keys are supplied, and the product works fully without them.

| Feature | What to set | Without it |
|---|---|---|
| **Stripe billing** | The three `LOVEPROOF_STRIPE_*` variables above | The billing page shows the plans and refuses to upgrade with `503 BILLING_NOT_CONFIGURED`. Every other part of the product works. |
| **Real email** | `LOVEPROOF_MAIL_SMTP_HOST`, `LOVEPROOF_MAIL_FROM` and Spring Boot's own `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | Verification and password-reset emails are **logged** rather than sent. The link is in the backend's stdout. |

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
| `POST` | `/api/testimonials` | Submit a testimonial to the original wall. Validated; stored as `PENDING`. |
| `GET` | `/api/testimonials` | The public wall. Paginated, filterable by rating, sortable. |
| `GET` | `/api/testimonials/stats` | Public count and average rating of approved testimonials. |

### Customer — requires a session with the `USER` role

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/app/auth/register` | Create an account. Exempt from CSRF. |
| `POST` | `/api/app/auth/sign-in` | Sign in. Exempt from CSRF. |
| `POST` | `/api/app/auth/sign-out` | Sign out. |
| `GET` | `/api/app/auth/me` | The signed-in account. |
| `POST` | `/api/app/auth/verify-email` | Confirm an address. Exempt from CSRF. |
| `GET` | `/api/app/auth/verify-email` | Confirm an address from the emailed link. Exempt from CSRF. |
| `POST` | `/api/app/auth/forgot-password` | Request a reset link. Exempt from CSRF. |
| `POST` | `/api/app/auth/reset-password` | Set a new password from the reset link. Exempt from CSRF. |
| `POST` | `/api/app/auth/change-password` | Change the password while signed in. |
| `POST` | `/api/app/auth/profile` | Change the display name. |
| `GET` | `/api/app/dashboard?days=` | The account's own totals, a daily series and the recent submissions. `days` is clamped to 1–365 and defaults to 30. |
| `GET` | `/api/app/spaces` | The account's Spaces. |
| `POST` | `/api/app/spaces` | Create a Space. Refused with `409 PLAN_LIMIT_REACHED` past the plan limit. |
| `GET` | `/api/app/spaces/{id}` | One Space. |
| `PUT` | `/api/app/spaces/{id}` | Rename or re-theme a Space. |
| `DELETE` | `/api/app/spaces/{id}` | Delete a Space. |
| `GET` | `/api/app/spaces/themes` | The available themes, with which are premium. |
| `GET` | `/api/app/spaces/{id}/embed` | The embed configuration and the snippet. |
| `PUT` | `/api/app/spaces/{id}/embed` | Change the embed configuration. Premium options are refused on the Free plan. |
| `POST` | `/api/app/spaces/{id}/embed/rotate-token` | Issue a new embed token. |
| `GET` | `/api/app/spaces/{id}/testimonials` | The Space's inbox. |
| `PATCH` | `/api/app/spaces/{id}/testimonials/{tid}` | Approve or reject. |
| `PUT` | `/api/app/spaces/{id}/testimonials/{tid}` | Edit the content or the rating. |
| `DELETE` | `/api/app/spaces/{id}/testimonials/{tid}` | Delete a testimonial from the Space. |
| `GET` | `/api/app/billing` | The billing read model: the plan, the subscription status and the two plans with their limits. |
| `POST` | `/api/app/billing/checkout` | Create a Stripe Checkout session. Answers `503 BILLING_NOT_CONFIGURED` without a Stripe key. |
| `POST` | `/api/app/billing/cancel` | Cancel at the end of the paid period. |
| `POST` | `/api/app/billing/resume` | Resume a cancelled subscription. |
| `POST` | `/api/app/billing/portal` | Open the Stripe Billing Portal. |

### Public Space — anonymous, no session

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/app/public/spaces/{slug}` | The public Space: its title, theme and whether it is accepting submissions. |
| `POST` | `/api/app/public/spaces/{slug}/testimonials` | Submit a testimonial to that Space. |
| `GET` | `/api/app/public/embeds/{token}` | The embed data for a token. |

### Stripe webhook — authenticated by signature, not by session

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/billing/webhook` | Receive a Stripe event. Verified against `Stripe-Signature`; an invalid signature answers `400`. |

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
cd frontend && npm run build  # frontend: type-check + production bundle + budget check
```

**361 backend tests and 222 frontend tests, 0 failures.**

The backend suite runs against a **real PostgreSQL 16 instance via Testcontainers**, so Flyway
migrations, CHECK constraints and partial indexes are exercised for real rather than against an
emulated database. **Docker must be running** for `mvn test`.

`npm run build` also enforces the **bundle budget**: 100 kB gzipped for the entry chunk, 100 kB for
any single chunk and 250 kB for the whole download. The build fails if the budget is exceeded, and
prints the per-file breakdown either way.

The frontend suite includes **axe accessibility checks** over every screen. The `color-contrast`
rule is disabled there because jsdom has no 2D canvas for axe to measure with; contrast is checked
in the real-browser pass instead.

`WallPerformanceTest` fills the table with 1,000 rows — the size a busy self-hosted installation
reaches within a year — and measures the public read. It asserts the *shape* of the answer and the
plan the database chose (an index scan, never a sequential scan), and prints the timings so a
regression shows up in the build log.

The billing tests run **offline**: the Stripe signature is verified against a pinned HMAC vector,
and the webhook is driven with hand-written event bodies, so no test ever needs the network or a
Stripe account.

---

## Security model

There are **three protected surfaces**, and none of them can be switched off — the routes are always
registered, and each one requires the credential its own chain demands:

| Surface | Credential |
|---|---|
| The installation's own admin (`/api/admin/**`) | A session with the `ADMIN` role, checked against `admin_user`. |
| The customer app (`/api/app/**`) | A session with the `USER` role, checked against `app_user`. |
| The Stripe webhook (`/api/billing/**`) | A verified `Stripe-Signature` header. No session. |

**Sessions.** The administrator signs in at `POST /api/admin/session`. The password is checked
against the BCrypt hash in `admin_user`, the session id is regenerated on authentication, and the
browser receives a `SESSION` cookie that is `HttpOnly` and `SameSite=Lax`. The frontend never needs
to read it, and `SameSite=Lax` keeps it off cross-site POSTs — the classic CSRF delivery route.
`DELETE /api/admin/session` invalidates the session server-side *and* expires the cookie, so the
browser does not keep a dead session id around.

Customer sessions work the same way but live in a **different session attribute**, so a customer
session can never be read as an administrator one even by mistake.

**CSRF.** The token is issued in a readable `XSRF-TOKEN` cookie and must be echoed in the
`X-XSRF-TOKEN` header on every state-changing request. The SPA's API client does this
automatically. On the customer chain the exempt paths are the five anonymous account endpoints
(`sign-in`, `register`, `forgot-password`, `reset-password`, `verify-email`) and the whole
`/api/app/public/**` tree — the public Space pages and the public submission endpoint, which are
anonymous and so have no credentials for an attacker to ride on. They are guarded by rate limiting,
a honeypot field and the duplicate-submission fingerprint instead.

**Three filter chains.** The installation has three, and they are deliberately separate:

| Chain | Matches | Trust model |
|---|---|---|
| Admin | everything else | A session with the `ADMIN` role, checked against `admin_user`. |
| Customer | `/api/app/**` | A session with the `USER` role, checked against `app_user`, kept under a **different** session attribute so a customer session can never be read as an administrator one. |
| Billing | `/api/billing/**` | A **Stripe signature**, not a session. Stateless, and CSRF is off because there is no session to protect. |

Because the CSRF filter runs *before* authorization, every endpoint in a chain's `permitAll` list
must also be in its CSRF exemption list — otherwise a legitimate first-time visitor would get a
`403`.

**The webhook is the third CSRF exemption, and it is exempt for a different reason.** It is
authenticated by the `Stripe-Signature` header — an HMAC over the exact bytes Stripe sent, with a
five-minute replay window — so there is no session for a forged request to ride on. An invalid
signature answers `400 INVALID_WEBHOOK_SIGNATURE` and nothing is applied. The same event delivered
twice is applied once: the ledger's primary key on the event id makes the second insert fail, and
the transaction rolls back.

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

**Rate limiting.** The public submission endpoints (both the original wall and the Space pages) and
the admin sign-in endpoint are limited server-side by a continuous-refill token bucket. The bucket
is keyed on a salted SHA-256 hash of the client IP, so the raw address is never stored, and it
starts *full* — a bucket that started empty would refuse the first request from every client.
Exceeding a limit answers `429` with a `Retry-After` header in seconds, and the frontend shows the
server's own message rather than a generic failure. The defaults are 5 submissions per hour and 10
sign-in attempts per five minutes; both are configurable (see [Configuration](#configuration)). The
login limiter returns the same `429` whether or not the account exists, so it cannot be used to
enumerate administrators.

**Response headers.** Every response carries `Content-Security-Policy`, `X-Content-Type-Options:
nosniff`, `X-Frame-Options: DENY` and `Referrer-Policy: same-origin`. The CSP is strict —
`default-src 'self'`, `script-src 'self'`, `object-src 'none'`, `frame-ancestors 'none'` — with
`style-src 'self' 'unsafe-inline'` as the one concession, because React sets a handful of element
styles inline. `Strict-Transport-Security` is only sent when the deployment is actually HTTPS:
sending it over plain HTTP would let an attacker strip it.

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

## Plans and limits

| | Free | Premium |
|---|---|---|
| Spaces | 3 | 25 |
| Testimonials per Space | 100 | 1000 |
| Themes | Standard | Premium themes |
| Embed | Masonry, light and dark | Carousel, background colour, animation |

The limits are enforced **on the server**, not in the interface — the frontend hides what the plan
does not allow, but the API refuses it regardless.

**The plan is a pure function of the subscription status.** `PREMIUM ⇔ status ∈ {active, trialing,
past_due}`. Cancelling sets `cancel_at_period_end` and keeps Premium until the period actually
ends; a failed renewal shows "past due" and still keeps Premium; a final failure (`unpaid`) or the
end of a cancelled period (`canceled`) drops the account to Free. Nothing but a verified Stripe
webhook — or the reconciliation job, which uses the same transition method — ever changes a plan.

**A downgrade never deletes data.** An account that had 10 Spaces and is downgraded keeps all 10.
The three oldest (by creation date, with the id as a deterministic tie-break) stay editable; the
rest become **read-only** — viewable, but no edits, no deletions, no new submissions, and no new
submissions accepted on their public page either.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| **"Registration failed — No endpoint matches this request"** | The backend is running an **old build** from before the customer surface existed, so every `/api/app/**` route 404s. | Restart the backend. `mvn spring-boot:run` does not hot-reload — a backend started before a code change keeps serving the old classes. To check which build you have: `curl -X POST localhost:8080/api/app/auth/register -H 'Content-Type: application/json' -d '{}'` answers `400 VALIDATION_FAILED` on a current build and `404` on a stale one. |
| The dashboard says "Your session has expired" | The session was not established. | Sign out and sign in again; if it persists, restart the backend so the current build is loaded. |
| The backend exits with `Port 8080 was already in use` | Something else holds the port. | Stop it, or run the backend on another port with `SERVER_PORT=8099` and point the SPA at it with `VITE_PROXY_TARGET=http://localhost:8099`. |
| The backend exits with a Flyway or datasource error | The database is not up yet, or the credentials differ. | `docker compose ps` and `docker compose exec db pg_isready -U loveproof -d loveproof`. Check `SPRING_DATASOURCE_*` in `.env`. |
| The SPA shows 404s or a blank page on `/app` | The backend is not running, so the Vite proxy has nothing to proxy to. | Check <http://localhost:8080/api/health> first. |
| "This account's email address is not verified yet" | The address was never confirmed. | The verification link is in the backend's log output (mail is logged by default). |
| A submission is refused with `429` | The rate limit was hit — 5 submissions per hour per client. | Wait, or raise `LOVEPROOF_RATE_LIMIT_SUBMIT_LIMIT`. |
| The upgrade button answers `503 BILLING_NOT_CONFIGURED` | The Stripe keys are not set. | Set the three `LOVEPROOF_STRIPE_*` variables and restart the backend. |
| The plan stays Free after paying | The webhook never arrived. | Run `stripe listen --forward-to localhost:8080/api/billing/webhook`, or wait for the reconciliation job. |
| A Space refuses edits after a downgrade | It is one of the Spaces beyond the Free limit, so it is read-only. | Expected. Upgrade, or delete the newer Spaces. |
| `mvn test` fails with a Testcontainers error | Docker is not running. | Start Docker; the suite needs a real PostgreSQL 16. |

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
