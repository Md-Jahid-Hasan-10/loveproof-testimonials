# LoveProof

A self-hosted, multi-tenant testimonial collection SaaS.

A customer signs up, creates a **Space** — a page with its own public URL that collects
testimonials — and curates what comes in. Each Space can be embedded on the customer's own website.
Plans are enforced on the server, and billing runs through Stripe.

The original single-tenant wall and its moderation inbox still exist alongside it; the two surfaces
share the database and the deployment but not their sessions.

| | |
|---|---|
| Frontend | React 18 · TypeScript (strict) · Vite 6 · Tailwind CSS 3.4 · Vitest 3 · jsdom |
| Backend | Java 21 · Spring Boot 3.5 · Spring Data JPA · Flyway 11 · Spring Security 6 · Actuator |
| Database | PostgreSQL 16 |
| Billing | Stripe (Checkout + Billing Portal + signed webhooks) |
| Build | Maven 3.8+ · npm 18.18+ |

> **Status.** Every phase of the PRD build order is implemented and verified, **except Google
> sign-in**, which has the data model but no sign-in path — see
> [Completed and incomplete features](#completed-and-incomplete-features).
> **361 backend tests, 222 frontend tests, 0 failures.**

---

## Table of contents

- [What LoveProof does](#what-loveproof-does)
- [Architecture](#architecture)
- [Running it](#running-it) — database, backend, frontend
- [Configuration](#configuration) — environment variables
- [API](#api) — every endpoint
- [Completed and incomplete features](#completed-and-incomplete-features)
- [Testing](#testing) — actual commands and results
- [Puku agentic techniques](#puku-agentic-techniques)
- [Screenshots](#screenshots)
- [Known limitations](#known-limitations)
- [Future improvements](#future-improvements)
- [Security model](#security-model)
- [Plans and limits](#plans-and-limits)
- [Troubleshooting](#troubleshooting)
- [Repository layout](#repository-layout)
- [Commands](#commands)
- [Documentation](#documentation)

---

## What LoveProof does

There are three separate surfaces in one application.

**1. The customer app (`/app`).** A customer registers, signs in, and manages **Spaces**. A Space
is a page with its own public slug (`/s/<slug>`) where visitors submit testimonials. The owner
curates what arrives — each testimonial has a `visible` flag and a `wall_of_love` flag, and only
`visible` + `wall_of_love` testimonials are published in the embed. The owner generates a
`<script>` snippet that renders a "Wall of Love" widget on any site. The dashboard shows the
account's totals and a per-day chart.

**2. The public Space pages (`/s/<slug>`).** Anonymous visitors. They read the Space's theme and
submit a testimonial — name, optional company/title, optional website or social link, a 1–5 star
rating, free text, and a consent checkbox. Rate limited, honeypot-guarded, and fingerprinted
against duplicate submission.

**3. The original single-tenant wall (`/wall`, `/admin`).** A public testimonial wall with an
administrator moderation inbox, exactly as the first version of this application was. It is
untouched by the multi-tenant work and still runs on the same database and deployment.

**Billing** runs through Stripe. Free accounts get 3 Spaces and 100 testimonials per Space;
Premium gets 25 and 1000, plus premium themes and premium embed options. The limits are enforced
**on the server** — the frontend hides what the plan does not allow, but the API refuses it
regardless.

**The plan is a pure function of the subscription status.** `PREMIUM ⇔ status ∈ {active,
trialing, past_due}`. Cancelling at period end keeps Premium until the period actually ends; a
failed renewal shows "past due" and still keeps Premium; a final failure drops the account to
Free. Nothing but a verified Stripe webhook — or the reconciliation job, which calls the same
transition method — ever changes a plan.

**A downgrade never deletes data.** An account with 10 Spaces that is downgraded keeps all 10. The
three oldest stay editable; the rest become **read-only** — viewable, but no edits, no deletions,
no new submissions, and their public pages stop accepting them too.

---

## Architecture

### The three filter chains

The application has three `SecurityFilterChain` beans and they are deliberately separate. None
can be switched off — the routes are always registered, and each requires the credential its own
chain demands.

| Chain | Order | Matches | Trust model |
|---|---|---|---|
| **Admin** | default (last) | everything else | A session with the `ADMIN` role, checked against `admin_user`. |
| **Customer** | `@Order(1)` | `/api/app/**` | A session with the `USER` role, checked against `app_user`, kept under a **different** session attribute (`LOVEPROOF_APP_SECURITY_CONTEXT`) so a customer session can never be read as an administrator one. |
| **Billing** | `@Order(2)` | `/api/billing/**` | A **Stripe signature**, not a session. Stateless; CSRF off because there is no session to protect. |

Because the CSRF filter runs *before* authorization, every endpoint in a chain's `permitAll` list
must also be in its CSRF exemption list — otherwise a legitimate first-time visitor would get a
`403`. This invariant holds in all three chains and is asserted in the test suite.

### Request flow

```
Browser ──/api/**──► Vite proxy ──► Spring Security
                                          │
                    ┌─────────────────────┼─────────────────────┐
                    ▼                     ▼                     ▼
              Admin chain          Customer chain          Billing chain
              (session, ADMIN)      (session, USER)     (Stripe-Signature)
                    │                     │                     │
                    ▼                     ▼                     ▼
              AdminUserDetails     AppUserDetails        WebhookService
                    │                     │                     │
                    └──────────┬──────────┘                     │
                               ▼                                ▼
                        PostgreSQL 16 ◄──── StripeEvent ledger
                          (Flyway)
```

### Layers

| Layer | Responsibility |
|---|---|
| `web/` | Controllers, DTO records, the shared error envelope. Controllers are thin — they validate, delegate, and translate domain exceptions into HTTP status codes via `GlobalExceptionHandler`. |
| `service/` | All business rules: authentication, plan limits, curation, billing transitions, rate limiting, fingerprinting. This is where the invariants live. |
| `domain/` | JPA entities and enums. `AppUser.applySubscriptionState` is the single place a plan can change, which is what stops the webhook and the reconciliation job from disagreeing. |
| `repository/` | Spring Data JPA repositories. Every query is parameterized. |
| `config/` | The three security chains, `ClockConfig` (an injectable `Clock`, so time-dependent rules are testable), Stripe properties, scheduling. |

### Data model

| Table | Holds |
|---|---|
| `admin_user` | The original administrator accounts. |
| `testimonial` | The original single-tenant wall's testimonials, with a moderation status. |
| `moderation_action` | The append-only audit trail of every moderation decision. |
| `app_user` | Customer accounts: password hash, email-verified flag, plan, Stripe ids, subscription status. |
| `space` | A customer's collection page: slug, theme, which form fields are enabled, whether each is required. |
| `space_testimonial` | A submission in a Space: `visible`, `wall_of_love`, rating, content, consent record. |
| `space_embed_config` | Per-Space embed settings — layout, background colour, animation, and the embed token. |
| `stripe_event` | The webhook idempotency ledger. The primary key on the event id is what makes a replay a no-op. |
| `auth_token` | Email-verification and password-reset tokens, stored as SHA-256 hashes, single-use and expiring. |

Full detail: [`docs/data-model.md`](docs/data-model.md).

### Key design decisions

- **No `stripe-java`.** The SDK is ~15 MiB plus an unmanaged Gson and buys six REST calls. Implementing
  the HTTP client and the HMAC-SHA256 signature verification directly makes the hardest criterion —
  *"webhooks with invalid signatures are rejected (400)"* — testable offline against a pinned vector,
  with no network and no SDK to mock.
- **The plan is derived, never stored independently.** `AppUser.applySubscriptionState(status, …)`
  sets the plan from the status. One method, two callers (webhook, reconciler), so they cannot disagree.
- **`Clock` is injected.** Time-dependent behaviour — token expiry, rate-limit windows, billing
  period ends — is tested against a fixed clock rather than the wall clock.
- **The webhook takes `byte[]`, not `String`.** The signature is computed over the exact bytes
  Stripe sent. A decoded-and-re-encoded body is byte-identical only for valid UTF-8.

---

## Running it

### Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java | 21 | Required by the backend build. |
| Maven | 3.8+ | Backend build tool. |
| Node.js | 18.18+ (20 recommended) | Frontend build tool. See [Node version note](#node-version-note). |
| Docker | any recent | Runs PostgreSQL 16 and the backend's Testcontainers suite. |
| Docker Compose | v2+ | Included with modern Docker Desktop / Engine. |

### 1. The database

```bash
cp .env.example .env     # first time only
docker compose up -d
```

PostgreSQL 16 starts on port 5432 with the credentials from `.env`. Check it:

```bash
docker compose exec db pg_isready -U loveproof -d loveproof
```

Flyway applies the migrations automatically when the backend starts, so there is **no separate
migration step**. Hibernate runs with `ddl-auto: validate` in every profile — Flyway owns the
schema.

### 2. The backend

```bash
cd backend && mvn spring-boot:run
```

The API comes up on <http://localhost:8080>. Check it:

```bash
curl http://localhost:8080/api/health
```

### 3. The frontend

```bash
cd frontend && npm install && npm run dev
```

The SPA comes up on <http://localhost:5173> and proxies `/api` to `localhost:8080`. Then open
<http://localhost:5173>.

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
   submission is there. It is **visible but not on the Wall of Love** by default — publish it by
   turning on the Wall of Love toggle, or edit its content and rating first.
5. **Embed it.** Click **Wall of Love** on the Space page. Copy the snippet and paste it into a
   `<script>` tag on any page; the widget renders the published testimonials there.
6. **Check the analytics.** The dashboard shows your Space count, the testimonials collected, the
   unique submitters and a per-day chart, with a range switch between 7 days, 30, 90 and a year.
7. **Try the plan limits.** A Free account may have 3 Spaces and 100 testimonials per Space. Try to
   create a fourth, and the API refuses with `409 PLAN_LIMIT_REACHED` — the interface hides the
   button, but the server is what actually enforces it.

The original single-tenant wall is still there at `/wall`, with the moderator's inbox at `/admin`.

> **Note on the original wall.** The legacy `/wall` surface *does* have a moderation workflow —
> pending, approved, rejected, soft-deleted — because it is the original single-tenant app. The
> per-Space inbox (`/app/spaces/{id}/testimonials`) deliberately **does not**: PRD §4.7 states there
> is no approval step, and a Space testimonial's `visible` flag is the only gate.

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

---

## Configuration

All configuration is externalised through environment variables with local defaults.
[`.env.example`](.env.example) is the annotated template.

### Database and server

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

### Application

| Variable | Default | Purpose |
|---|---|---|
| `LOVEPROOF_APP_BASE_URL` | `http://localhost:5173` | The public origin of the installation. The embed script and embed data are served from it, so it must match the host that actually serves the backend. **Set this in every deployment** — it is the one value a self-hoster is most likely to get wrong. |
| `LOVEPROOF_ADMIN_USERNAME` | `admin` | Username seeded into `admin_user` by the `V2` migration. |
| `LOVEPROOF_ADMIN_DISPLAY_NAME` | `LoveProof Admin` | Display name shown for the seeded administrator. |
| `LOVEPROOF_ADMIN_ROLE` | `ADMIN` | Role of the seeded administrator (`ADMIN` or `MODERATOR`). |
| `LOVEPROOF_ADMIN_PASSWORD_HASH` | dev-only BCrypt hash | BCrypt hash (cost 10) of the seeded admin's password. Never a plaintext secret. |
| `LOVEPROOF_CONSENT_TEXT_VERSION` | `v1` | Identifier of the consent wording shown at submission time. |
| `LOVEPROOF_IP_HASH_SALT` | `loveproof-local-salt` | Salt for hashing client IPs. Change it per environment. |
| `LOVEPROOF_SECURE_COOKIES` | `false` | Marks the session and CSRF cookies `Secure`. Leave `false` for local HTTP; the `prod` profile sets it to `true` regardless. |

### Rate limiting and client address

| Variable | Default | Purpose |
|---|---|---|
| `LOVEPROOF_TRUST_FORWARDED_FOR` | `false` | Whether `X-Forwarded-For` may be trusted. Leave `false` unless the deployment really sits behind a proxy that sets it: a client that can set it can otherwise choose its own rate-limit bucket. |
| `LOVEPROOF_RATE_LIMIT_SUBMIT_LIMIT` | `5` | Submissions allowed per client per submission window. |
| `LOVEPROOF_RATE_LIMIT_SUBMIT_PERIOD_SECONDS` | `3600` | The submission window, in seconds. |
| `LOVEPROOF_RATE_LIMIT_LOGIN_LIMIT` | `10` | Sign-in attempts allowed per client per login window. |
| `LOVEPROOF_RATE_LIMIT_LOGIN_PERIOD_SECONDS` | `300` | The login window, in seconds. |

### Stripe billing

| Variable | Default | Purpose |
|---|---|---|
| `LOVEPROOF_STRIPE_SECRET_KEY` | *(empty)* | The Stripe secret API key. Without it the billing endpoints answer `503 BILLING_NOT_CONFIGURED`. Use a restricted key, never the account's full-access secret. |
| `LOVEPROOF_STRIPE_WEBHOOK_SECRET` | *(empty)* | The signing secret of the webhook endpoint. Without it the webhook refuses every delivery with `400 INVALID_WEBHOOK_SIGNATURE`. |
| `LOVEPROOF_STRIPE_PREMIUM_PRICE_ID` | *(empty)* | The `price_…` identifier of the Premium plan in your own Stripe catalogue. |
| `LOVEPROOF_STRIPE_API_BASE_URL` | `https://api.stripe.com` | The Stripe API host. Point it at a local Stripe mock if you have one. |
| `LOVEPROOF_BILLING_RECONCILE_INTERVAL_MS` | `3600000` | How often the reconciliation job re-checks live subscriptions. The webhook is the primary mechanism; this is the backstop for a delivery that never arrived. |
| `LOVEPROOF_BILLING_RECONCILE_INITIAL_DELAY_MS` | `60000` | How long the job waits before its first run, so a freshly started deployment does not immediately call Stripe for every subscription. |

### Email

| Variable | Default | Purpose |
|---|---|---|
| `LOVEPROOF_MAIL_SMTP_HOST` | *(empty)* | The SMTP host. **Unset by default**, in which case the verification and password-reset emails are **logged** rather than sent — the link is in the backend's output. |
| `LOVEPROOF_MAIL_FROM` | `no-reply@localhost` | The address the outgoing mail comes from. |
| `SPRING_MAIL_USERNAME` | *(empty)* | Spring Boot's own SMTP credentials. Only needed when the host above is set. |
| `SPRING_MAIL_PASSWORD` | *(empty)* | Spring Boot's own SMTP credentials. |

### Optional integrations

Both are off until their keys are supplied, and the product works fully without them.

| Feature | What to set | Without it |
|---|---|---|
| **Stripe billing** | The three `LOVEPROOF_STRIPE_*` variables above | The billing page shows the plans and refuses to upgrade with `503 BILLING_NOT_CONFIGURED`. Every other part of the product works. |
| **Real email** | `LOVEPROOF_MAIL_SMTP_HOST`, `LOVEPROOF_MAIL_FROM` and `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | Verification and password-reset emails are **logged** rather than sent. The link is in the backend's stdout. |

> The seeded administrator's default password is `loveproof`. It exists for local development only
> — override `LOVEPROOF_ADMIN_PASSWORD_HASH` with a BCrypt hash of your own in any shared
> environment. The `V3` migration resynchronises the seeded row whenever the configured hash
> differs from the stored one.

---

## API

Errors use one envelope for every failure: `{ code, message, fieldErrors, timestamp, path }`.
Full request and response examples: [`docs/api.md`](docs/api.md).

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
| `POST` | `/api/app/auth/register` | Create an account. Signs the customer in. Exempt from CSRF. |
| `POST` | `/api/app/auth/sign-in` | Sign in. Exempt from CSRF. |
| `POST` | `/api/app/auth/sign-out` | Sign out. |
| `GET` | `/api/app/auth/me` | The signed-in account. |
| `POST` | `/api/app/auth/verify-email` | Confirm an address. Does **not** sign the account in. Exempt from CSRF. |
| `GET` | `/api/app/auth/verify-email` | Whether a verification link is still good. Exempt from CSRF. |
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
| `PATCH` | `/api/app/spaces/{id}/testimonials/{tid}` | Set the curation flags: visibility, favourite, Wall of Love. |
| `PUT` | `/api/app/spaces/{id}/testimonials/{tid}` | Edit the content of a testimonial. |
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

---

## Completed and incomplete features

Measured against [`docs/PRD-testimonial-saas.md`](docs/PRD-testimonial-saas.md) §9 (build order) and
§10 (acceptance criteria).

### Complete and verified

| PRD area | What is implemented | Where |
|---|---|---|
| §4.1 Authentication | Email/password registration, sign-in, sign-out, change password, forgot/reset password, email verification, profile display name. Registering signs the customer in. | `AppAuthController`, `AuthService` |
| §4.2 Spaces management | Create, list, rename, re-theme, delete. Owner-scoped. | `AppSpaceController`, `SpaceService` |
| §4.3 Form configuration | Per-Space toggles for company, job title, social link, website — each independently optional or required. | `Space`, `SpaceWizardPage` |
| §4.4 Themes | Standard and premium themes, with premium ones refused on the Free plan. | `ThemeCatalog` |
| §4.5 Public submission | The public Space page, the full form, validation on both sides, honeypot, rate limiting, duplicate fingerprinting, consent recording. | `SpaceSubmissionService`, `SpaceSubmitPage` |
| §4.6 Dashboard | Totals, a per-day series with explicit zero buckets, a range switch, recent submissions. | `DashboardAnalyticsService`, `DashboardPage` |
| §4.7 Testimonial inbox | The per-Space inbox with the curation actions: visible, favourite, Wall of Love, edit content, delete. | `AppSpaceInboxController`, `SpaceInboxService` |
| §4.8 Wall of Love | The embed widget, the generated snippet, the embed token, the layout/theme/colour/animation options, premium gating. | `EmbedService`, `SpaceEmbedPage` |
| §4.9 Billing | Stripe Checkout, the Billing Portal, cancel-at-period-end, resume, signed webhooks, the idempotency ledger, the reconciliation job, and server-side plan limits. | `AppBillingController`, `WebhookService`, `CheckoutService` |
| §4.10 Account settings | Display name, password change, subscription summary. | `SettingsPage` |
| §7 Limits | 3 / 25 Spaces, 100 / 1000 testimonials per Space, premium embed options and themes refused on Free — **all enforced server-side**. | `PlanLimits`, `PlanCatalog`, `SpaceService` |
| §6 Business rules | A downgrade keeps all data; the three oldest Spaces stay editable and the rest are read-only across every mutating path, including the public submission endpoint. | `SpaceService.isEditable`, `DowngradeApiTest` |
| §10 Cross-tenant isolation | User A can never read or modify User B's Spaces or testimonials. Every Space-scoped call resolves through the authenticated owner. | `SpaceService`, `SpaceInboxService` |

### Incomplete

| Item | State | Impact |
|---|---|---|
| **Google sign-in** | **Not implemented.** The data model supports it — `app_user.auth_provider`, `app_user.google_subject`, a unique index, and `AuthService.signInWithGoogle(email, displayName, googleSubject)` with unit tests. But there is **no HTTP entry point**: no `spring-security-oauth2-client` dependency, no `oauth2Login` success handler, no `/oauth2/**` route, no `LOVEPROOF_GOOGLE_*` configuration and no frontend button. The method is called from nowhere in main source. | PRD §4.1 and the first acceptance criterion say "register by email **or Google**". Only the email path exists. Everything else in the product is unaffected. |
| **Unverified accounts can create Spaces** | `SpaceService.create` checks the Space-count limit but never `owner.isEmailVerified()`. | PRD §4.1 says unverified users "cannot create Spaces". In practice the verification email is advisory: an unverified account can create up to 3 Spaces (25 on Premium). |
| **The webhook cannot resolve a first purchase** | `HttpStripeGateway` writes `subscription_data[metadata][account_id]` to Stripe, but `retrieveSubscription` discards the `metadata` object and `SubscriptionSnapshot` has no field to carry it — so both webhook call sites resolve the account with `customerId = null`. | On a **first** purchase the subscription id is not yet stored on the account, so `customer.subscription.created` resolves to `NO_ACCOUNT` and the customer stays on Free until the reconciliation job runs. Existing subscriptions, whose id is already stored, work correctly. |
| **The dashboard's recent-submissions query is unbounded** | `SpaceTestimonialRepository.findRecentAcrossSpaces` declares a `limit` parameter that its JPQL never references. | The query returns every testimonial across every Space of the account, ordered, where 10 are displayed. On a Premium account that is up to 25,000 rows loaded per dashboard page load. `docs/data-model.md` documents the query as `LIMIT 10` — the code does not do that. |
| **Rate-limit buckets are never reclaimed** | `RateLimiterService.evictIdleBuckets` exists but nothing calls it. | The in-memory bucket map grows by one entry per client IP that ever submits, forever. |
| **The reconciler does not downgrade an account Stripe has forgotten** | The job catches every `RuntimeException` and only logs a warning. | If Stripe returns 404 for a subscription — deleted outside the product, or a cancellation whose webhook never arrived — the account stays on Premium indefinitely. |

### Deliberately out of scope

Per PRD §2 (Non-Goals): video or image testimonials; workspaces, teams and roles (one user = one
account); a drag-and-drop form builder and custom fields; custom domains, white-label and agency
features; integrations such as Zapier; AI features and social import; notifications beyond auth
and Stripe emails; account deletion; social login providers other than Google; annual billing and
free trials.

Two further decisions were taken deliberately:

- **No CAPTCHA.** The PRD §4.5 mentions Turnstile; this was overridden. The public endpoints are
  guarded by a honeypot field, rate limiting and a duplicate-submission fingerprint instead.
- **The legacy single-tenant wall stays.** `/wall` and `/admin` were the original application and
  remain in place, sharing the database and the deployment. They are not part of the PRD.

---

## Testing

### Actual commands and results

Run from the repository root. These are the commands and the numbers from the most recent full
run of this repository.

| # | Command | Result |
|---|---|---|
| 1 | `cd backend && mvn -o clean test` | **Tests run: 361, Failures: 0, Errors: 0, Skipped: 0** — `BUILD SUCCESS` (1 m 25 s) |
| 2 | `cd frontend && npm run lint` | Clean — ESLint reported no problems. |
| 3 | `cd frontend && npx vitest run` | **Test Files 22 passed (22), Tests 222 passed (222)** (9.84 s) |
| 4 | `cd frontend && npm run build` | Type-check passed; **101.1 kB gzip total**, within the 250 kB budget — `Within budget: the whole download is 101.1 kB of the 250.0 kB allowed.` |

> A first attempt at command 1 in this session failed with **230 errors**, all of them
> `FileNotFoundException: class path resource [...Test.class] cannot be opened`. That was a stale
> `target/` directory — the compiled test classes had gone missing — not a code regression;
> `mvn -o clean test` passed 361/361 straight afterwards. If you see that error, run `mvn clean`
> first.

### What the suites actually cover

- **The backend suite runs against a real PostgreSQL 16 instance via Testcontainers**, so Flyway
  migrations, CHECK constraints and partial indexes are exercised for real rather than against an
  emulated database. **Docker must be running** for `mvn test`.
- **`npm run build` enforces the bundle budget**: 100 kB gzipped for the entry chunk, 100 kB for
  any single chunk and 250 kB for the whole download. The build fails if the budget is exceeded, and
  prints the per-file breakdown either way.
- **The frontend suite includes axe accessibility checks** over every screen. The `color-contrast`
  rule is disabled there because jsdom has no 2D canvas for axe to measure with; contrast is
  checked in the real-browser pass instead.
- **`WallPerformanceTest` fills the table with 1,000 rows** — the size a busy self-hosted
  installation reaches within a year — and measures the public read. It asserts the *shape* of the
  answer and the plan the database chose (an index scan, never a sequential scan), and prints the
  timings so a regression shows up in the build log.
- **The billing tests run offline**: the Stripe signature is verified against a pinned HMAC vector,
  and the webhook is driven with hand-written event bodies, so no test ever needs the network or a
  Stripe account.
- **Authorization is tested from the outside.** The API tests call the endpoints as the wrong
  user, as an anonymous user, and against another account's ids, and assert the refusal — not just
  the happy path.

### Testing philosophy

Every fix in this repository was **mutation-tested**: the fix is reverted, the new test is run,
and it must fail. A test that passes both with and without the fix is not evidence of anything.
The same discipline applies to documentation — every endpoint path in this README was
cross-checked against the controllers' `@*Mapping` annotations, the CSRF exemption list against
`AppSecurityConfig`, the frontend routes against `App.tsx`, and the button labels against the
components that render them.

---

## Puku agentic techniques

This project was built with Puku CLI. The techniques that did the real work, and where they
appear:

### 1. Plan mode with a written implementation plan

`docs/implementation-plan.md` (the original single-tenant build) and the Phase 7 billing plan
were both produced in plan mode, reviewed by the user, and then executed step by step with a
report after each phase. Every commit in this repository corresponds to a plan step, and the
commit messages name the step rather than the diff.

### 2. A dedicated code-reviewer sub-agent

`.puku-cli/agents/loveproof-code-reviewer.md` — a project-scoped reviewer with **persistent,
file-based memory** at `.puku-cli/agent-memory/loveproof-code-reviewer/`. It runs with a
read-only mandate: it never edits, never commits, and never claims a problem is fixed.

It found the defects listed under [Incomplete](#incomplete) — the unreachable Google path, the
webhook's account-resolution gap, the missing unverified-user gate, the unbounded query, the
unreclaimed rate-limit buckets and the reconciler's swallowed 404 — none of which the test suite
caught, because the suite was green while all six were present.

### 3. Real-browser verification with headless Chrome

The backend's behaviour was checked in a real browser over the DevTools Protocol, driving
`Runtime.evaluate` with `awaitPromise`. The register form was filled, submitted, redirected to
`/app`, and the dashboard was confirmed to render with no session errors. This is what caught the
"your session has expired" bug that the unit tests had passed straight over, because the cause was
in the controller, not in the component under test.

### 4. Mutation testing as a proof of test quality

Each of the two bug fixes in the final session was verified by reverting it and confirming the new
test fails. See [Testing philosophy](#testing-philosophy).

### 5. Verification against a running instance

The full product path was exercised against a live backend and database: register → sign in →
create a Space → visitor submission → owner's inbox → dashboard analytics → `PLAN_LIMIT_REACHED`
on the 4th Space → `503 BILLING_NOT_CONFIGURED` on checkout → `400 INVALID_WEBHOOK_SIGNATURE` on
a bad webhook signature. The test accounts were deleted from the database afterwards.

### 6. Skills for repeatable workflows

`.puku-cli/skills/verify-feature/SKILL.md` — a project skill that verifies a feature against the
requirements by extracting its acceptance criteria, running the repository's own documented test
commands, checking both the happy path and the error cases, and reporting the commands executed
with their actual output. It exists because the reviewer's findings were exactly the failures this
workflow is designed to surface.

### 7. Persistent memory for the project

`.puku-cli/agent-memory/` holds the reviewer's accumulated knowledge of this codebase, so a later
review starts from what earlier reviews already established rather than re-deriving it.

---

## Screenshots

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

> The screenshots cover the **original single-tenant wall and its admin surface**. The customer app
> (`/app`) — the dashboard, the Space wizard, the Space inbox, the billing page and the account
> settings — has no screenshots yet.

---

## Known limitations

**Functional**

- **Google sign-in does not exist.** The schema and the service method are there; the sign-in path
  is not. PRD §4.1's "register by email or Google" is half-delivered.
- **Unverified accounts can create Spaces.** The verification email is advisory rather than a gate.
- **A first Stripe purchase does not upgrade the account immediately.** The webhook cannot resolve
  the account until the subscription id has been stored, so the upgrade waits for the reconciliation
  job. This is the most commercially significant defect in the build.
- **The dashboard's recent-submissions query is unbounded** and will slow down as an account grows.
- **Rate-limit buckets are never reclaimed**, so the limiter's memory grows with the number of
  distinct client addresses ever seen.
- **The reconciler leaves an account on Premium when Stripe no longer knows its subscription.**

**Validation**

- **The backend does not validate the format of `website` or `socialLink`.** Both are only length-
  checked. The frontend does validate format, but a direct API client can store anything. React
  escapes on render, so this is not currently an XSS vector — but the data becomes dangerous the
  moment any consumer renders those fields as links.
- **Disabled form fields are silently dropped rather than rejected.** A submitter who sends a
  `jobTitle` to a Space that disabled it gets `201` and no indication the value was discarded.
  PRD §4.3 says "rejected if submitted".
- **Disabled fields and the required-field check interact oddly**: a disabled field is never
  required, so a Space with every optional field disabled accepts a nearly empty submission.

**Operational**

- **Node 18 is end-of-life.** The frontend is pinned to versions that support it so it builds on
  the runtime available here. See [Node version note](#node-version-note).
- **Mail is logged, not sent, by default.** Set `LOVEPROOF_MAIL_SMTP_HOST` to send real email.
- **The Stripe integration is untested against the live Stripe API.** Every billing test runs
  offline against a pinned signature vector and hand-written event bodies. The Stripe *dashboard*
  flow was exercised manually, but no automated test talks to Stripe.
- **The in-memory rate limiter does not work across multiple instances.** Each instance keeps its
  own buckets, so a deployment with N instances allows N times the configured limit.
- **The legacy `/wall` and `/admin` surfaces have no multi-tenant isolation** — by design, they are
  the original single-tenant app.
- **The reviewer's report was static.** It did not run the build or the suites, so its test counts
  were unverified. The numbers in [Testing](#actual-commands-and-results) are from an actual run.

---

## Future improvements

**Fixing what is incomplete**

1. **Resolve the account in the checkout webhook.** Carry `metadata.account_id` (or the customer id)
   through `SubscriptionSnapshot` and use it as the fallback in `findAccount`. Add a regression
   test with a subscription id that is *not* pre-stored on the account. This is the highest-value
   fix in the list — it is on the revenue path.
2. **Implement Google sign-in**, or remove `signInWithGoogle` and the `GOOGLE` provider vocabulary
   and correct the PRD and `docs/data-model.md` to match. Right now the documentation describes a
   feature the code does not have.
3. **Gate Space creation on `isEmailVerified()`**, with a stable error code such as
   `EMAIL_NOT_VERIFIED`.
4. **Bound the recent-submissions query** with a `Pageable`, so the dashboard reads 10 rows rather
   than every row.
5. **Schedule `evictIdleBuckets`** on a `@Scheduled` task, and distinguish "Stripe says this
   subscription does not exist" from "transient API failure" in the reconciler.
6. **Validate URL format server-side** on `website` and `socialLink`, and either reject or
   explicitly document the behaviour for disabled fields.

**Beyond the current scope**

7. **Move the rate limiter to a shared store** so the limit holds across instances, and key the
   buckets by something that survives a restart.
8. **Add a live Stripe integration test** behind a separate Maven profile, so the HTTP client
   itself — not just the signature verification — is covered.
9. **Screenshot the customer app** so the README covers both surfaces.
10. **Add contract tests** that assert the frontend's TypeScript types and the backend's DTO records
    agree, which would have caught the `UpdateSpacePayload` drift (it omits `fieldJobTitle` and
    `fieldJobTitleRequired`, which the backend accepts and the page sends anyway).
11. **Batch the Space-list query.** `AppSpaceController.list()` calls `isEditable` per Space, each
    issuing its own query — 25 round trips at the Premium limit.
12. **Reconcile the documentation set.** `docs/requirements.md` and `docs/implementation-plan.md`
    are the *original single-tenant* specification, while `docs/PRD-testimonial-saas.md` is the
    current multi-tenant one. `CLAUDE.md` points at the former as the source of truth. Either
    retitle them explicitly as the historical spec or repoint the references.
13. **Upgrade to Node 20+**, which would allow newer Vite/Vitest/Router releases and clear the
    remaining `npm audit` advisories.

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

The plan is a pure function of the subscription status, as described in
[What LoveProof does](#what-loveproof-does). A downgrade never deletes data: the three oldest
Spaces (by creation date, with the id as a deterministic tie-break) stay editable and the rest
become read-only — viewable, but no edits, no deletions, no new submissions, and no new submissions
accepted on their public page either.

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
| The plan stays Free after paying | The webhook could not resolve the account — a known defect on a **first** purchase — or the webhook never arrived. | Run `stripe listen --forward-to localhost:8080/api/billing/webhook`, or wait for the reconciliation job. |
| A Space refuses edits after a downgrade | It is one of the Spaces beyond the Free limit, so it is read-only. | Expected. Upgrade, or delete the newer Spaces. |
| `mvn test` fails with a Testcontainers error | Docker is not running. | Start Docker; the suite needs a real PostgreSQL 16. |
| `mvn test` fails with `FileNotFoundException: class path resource [...Test.class] cannot be opened` | A stale `target/` directory — the compiled test classes went missing. | `mvn clean test`. This is not a code regression. |

---

## Node version note

The frontend is pinned to versions that support **Node 18** (Vite 6, Vitest 3, jsdom 26,
Tailwind 3.4) so it builds on the runtime available in this environment. Node 18 is end-of-life;
`.nvmrc` specifies Node 20 as the recommended version. Upgrading to Node 20+ would allow newer
Vite/Vitest/Router releases and clear the remaining `npm audit` advisories, which currently have no
Node 18-compatible fix.

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
│       │   │                 #   StripeProperties, SecurityHeadersConfig, SchedulingConfig,
│       │   │                 #   MailConfiguredCondition/MailNotConfiguredCondition
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
├── .puku-cli/                # ✓ Puku CLI config — the code-reviewer agent, its memory, the
│                             #   verify-feature skill, and the local permission allowlist
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
| `mvn clean test` | Wipe `target/` and run the suite — use this if you see `FileNotFoundException` errors about missing `.class` files. |
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
| `npm run build` | Type-check and produce a production bundle in `dist/`, then check the bundle budget. |
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

## Documentation

| Document | Contents |
|---|---|
| [`docs/PRD-testimonial-saas.md`](docs/PRD-testimonial-saas.md) | **The current specification.** The multi-tenant product: roles, functional requirements, business rules, subscription limits, MVP scope and the end-to-end acceptance criteria. |
| [`docs/data-model.md`](docs/data-model.md) | Entities, fields, relationships, constraints, ER diagram. |
| [`docs/api.md`](docs/api.md) | The full API reference — every endpoint with example requests and responses. |
| [`docs/requirements.md`](docs/requirements.md) | The **original single-tenant** requirements specification, with the out-of-scope list. Historical. |
| [`docs/implementation-plan.md`](docs/implementation-plan.md) | The **original single-tenant** phased build plan (phases 0–11) with acceptance criteria. Historical. |
| [`CLAUDE.md`](CLAUDE.md) | Architecture, commands, conventions, workflow rules. |

> The two historical documents describe the first version of this application — the public wall and
> the administrator's moderation inbox. They are kept for history and are **not** the specification
> for the multi-tenant product.

---

## License

Course submission — not licensed for redistribution.
