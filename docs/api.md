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
- [Authentication and CSRF](#authentication-and-csrf)
- [Public endpoints](#public-endpoints)
- [Admin endpoints](#admin-endpoints)
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
| `401` | `AUTHENTICATION_FAILED` | The login was rejected — wrong username or password |
| `401` | `UNAUTHENTICATED` | No session, or the session is no longer valid |
| `403` | `FORBIDDEN` | Signed in but not permitted, or a missing/invalid CSRF token |
| `404` | `NOT_FOUND` | No such testimonial |
| `409` | `DUPLICATE_SUBMISSION` | An identical submission was made inside the 10-minute window |
| `409` | `CONFLICT` | Acting on an already-deleted testimonial, or restoring a live one |
| `429` | `RATE_LIMITED` | Rate limit exceeded — always with a `Retry-After` header |
| `500` | `INTERNAL_ERROR` | Unexpected — logged in full server-side, generic message to the client |

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

Two endpoints are **exempt**, and only two:

| Endpoint | Why |
|---|---|
| `POST /api/admin/session` | The browser has no token yet. Protected by the session-id regeneration on authentication instead. |
| `POST /api/testimonials` | Anonymous — there are no credentials for an attacker to ride on. Guarded by rate limiting and the duplicate fingerprint instead. |

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

---

## Related documents

- [`docs/requirements.md`](./requirements.md) — the functional and non-functional requirements.
- [`docs/data-model.md`](./data-model.md) — the entities, constraints and ER diagram.
- [`docs/implementation-plan.md`](./implementation-plan.md) — the build phases and their acceptance
  criteria.
- [`CLAUDE.md`](../CLAUDE.md) §6 — the API conventions the implementation follows.
