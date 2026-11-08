# LoveProof — API Reference

Every endpoint lives under `/api`. The public endpoints need no credentials. The admin endpoints
require a signed-in session with the `ADMIN` role, and every state-changing request additionally
requires the CSRF token.

**Base URL:** `http://localhost:8080` in development. The frontend reaches it same-origin through the
Vite proxy, so the browser sees `/api/...` on port 5173.

---

## Contents

- [Conventions](#conventions)
- [Errors](#errors)
- [Rate limiting](#rate-limiting)
- [Response headers](#response-headers)
- [Authentication and CSRF](#authentication-and-csrf)
- [Public endpoints](#public-endpoints)
- [Admin endpoints](#admin-endpoints)
- [Customer endpoints](#customer-endpoints)
- [Public Space and embed endpoints](#public-space-and-embed-endpoints)
- [Billing endpoints](#billing-endpoints)
- [The Stripe webhook](#the-stripe-webhook)
- [Data shapes](#data-shapes)

---

## Conventions

| | |
|---|---|
| Content type | `application/json` for every request and response with a body |
| Dates | ISO-8601 UTC strings, e.g. `2026-11-08T13:34:07Z` |
| Pagination | `page` (0-based) and `size`; the response echoes `page`, `size`, `total` and `totalPages` |
| Casing | `camelCase` in JSON, `snake_case` in SQL |
| Statuses | `PENDING`, `APPROVED`, `REJECTED` |

---

## Errors

Every failure — validation, authentication, authorization, not-found, conflict — uses the same
envelope:

```json
{
  "code": "VALIDATION_FAILED",
  "message": "The submitted testimonial is not valid.",
  "fieldErrors": {
    "rating": "Rating must be between 1 and 5."
  },
  "timestamp": "2026-11-08T13:34:07Z",
  "path": "/api/testimonials"
}
```

`fieldErrors` is an empty object when the failure is not field-level.

| Status | `code` | When |
|---|---|---|
| `400` | `VALIDATION_FAILED` | A field failed validation |
| `400` | `INVALID_WEBHOOK_SIGNATURE` | A Stripe delivery whose signature did not verify, or whose event carried no identifier |
| `401` | `AUTHENTICATION_FAILED` | The login was rejected — wrong username or password |
| `401` | `UNAUTHENTICATED` | No session, or the session is no longer valid |
| `403` | `FORBIDDEN` | Signed in but not permitted, or a missing/invalid CSRF token |
| `404` | `NOT_FOUND` | No such testimonial |
| `409` | `DUPLICATE_SUBMISSION` | An identical submission was made inside the 10-minute window |
| `409` | `CONFLICT` | Acting on an already-deleted testimonial, or restoring a live one |
| `409` | `PLAN_LIMIT_REACHED` | The account's plan does not allow this — too many Spaces, or a mutation on a read-only Space |
| `409` | `SPACE_NOT_ACCEPTING` | The Space is not accepting submissions — it is closed, or it is at its testimonial limit |
| `429` | `RATE_LIMITED` | Rate limit exceeded — always with a `Retry-After` header |
| `500` | `INTERNAL_ERROR` | Unexpected — logged in full server-side, generic message to the client |
| `503` | `BILLING_NOT_CONFIGURED` | A billing action on an installation without Stripe keys. Nothing changes and no plan moves |

---

## Rate limiting

Two endpoints are limited server-side: the public submission endpoint and the admin sign-in
endpoint. Both use a continuous-refill token bucket keyed on a salted SHA-256 hash of the client
IP address — the raw address is never stored, and the hash is salted per environment so the
bucket keys cannot be correlated across deployments.

When a limit is exceeded the response is `429` with the `RATE_LIMITED` code and a `Retry-After`
header giving the number of seconds until the next request would be accepted:

```http
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
Retry-After: 3195
```

```json
{
  "code": "RATE_LIMITED",
  "message": "Too many testimonials were submitted from this location. Please try again later.",
  "fieldErrors": {},
  "timestamp": "2026-11-08T13:34:07Z",
  "path": "/api/testimonials"
}
```

The sign-in limit carries its own message, `"Too many sign-in attempts. Please try again later."`,
which says nothing about whether the account exists.

The bucket refills continuously rather than resetting on a fixed boundary, so `Retry-After` is the
time until the next single request would be allowed.

The defaults are 5 submissions per hour and 10 sign-in attempts per five minutes. Both are
configurable through `LOVEPROOF_RATE_LIMIT_SUBMIT_LIMIT`,
`LOVEPROOF_RATE_LIMIT_SUBMIT_PERIOD_SECONDS`, `LOVEPROOF_RATE_LIMIT_LOGIN_LIMIT` and
`LOVEPROOF_RATE_LIMIT_LOGIN_PERIOD_SECONDS`.

The sign-in limiter answers with the same `429` whether or not the account exists, so the limiter
cannot be used to enumerate administrators.

> **Trusting `X-Forwarded-For`.** The client address is taken from `X-Forwarded-For` only when
> `LOVEPROOF_TRUST_FORWARDED_FOR` is `true`, which is off by default. A client that can set the
> header can otherwise choose its own rate-limit bucket and defeat the limit entirely. Turn it on
> only when the deployment really does sit behind a proxy that sets the header.

---

## Response headers

Every response carries the same security headers:

| Header | Value |
|---|---|
| `Content-Security-Policy` | `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'` |
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `same-origin` |
| `Strict-Transport-Security` | `max-age=63072000; includeSubDomains` — **only** when the deployment is HTTPS |

The `style-src` concession is there because React sets a handful of element styles inline; a strict
style policy would break them. `Strict-Transport-Security` is only sent over HTTPS, because sending
it over plain HTTP would let an attacker strip it.

---

## Authentication and CSRF

The admin surface uses **session authentication**, not tokens.

### Signing in

```http
POST /api/admin/session
Content-Type: application/json

{
  "username": "admin",
  "password": "loveproof"
}
```

```http
HTTP/1.1 200 OK
Set-Cookie: SESSION=8f14e45fceea167a5a36dedd4bea2543; Path=/; HttpOnly; SameSite=Lax
Set-Cookie: XSRF-TOKEN=6f8b9c2d1e4a7f30; Path=/; SameSite=Lax
Content-Type: application/json

{
  "username": "admin",
  "displayName": "LoveProof Admin",
  "role": "ADMIN",
  "lastLoginAt": "2026-11-08T13:34:07Z"
}
```

The response sets two cookies with deliberately different rules:

| Cookie | `HttpOnly` | `SameSite` | `Secure` | Why |
|---|---|---|---|---|
| `SESSION` | **yes** | `Lax` | under `prod` | Carries the session id. A script must not be able to read it. |
| `XSRF-TOKEN` | no | `Lax` | under `prod` | The SPA must read it to echo it back in the `X-XSRF-TOKEN` header. |

A wrong password and an unknown username both return `401` with the same message, so the response
does not reveal whether an account exists:

```json
{
  "code": "AUTHENTICATION_FAILED",
  "message": "Invalid username or password.",
  "fieldErrors": {},
  "timestamp": "2026-11-08T13:34:07Z",
  "path": "/api/admin/session"
}
```

### The CSRF token

Every state-changing request must echo the `XSRF-TOKEN` cookie's value in the `X-XSRF-TOKEN`
header:

```http
POST /api/admin/testimonials/{id}/approve
Cookie: SESSION=…; XSRF-TOKEN=6f8b9c2d1e4a7f30
X-XSRF-TOKEN: 6f8b9c2d1e4a7f30
```

Without the header the request is refused with `403` and `code: FORBIDDEN`. The token is issued
on the first response of any kind, so a client that reads it once can reuse it for the session.

Three endpoints are **exempt**, and only these:

| Endpoint | Why |
|---|---|
| `POST /api/admin/session` | The browser has no token yet. Protected by the session-id regeneration on authentication instead. |
| `POST /api/testimonials` | Anonymous — there are no credentials for an attacker to ride on. Guarded by rate limiting and the duplicate fingerprint instead. |
| `POST /api/billing/webhook` | Authenticated by a Stripe **signature**, not by a session — so there is no ambient credential for an attacker to ride on. It lives on its own filter chain. See [The Stripe webhook](#the-stripe-webhook). |

The **customer** chain (`/api/app/**`) has its own exemption list, because it has its own
anonymous endpoints:

| Endpoint | Why |
|---|---|
| `POST /api/app/auth/register` | No session yet. Protected by the session-id regeneration on authentication. |
| `POST /api/app/auth/sign-in` | Same. |
| `POST /api/app/auth/forgot-password` | Carries nothing that a forged request could use. The answer is the same whether or not the address exists. |
| `POST /api/app/auth/reset-password` | Carries a single-use token in the body. |
| `POST /api/app/auth/verify-email` | Carries a single-use token in the body. |
| `/api/app/public/**` | The public Space pages and the submission endpoint are anonymous. Guarded by rate limiting and the honeypot field instead. |

> **The CSRF exemption list and the `permitAll` list must match exactly.** The CSRF filter runs
> *before* authorization, so an endpoint that is reachable without a session and is not exempt
> from CSRF would answer `403` to a legitimate first-time visitor.

### Signing out

```http
DELETE /api/admin/session
Cookie: SESSION=…
```

```http
HTTP/1.1 204 No Content
Set-Cookie: SESSION=; Path=/; HttpOnly; Max-Age=0
```

The session is invalidated server-side **and** the cookie is expired, so the browser does not keep
a dead session id. A subsequent admin request with the old cookie returns `401`.

---

## Public endpoints

### `GET /api/health`

Liveness and service identity. No credentials.

```json
{
  "status": "UP",
  "service": "loveproof-testimonials",
  "timestamp": "2026-11-08T13:34:07Z"
}
```

### `GET /actuator/health`

Spring Boot's operational health, including the liveness and readiness probes.

### `POST /api/testimonials`

Submit a testimonial. The submission is stored as `PENDING` and is **not** immediately visible on
the public wall — it waits for moderation.

```http
POST /api/testimonials
Content-Type: application/json

{
  "name": "Priya Raman",
  "role": "Head of Customer Success",
  "company": "Northwind Traders",
  "rating": 5,
  "content": "Migrating our testimonial collection over took a single afternoon.",
  "consent": true
}
```

```http
HTTP/1.1 201 Created
```

```json
{
  "id": "6f1e2b7c-4a3d-4e8f-9b1a-2c3d4e5f6a7b",
  "name": "Priya Raman",
  "role": "Head of Customer Success",
  "company": "Northwind Traders",
  "rating": 5,
  "content": "Migrating our testimonial collection over took a single afternoon.",
  "status": "PENDING",
  "consentTextVersion": "v1",
  "submittedAt": "2026-11-08T13:34:07Z"
}
```

| Field | Rules |
|---|---|
| `name` | Required, 2–100 characters, trimmed before validation |
| `role` | Optional, at most 100 characters. Omitted or blank is stored as `null`, not `""` |
| `company` | Optional, at most 100 characters. Same |
| `rating` | Required, integer 1–5. No half-stars |
| `content` | Required, 20–2000 characters |
| `consent` | Required and must be `true` |

An identical `name` + `content` pair submitted inside a 10-minute window is rejected with `409`
and `code: DUPLICATE_SUBMISSION`.

### `GET /api/testimonials`

The public wall: approved, non-deleted testimonials only, newest first.

| Parameter | Default | Purpose |
|---|---|---|
| `page` | `0` | The 0-based page number |
| `size` | `12` | The page size. Clamped to 1–100. |
| `rating` | — | Filter to one rating |
| `sort` | `submittedAt` | Ordering. `submittedAt` (or `newest`) is newest first; `rating` is highest rated first. An unrecognised value falls back to `submittedAt`. |

```http
GET /api/testimonials?page=0&size=12&rating=5
```

```json
{
  "content": [
    {
      "id": "6f1e2b7c-4a3d-4e8f-9b1a-2c3d4e5f6a7b",
      "name": "Priya Raman",
      "role": "Head of Customer Success",
      "company": "Northwind Traders",
      "rating": 5,
      "content": "Migrating our testimonial collection over took a single afternoon.",
      "status": "APPROVED",
      "consentTextVersion": "v1",
      "submittedAt": "2026-11-08T13:34:07Z"
    }
  ],
  "page": 0,
  "size": 12,
  "total": 1,
  "totalPages": 1
}
```

### `GET /api/testimonials/stats`

The public count and the average rating of approved testimonials. The average is `null` when
nothing is approved.

```json
{
  "count": 5,
  "averageRating": 4.4
}
```

---

## Admin endpoints

Every endpoint below requires a signed-in session with the `ADMIN` role. A request without one
returns `401`; a request with a session but no CSRF token (on a write) returns `403`.

### `GET /api/admin/session`

The signed-in administrator's identity. Used by the SPA to restore a session on page load.

```json
{
  "username": "admin",
  "displayName": "LoveProof Admin",
  "role": "ADMIN",
  "lastLoginAt": "2026-11-08T13:34:07Z"
}
```

### `GET /api/admin/testimonials/stats`

The dashboard counts. They are computed at request time, so they cannot drift from the rows.

```json
{
  "total": 8,
  "pending": 2,
  "approved": 5,
  "rejected": 1,
  "deleted": 0,
  "last7Days": 4,
  "averageRating": 4.4
}
```

| Field | Meaning |
|---|---|
| `total` | The non-deleted testimonials — `pending + approved + rejected` |
| `pending` | Awaiting moderation |
| `approved` | Approved and published |
| `rejected` | Rejected |
| `deleted` | Soft-deleted. Excluded from every other count |
| `last7Days` | Submitted in the last seven days, excluding deleted |
| `averageRating` | The average rating of the approved testimonials, or `null` |

### `GET /api/admin/testimonials`

The moderation inbox. Returns every non-deleted testimonial matching the filter.

| Parameter | Default | Purpose |
|---|---|---|
| `status` | — | `PENDING`, `APPROVED` or `REJECTED` |
| `q` | — | Free-text search across name, company and content. Case-insensitive |
| `page` | `0` | The 0-based page number |
| `size` | `20` | The page size (capped at 100) |

```http
GET /api/admin/testimonials?status=PENDING&q=northwind
```

The response is a page of testimonials in the same shape as the public wall, but the items may be in
any status.

### `GET /api/admin/testimonials/deleted`

The soft-deleted testimonials, for restore. Paginated with `page` and `size`.

### `GET /api/admin/testimonials/{id}`

One testimonial in its actual state — pending, rejected or soft-deleted included, unlike the
public wall.

### `GET /api/admin/testimonials/{id}/audit`

The append-only moderation history of one testimonial, newest first. The submission itself is in
the trail, so it explains how the testimonial reached its current state rather than only listing
the moderation decisions.

```json
[
  {
    "action": "RESTORE",
    "adminUsername": "admin",
    "adminUserId": "1df6fac8-fb3f-463b-893d-40156ee299fe",
    "previousStatus": "APPROVED",
    "newStatus": "APPROVED",
    "reason": null,
    "createdAt": "2026-11-08T07:36:13.885452Z"
  },
  {
    "action": "APPROVE",
    "adminUsername": "admin",
    "adminUserId": "1df6fac8-fb3f-463b-893d-40156ee299fe",
    "previousStatus": "PENDING",
    "newStatus": "APPROVED",
    "reason": null,
    "createdAt": "2026-11-08T07:36:13.836249Z"
  },
  {
    "action": "SUBMIT",
    "adminUsername": "anonymous",
    "adminUserId": null,
    "previousStatus": null,
    "newStatus": "PENDING",
    "reason": null,
    "createdAt": "2026-11-08T07:34:07.143987Z"
  }
]
```

`action` is one of `SUBMIT`, `APPROVE`, `REJECT`, `DELETE` or `RESTORE`. `adminUsername` is
`anonymous` for the original submission, and `adminUserId` is `null` for it. The `DELETE` and
`RESTORE` entries carry the status on both sides, because a soft deletion leaves the moderation
status untouched.

### `POST /api/admin/testimonials/{id}/approve`

Approves a testimonial. It becomes immediately visible on the public wall.

```http
POST /api/admin/testimonials/6f1e2b7c-4a3d-4e8f-9b1a-2c3d4e5f6a7b/approve
X-XSRF-TOKEN: 6f8b9c2d1e4a7f30
```

```http
HTTP/1.1 200 OK
```

The response is the moderated testimonial. Acting on a soft-deleted testimonial returns `409`.

### `POST /api/admin/testimonials/{id}/reject`

Rejects a testimonial. A reason is **mandatory** — a blank reason returns `400` with
`fieldErrors.reason` populated.

```http
POST /api/admin/testimonials/6f1e2b7c-4a3d-4e8f-9b1a-2c3d4e5f6a7b/reject
Content-Type: application/json
X-XSRF-TOKEN: 6f8b9c2d1e4a7f30

{
  "reason": "Off topic."
}
```

The reason is at most 255 characters, is recorded in the audit trail, and never reaches the public
API.

### `DELETE /api/admin/testimonials/{id}`

Soft-deletes a testimonial. The moderation status is left untouched — deletion and moderation are
orthogonal — and the row is removed from the wall and from every count except `deleted`.

```http
DELETE /api/admin/testimonials/6f1e2b7c-4a3d-4e8f-9b1a-2c3d4e5f6a7b
X-XSRF-TOKEN: 6f8b9c2d1e4a7f30
```

### `POST /api/admin/testimonials/{id}/restore`

Restores a soft-deleted testimonial to the status it had when it was deleted. Restoring a
testimonial that is not deleted returns `409`.

---

## Customer endpoints

Every endpoint under `/api/app/**` requires a signed-in **customer** session and, on writes, the
CSRF token. It carries no administrator role: the admin surface and the customer surface are
separate filter chains, and a customer session grants nothing on `/api/admin/**`.

The customer surface is **scoped to the signed-in account**. An account can only read and change
its own Spaces, its own testimonials and its own billing state; there is no identifier in any
request that lets one account reach another's.

### `POST /api/app/auth/register`

```http
POST /api/app/auth/register
Content-Type: application/json

{
  "email": "ada@example.com",
  "displayName": "Ada",
  "password": "correct horse battery staple"
}
```

Returns `201` with the account. The account starts on the `FREE` plan with
`subscriptionStatus: "NONE"`.

### `POST /api/app/auth/sign-in`

```http
POST /api/app/auth/sign-in
Content-Type: application/json

{
  "email": "ada@example.com",
  "password": "correct horse battery staple"
}
```

Returns `200` with the account. The response sets the same `SESSION` and `XSRF-TOKEN` cookies
as the admin sign-in — one cookie pair, two separate sessions.

### `POST /api/app/auth/sign-out`

```http
POST /api/app/auth/sign-out
```

Returns `204` and expires the session cookie, exactly as the admin sign-out does.

### `GET /api/app/auth/me`

The signed-in account, used by the SPA to restore the session on page load.

```json
{
  "id": "b1a0f5a4-0000-4000-8000-000000000001",
  "email": "ada@example.com",
  "displayName": "Ada",
  "emailVerified": true,
  "plan": "FREE",
  "subscriptionStatus": "NONE",
  "cancelAtPeriodEnd": false,
  "subscriptionPastDue": false,
  "currentPeriodEnd": null
}
```

The password hash, the Stripe identifiers and the Google subject are never included.

### The remaining account endpoints

| Endpoint | Purpose |
|---|---|
| `GET /api/app/auth/verify-email?token=…` | Whether a verification token is still valid. The only `GET` that is not idempotent-by-omission |
| `POST /api/app/auth/verify-email` | Confirms the address and signs the account in |
| `POST /api/app/auth/forgot-password` | Starts a password reset. The answer is the same whether or not the address exists |
| `POST /api/app/auth/reset-password` | Completes a reset |
| `POST /api/app/auth/change-password` | Changes the password of a signed-in account |
| `POST /api/app/auth/profile` | Changes the display name |

### The dashboard

| Endpoint | Purpose |
|---|---|
| `GET /api/app/dashboard` | The account's numbers. `?days=` selects the chart's range |

Everything it returns is the signed-in account's own data. There is no parameter that names
another account, so there is nothing to authorise beyond the session itself.

Three rules shape the answer (PRD §4.6):

- **Only the account's own data.** Every count is scoped by the Space ids the account owns.
- **The buckets are UTC days.** A submission is filed under the UTC day it arrived on, so two
  people looking at the dashboard at the same moment see the same chart.
- **A day with no submissions shows zero.** The series has a point for every day in the range,
  so the line does not lie about what happened in the gaps.

`days` is clamped to `1…365` and defaults to `30`. An out-of-range value is answered with the
clamped range rather than refused — the chart can draw any of them.

### Spaces

| Endpoint | Purpose |
|---|---|
| `GET /api/app/spaces` | Every Space the account owns |
| `POST /api/app/spaces` | Creates one. `409 PLAN_LIMIT_REACHED` once the account is at its plan's Space limit |
| `GET /api/app/spaces/themes` | The theme catalogue. Each entry says whether it is premium |
| `GET /api/app/spaces/{spaceId}` | One Space |
| `PUT /api/app/spaces/{spaceId}` | Updates one Space. `409 PLAN_LIMIT_REACHED` on a read-only Space |
| `DELETE /api/app/spaces/{spaceId}` | Deletes one Space. `409 PLAN_LIMIT_REACHED` on a read-only Space |

> **A downgrade never deletes data.** An account that is downgraded keeps all its Spaces; the ones
> beyond its plan's limit become **read-only** — they are listed, they still render, and they
> still serve their submissions, but they cannot be updated or deleted. The oldest Spaces by
> creation date keep their editability.

### Testimonials in a Space

| Endpoint | Purpose |
|---|---|
| `GET /api/app/spaces/{spaceId}/testimonials` | The inbox. Filters: `search`, `favorite`, `wallOfLove`, `hidden`, `rating`; paged with `page` and `size` |
| `PATCH /api/app/spaces/{spaceId}/testimonials/{id}` | Curation: `favorite`, `wallOfLove`, `visible` |
| `PUT /api/app/spaces/{spaceId}/testimonials/{id}` | Edits the text of a testimonial |
| `DELETE /api/app/spaces/{spaceId}/testimonials/{id}` | Deletes a testimonial |

### The embed configuration

| Endpoint | Purpose |
|---|---|
| `GET /api/app/spaces/{spaceId}/embed` | The embed configuration and the snippet to paste |
| `PUT /api/app/spaces/{spaceId}/embed` | Saves it. A premium option on a Free account is refused with `409 CONFLICT` |
| `POST /api/app/spaces/{spaceId}/embed/rotate-token` | Issues a new embed token. The old snippet stops working |

---

## Public Space and embed endpoints

These carry **no credentials** — the visitor collecting a testimonial is not signed in. They are
scoped by the Space's slug or by its embed token.

### `GET /api/app/public/spaces/{slug}`

The public Space: its configuration, its wall, and whether it is accepting submissions.

```json
{
  "slug": "adas-reviews",
  "title": "Ada’s Reviews",
  "subtitle": "What people say",
  "theme": "basic",
  "fieldCompany": false,
  "fieldCompanyRequired": false,
  "fieldSocial": false,
  "fieldSocialRequired": false,
  "fieldWebsite": false,
  "fieldWebsiteRequired": false,
  "fieldJobTitle": false,
  "fieldJobTitleRequired": false,
  "ratingEnabled": true,
  "accepting": true
}
```

It is deliberately **not** the owner's `SpaceResponse`: it carries no owner identifier, no
`editable` flag and no slug-lock state. A visitor learns what to fill in and how the page looks,
and nothing about the account that owns it.

`accepting` is `false` when the Space is closed **or** when it has reached its owner's per-Space
testimonial limit. It comes from the same check that refuses a submission, so the page can never
offer a form that the server will reject.

### `POST /api/app/public/spaces/{slug}/testimonials`

Submits a testimonial to a Space. The fields the owner asked for are required, and the optional
ones are absent from the response.

```http
POST /api/app/public/spaces/adas-reviews/testimonials
Content-Type: application/json

{
  "name": "Grace",
  "email": "grace@example.com",
  "content": "Lovely work.",
  "rating": 5,
  "company": "Northwind",
  "socialLink": "https://example.com",
  "website": "https://example.com",
  "jobTitle": "Engineer",
  "consent": true,
  "website2": ""
}
```

| Field | Rules |
|---|---|
| `name` | Required, at most 100 characters |
| `email` | Required, a valid address, at most 254 characters |
| `content` | Required, at most 2000 characters |
| `rating` | Optional, 1–5. Omitted when the Space has ratings off |
| `company` / `jobTitle` | Optional, at most 120 characters. Only collected when the Space asks for them |
| `socialLink` / `website` | Optional, at most 500 characters. Only collected when the Space asks for them |
| `consent` | Optional. Never gates display |
| `website2` | **The honeypot.** A hidden field that must stay empty |

Returns `201` with `{ "id": "…", "submittedAt": "…" }`.

> **The honeypot is the spam guard.** The field is hidden from people and visible to the bots that
> fill in every input on a form. A submission that fills it in is refused. Together with the
> per-IP rate limiting, it is what stands between the public form and a spam campaign.

A Space that is not accepting submissions answers `409` with `code: SPACE_NOT_ACCEPTING` and the
message *"This Space is not accepting responses at the moment."* — deliberately neutral, so the
submitter learns nothing about the owner's plan or their testimonial count.

### `GET /api/app/public/embeds/{token}`

The testimonials an embed shows, in the embed's own layout and theme. The token is the embed's
public identity; rotating it invalidates every snippet that used the old one.

### `GET /embed/loveproof.js`

The embed loader. It is a static script served by the backend, so a customer can paste the
snippet on their own site without a build step.

---

## Billing endpoints

Every endpoint under `/api/app/billing` requires a signed-in customer session, and the writes
require the CSRF token.

> **No endpoint here changes a plan.** Checkout, cancel and resume all ask *Stripe* to do
> something and write nothing locally. The plan moves when Stripe's webhook says so — see
> [The Stripe webhook](#the-stripe-webhook). This is what makes the upgrade unforgeable: a
> customer who reaches the success URL has not paid anything as far as this server is concerned.

### `GET /api/app/billing`

The current plan, the subscription state, and the catalogue of both plans. The limits in the
catalogue are the same numbers the server enforces, so the page a customer reads and the limit
they hit cannot drift apart.

```json
{
  "plan": "PREMIUM",
  "subscriptionStatus": "active",
  "cancelAtPeriodEnd": false,
  "pastDue": false,
  "currentPeriodEnd": "2026-12-08T13:34:07Z",
  "billingConfigured": true,
  "plans": [
    {
      "plan": "FREE",
      "label": "Free",
      "price": "Free",
      "spaces": 3,
      "testimonialsPerSpace": 100,
      "features": [
        "3 Spaces",
        "100 testimonials per Space",
        "Masonry embed",
        "Light and dark mode"
      ]
    },
    {
      "plan": "PREMIUM",
      "label": "Premium",
      "price": "$9.99/month",
      "spaces": 25,
      "testimonialsPerSpace": 1000,
      "features": [
        "25 Spaces",
        "1,000 testimonials per Space",
        "Premium themes",
        "Carousel embed",
        "Background colour",
        "Animation"
      ]
    }
  ]
}
```

`subscriptionStatus` uses Stripe's own vocabulary: `NONE`, `active`, `trialing`, `past_due`,
`canceled`, `unpaid`, `incomplete`, `incomplete_expired` or `paused`. `NONE` means the account
has never subscribed.

`billingConfigured` is `false` on an installation without Stripe keys. The read still succeeds —
the customer can still see their plan — but the upgrade button is unavailable and the billing
actions answer `503 BILLING_NOT_CONFIGURED`.

### `POST /api/app/billing/checkout`

Creates a Stripe Checkout Session and returns its URL.

```json
{ "url": "https://checkout.stripe.com/c/pay/cs_test_a1B2…" }
```

The session is created for the Premium price configured on the installation, with the account's
identifier attached as subscription metadata — which is how the webhook finds the account when
the event arrives.

### `POST /api/app/billing/cancel`

Asks Stripe to cancel the subscription at the end of the period already paid for. Returns `204`.

Nothing changes locally: the plan **stays Premium** until the period ends, and the account is
only downgraded when Stripe reports the subscription as `canceled`.

### `POST /api/app/billing/resume`

Asks Stripe to resume a subscription that is set to end. Returns `204`. The local cancel flag
clears when the webhook arrives.

### `POST /api/app/billing/portal`

Creates a Stripe Billing Portal session and returns its URL, for a customer who wants to manage
their card details themselves.

---

## The Stripe webhook

```http
POST /api/billing/webhook
Content-Type: application/json
Stripe-Signature: t=1772992447,v1=5257a869e7ecebeda32affa62cdca3fa51cad7e77a0e56ff536d0ce8e108d8bd

{ "id": "evt_1Q...", "type": "customer.subscription.updated", "data": { "object": { … } } }
```

This endpoint has **no session and no CSRF token**. It is authenticated by the Stripe signature
and lives on its own filter chain, because its trust model is *a verified signature*, not *a
session*.

### What is accepted

| Check | Rule |
|---|---|
| The signature | `HMAC-SHA256(webhook_secret, "<timestamp>.<raw body>")` must equal one of the header's `v1` values, compared in constant time |
| The timestamp | Rejected when it is more than five minutes old, so a captured delivery cannot be replayed |
| The event identifier | An event with no `id` is refused — without one it cannot be recorded in the ledger |

The signature is computed over the **exact bytes Stripe sent**. The body is therefore read as
`byte[]`, not as a `String`: decoding and re-encoding a string is byte-identical only for valid
UTF-8.

### What it answers

| Outcome | Status | `code` |
|---|---|---|
| A verified event | `200` | — |
| A signature that does not verify | `400` | `INVALID_WEBHOOK_SIGNATURE` |
| An event with no identifier | `400` | `INVALID_WEBHOOK_SIGNATURE` |
| A replayed event | `200` | — (and nothing is applied a second time) |
| An event naming an unknown account | `200` | — (recorded as `NO_ACCOUNT`) |

> **A replay answers `200` on purpose.** Stripe retries anything it does not get a `200` for. A
> replayed event is a success — the work has already been done — so refusing it would only make
> Stripe deliver it again.

### What it does

| Event | Effect |
|---|---|
| `customer.subscription.created` / `.updated` | The subscription status is mapped and applied |
| `customer.subscription.deleted` | The subscription ends: `canceled`, and the plan becomes `FREE` |
| `checkout.session.completed` | The subscription is retrieved from Stripe and applied |
| `invoice.payment_failed` / `.payment_succeeded` | Recorded, but the plan is **not** changed |
| Anything else | Recorded as `IGNORED` |

### The status is the authority

The plan is a pure function of the subscription status: `PREMIUM ⇔ status ∈ {active, trialing,
past_due}`. Everything the product promises falls out of that one rule.

| Stripe status | `subscriptionStatus` | Plan | Meaning |
|---|---|---|---|
| `active` | `active` | **Premium** | Paying |
| `trialing` | `trialing` | **Premium** | In a trial |
| `past_due` | `past_due` | **Premium** | A renewal failed. Access continues while Stripe retries |
| `canceled` | `canceled` | Free | The period ran out after a cancellation, or it was cancelled |
| `unpaid` | `unpaid` | Free | The payment failed for good |
| `incomplete` | `incomplete` | Free | The first payment never completed |
| `incomplete_expired` | `incomplete_expired` | Free | The first payment expired |
| `paused` | `paused` | Free | The subscription is paused |

`cancel_at_period_end = true` is **not** a downgrade. The subscription is still `active`, so the
plan stays Premium, the end date is recorded, and the billing page shows the date with a Resume
button. The downgrade happens when Stripe later reports the subscription as `canceled`.

`past_due` and `canceled` are both "the plan is Free" from the customer's point of view, but they
are recorded faithfully and stay distinguishable, because they are different facts.

### Idempotency

Stripe delivers an event more than once, and out of order. Each delivery is recorded in the
`stripe_event` ledger, keyed on Stripe's own event identifier. The ledger row is written in the
**same transaction** as the effect, so a replayed event cannot be inserted, its transaction rolls
back, and no effect is applied twice.

An old event delivered after a newer one does not resurrect the old state: the ledger records
what happened, and the plan follows the most recent status Stripe reported.

### The reconciliation job

The webhook is the primary mechanism. A scheduled job re-checks every live subscription against
Stripe every hour, which covers a delivery that was lost outright and the case where a period
ended without an event arriving. It applies the state through **the same method** the webhook
uses, so the two cannot disagree.

---

## Data shapes

### `Testimonial`

```json
{
  "id": "6f1e2b7c-4a3d-4e8f-9b1a-2c3d4e5f6a7b",
  "name": "Priya Raman",
  "role": "Head of Customer Success",
  "company": "Northwind Traders",
  "rating": 5,
  "content": "Migrating our testimonial collection over took a single afternoon.",
  "status": "PENDING",
  "consentTextVersion": "v1",
  "submittedAt": "2026-11-08T13:34:07Z"
}
```

The submitter's IP hash and the content fingerprint are internal anti-spam bookkeeping and are
never sent to a client.

### `Page<T>`

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "total": 0,
  "totalPages": 0
}
```

### `DashboardStats`

```json
{
  "total": 8,
  "pending": 2,
  "approved": 5,
  "rejected": 1,
  "deleted": 0,
  "last7Days": 4,
  "averageRating": 4.4
}
```

### `PublicStats`

```json
{
  "count": 5,
  "averageRating": 4.4
}
```

### `Billing`

```json
{
  "plan": "PREMIUM",
  "subscriptionStatus": "active",
  "cancelAtPeriodEnd": false,
  "pastDue": false,
  "currentPeriodEnd": "2026-12-08T13:34:07Z",
  "billingConfigured": true,
  "plans": []
}
```

See [Billing endpoints](#billing-endpoints) for the plan catalogue and the meaning of each field.

### `UserResponse`

```json
{
  "id": "b1a0f5a4-0000-4000-8000-000000000001",
  "email": "ada@example.com",
  "displayName": "Ada",
  "emailVerified": true,
  "hasPassword": true,
  "plan": "FREE",
  "subscriptionStatus": "NONE",
  "cancelAtPeriodEnd": false,
  "subscriptionPastDue": false,
  "currentPeriodEnd": null
}
```

`hasPassword` says whether the account has a password to change. It is `false` for a Google-only
account, which has none — the account settings page offers the password form only when there is
one, so a Google-only account is not shown a form that could not work. The provider itself is
not sent: the interface needs to know whether the form applies, not who the provider is.

### `Dashboard`

```json
{
  "totalSpaces": 2,
  "totalTestimonials": 47,
  "uniqueSubmitters": 39,
  "daily": [
    { "date": "2026-11-01", "count": 0 },
    { "date": "2026-11-02", "count": 3 }
  ],
  "rangeDays": 30,
  "recent": [
    {
      "id": "d3e2f1a0-0000-4000-8000-000000000001",
      "spaceId": "c2b1f6b5-0000-4000-8000-000000000002",
      "spaceTitle": "Ada’s Reviews",
      "name": "Grace",
      "content": "The service was excellent from start to finish.",
      "rating": 5,
      "submittedAt": "2026-11-07T09:15:00Z"
    }
  ],
  "plan": "FREE",
  "spacesLimit": 3
}
```

`uniqueSubmitters` counts distinct email addresses across the account's testimonials. `daily` has
one entry per day in the range, oldest first, including the days with no submissions. `plan` and
`spacesLimit` let the interface show the upgrade notice and the Space allowance without a second
request.

### `EmbedConfig`

```json
{
  "spaceId": "c2b1f6b5-0000-4000-8000-000000000002",
  "embedToken": "a-token-1234",
  "layout": "masonry",
  "themeMode": "light",
  "backgroundColor": null,
  "animation": false,
  "scriptUrl": "http://localhost:5173/embed/loveproof.js",
  "dataUrl": "http://localhost:5173/api/app/public/embeds/a-token-1234"
}
```

`scriptUrl` and `dataUrl` are absolute URLs on the installation, so the owner can copy the
snippet without having to know how the address is put together.

---

## Related documents

- [`docs/requirements.md`](./requirements.md) — the functional and non-functional requirements.
- [`docs/data-model.md`](./data-model.md) — the entities, constraints and ER diagram.
- [`docs/implementation-plan.md`](./implementation-plan.md) — the build phases and their acceptance
  criteria.
- [`CLAUDE.md`](../CLAUDE.md) §6 — the API conventions the implementation follows.
