# LoveProof — Requirements Specification

**Version:** 1.3 (Phases 0–9 verified)
**Status:** Phases 0–9 complete and verified — see [`docs/implementation-plan.md`](./implementation-plan.md) §2 for results
**Stack:** React + TypeScript + Tailwind CSS (frontend) · Java 21 + Spring Boot + Maven (backend) · PostgreSQL

---

## 1. Purpose and Scope

LoveProof is a self-hosted testimonial management application in the style of Testimonial.io. It lets
visitors submit a testimonial through a public form, lets a signed-in administrator moderate those
submissions through an inbox, and publishes approved testimonials on a public wall.

The product has three surfaces:

| Surface | Audience | Auth | Purpose |
|---|---|---|---|
| Public submit form | Anonymous visitors | None | Capture a testimonial + consent |
| Public wall | Anonymous visitors | None | Display approved testimonials only |
| Admin area | Moderators/admins | Session cookie | Counts, moderation inbox, approve/reject/delete |

### 1.1 Glossary

| Term | Meaning |
|---|---|
| Testimonial | A piece of submitted social proof: name, role, company, rating, text |
| Submission | The act of creating a testimonial; always starts in `PENDING` |
| Moderation | An admin decision on a submission: approve, reject, or delete |
| Consent | The visitor's explicit agreement that their testimonial may be published |
| Wall | The public, read-only listing of approved testimonials |
| Inbox | The admin-only, filterable list of submissions awaiting or subject to moderation |

---

## 2. Functional Requirements

### FR-1 — Public testimonial submission

| ID | Requirement | Priority |
|---|---|---|
| FR-1.1 | An anonymous visitor can submit a testimonial via a public form reachable without login. | Must |
| FR-1.2 | The form collects: **name**, **role** (job title), **company**, **rating (1–5)**, **testimonial text**, and a **consent checkbox**. | Must |
| FR-1.3 | `name` is required and must be 2–100 characters after trimming whitespace. | Must |
| FR-1.4 | `role` is optional, max 120 characters. | Must |
| FR-1.5 | `company` is optional, max 120 characters. | Must |
| FR-1.6 | `rating` is required and must be an integer between 1 and 5 inclusive. | Must |
| FR-1.7 | `testimonial text` is required and must be 20–2000 characters after trimming whitespace. | Must |
| FR-1.8 | `consent` must be `true`. The form cannot be submitted with the checkbox unticked, and the API rejects `consent=false` with a validation error. | Must |
| FR-1.9 | The form shows the exact consent wording in a linked/inline statement, and the stored record captures a version identifier for the consent wording that was shown at submission time. | Must |
| FR-1.10 | On successful submission the visitor sees a confirmation state; the testimonial is **not** immediately shown on the wall. | Must |
| FR-1.11 | Server-side validation is authoritative. The client mirrors the rules for fast feedback but the API never trusts the client. | Must |
| FR-1.12 | Submissions are rate limited per client IP (see NFR-SEC-6). Exceeding the limit returns HTTP 429 with a `Retry-After` header. | Should |
| FR-1.13 | Duplicate spam protection: submissions with identical name + identical text submitted within a 10-minute window are rejected as a duplicate. | Should |

### FR-2 — Admin authentication

| ID | Requirement | Priority |
|---|---|---|
| FR-2.1 | Admin routes are protected. An unauthenticated request to any `/api/admin/**` endpoint returns HTTP 401; an authenticated request without the admin role returns HTTP 403. | Must |
| FR-2.2 | Admin login uses username + password against a stored BCrypt hash. | Must |
| FR-2.3 | On success the server creates an HTTP session and sets a `SESSION` cookie that is `HttpOnly` and `SameSite=Lax`. | Must |
| FR-2.4 | Failed logins return HTTP 401 with a generic message that does not reveal whether the username exists. | Must |
| FR-2.5 | The session cookie is `Secure` in production profiles. | Must |
| FR-2.6 | Login is rate limited by IP and username (e.g. 10 attempts / 15 min) to blunt credential stuffing. | Should |
| FR-2.7 | Admin users can log out; the session is invalidated server-side and the cookie cleared. | Must |
| FR-2.8 | The application seeds a single local admin account from configuration on first startup. Credentials are documented and overridable via environment variables. No self-service registration exists. | Must |
| FR-2.9 | All state-changing requests (including admin ones) require a CSRF token, obtained from a readable `XSRF-TOKEN` cookie and echoed in the `X-XSRF-TOKEN` header. | Must |

### FR-3 — Admin dashboard

| ID | Requirement | Priority |
|---|---|---|
| FR-3.1 | The dashboard displays total testimonial count. | Must |
| FR-3.2 | The dashboard displays a count per moderation state: pending, approved, rejected, deleted. | Must |
| FR-3.3 | The dashboard displays the average rating of approved testimonials (one decimal place) and the total approved count. | Should |
| FR-3.4 | The dashboard displays the number of submissions received in the last 7 days. | Should |
| FR-3.5 | Counts are computed from the database at request time; no denormalised counter table exists, so counts can never drift from the data. | Must |
| FR-3.6 | Counts exclude soft-deleted records except where explicitly labelled as "deleted". | Must |

### FR-4 — Moderation inbox

| ID | Requirement | Priority |
|---|---|---|
| FR-4.1 | The inbox lists submissions with status, name, role, company, rating, text excerpt, and submission date. | Must |
| FR-4.2 | The inbox is filterable by moderation state, defaulting to `PENDING`. | Must |
| FR-4.3 | The inbox is searchable by free text across name, company and testimonial text. | Should |
| FR-4.4 | The inbox is paginated with a fixed page size and a total count. | Must |
| FR-4.5 | From a pending item the moderator can **Approve**, **Reject**, or **Delete**. | Must |
| FR-4.6 | **Approve** sets status to `APPROVED` and makes the testimonial immediately visible on the public wall. | Must |
| FR-4.7 | **Reject** sets status to `REJECTED`, requires a reason (max 255 chars), and the item disappears from the pending filter. | Must |
| FR-4.8 | **Delete** soft-deletes the record, removes it from the wall and from all counts except the "deleted" count, and is reversible by an admin (restore) in the same inbox. | Must |
| FR-4.9 | Every moderation action is written to an append-only audit log recording the action, the admin user, the target testimonial, an optional reason, and a timestamp. | Must |
| FR-4.10 | Acting on an already-deleted testimonial returns HTTP 409 (conflict) rather than silently succeeding. | Should |
| FR-4.11 | Moderation actions require a CSRF token and an authenticated admin session. | Must |

### FR-5 — Public testimonial wall

| ID | Requirement | Priority |
|---|---|---|
| FR-5.1 | The wall displays only testimonials with status `APPROVED` that are not deleted. | Must |
| FR-5.2 | Each card shows the name, role, company, rating (as stars plus a text alternative such as "4 out of 5"), and the full testimonial text. | Must |
| FR-5.3 | The wall is filterable by rating and sortable by newest-first or highest-rating-first. | Should |
| FR-5.4 | The wall is paginated with a fixed page size. | Must |
| FR-5.5 | When no approved testimonials exist, a friendly empty state is shown. | Must |
| FR-5.6 | The wall is readable by anonymous visitors without any authentication. | Must |
| FR-5.7 | Pending, rejected and deleted testimonials are never returned by the public API, including in counts or error messages. | Must |

**Verified in Phase 6.** FR-5.1 and FR-5.7 were checked end-to-end: a testimonial was submitted
(`PENDING`), approved, rejected and soft-deleted in turn, and the public listing and the public
statistics were re-read after each step. Only the approved row ever appeared. FR-5.4's "fixed page
size" is 12 on the public endpoint — the frontend always asks for 12 and never sends a size of its
own, so the wall's pages are a fixed size whatever the caller's default is.

### FR-6 — Cross-cutting behaviour

| ID | Requirement | Priority |
|---|---|---|
| FR-6.1 | All API errors return a consistent JSON envelope with a machine-readable `code`, a human-readable `message`, and per-field `fieldErrors` for validation failures. | Must |
| FR-6.2 | The UI surfaces validation errors inline, next to the offending field, linked to the field for screen readers. | Must |
| FR-6.3 | The UI surfaces server/network failures as a dismissible, retryable error state rather than a blank screen or unhandled rejection. | Must |
| FR-6.4 | The application is usable on mobile (360 px), tablet and desktop viewports. | Must |
| FR-6.5 | The application is keyboard operable end-to-end and meets WCAG 2.1 AA. | Must |
| FR-6.6 | Admin mutations are guarded by both an authenticated session and the admin role; no admin action is reachable without them. | Must |

---

## 3. Non-Functional Requirements

### NFR-PERF — Performance

| ID | Requirement |
|---|---|
| NFR-PERF-1 | Public wall and submit endpoints respond within 300 ms at p95 on a development machine with up to 1,000 seeded testimonials. |
| NFR-PERF-2 | Admin dashboard and inbox respond within 400 ms at p95 with the same dataset. |
| NFR-PERF-3 | The public wall never issues an unbounded query — every read is paginated and index-supported. |
| NFR-PERF-4 | The frontend ships a production bundle under 250 KB gzipped, code-split by route. |

### NFR-SEC — Security & privacy

| ID | Requirement |
|---|---|
| NFR-SEC-1 | Admin passwords are stored only as BCrypt hashes (cost ≥ 10). No plaintext password is ever persisted or logged. |
| NFR-SEC-2 | Session cookies are `HttpOnly`, `SameSite=Lax`, and `Secure` under the production profile. Session IDs are regenerated on login (session fixation defence). |
| NFR-SEC-3 | CSRF protection is enabled on every state-changing endpoint and validated by the server. |
| NFR-SEC-4 | All user-supplied text is treated as untrusted: output is escaped by React's default rendering, no `dangerouslySetInnerHTML` is used, and no SQL is built by string concatenation (JPA parameter binding only). |
| NFR-SEC-5 | Security response headers are set: `Content-Security-Policy`, `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy`. |
| NFR-SEC-6 | Rate limiting protects public submission and admin login. The client IP is stored as a salted hash, never in plaintext. |
| NFR-SEC-7 | The public API exposes only approved testimonials; no endpoint leaks pending/rejected/deleted content to anonymous callers. |
| NFR-SEC-8 | Logs never contain testimonial content, passwords, session identifiers, or raw IP addresses. |
| NFR-SEC-9 | Database access uses a least-privilege application user distinct from the database owner. |

### NFR-A11Y — Accessibility

| ID | Requirement |
|---|---|
| NFR-A11Y-1 | The UI targets WCAG 2.1 Level AA. |
| NFR-A11Y-2 | Every input has a programmatically associated `<label>`; placeholder text is never the only label. |
| NFR-A11Y-3 | The 1–5 rating input is an accessible radio group: operable with arrow keys, with an accessible name and a text alternative. |
| NFR-A11Y-4 | Validation errors are announced via `aria-live="polite"` and are linked to their input with `aria-describedby`; `aria-invalid` is set on failing fields. |
| NFR-A11Y-5 | A "skip to content" link is the first focusable element on every page. |
| NFR-A11Y-6 | Visible focus indicators are never removed; focus is visibly trapped only in genuine modal dialogs, which can be dismissed with `Escape`. |
| NFR-A11Y-7 | Colour contrast meets 4.5:1 for normal text and 3:1 for large text and UI boundaries; rating is never conveyed by colour alone. |
| NFR-A11Y-8 | The layout reflows to 320 px width without horizontal scrolling, and honours `prefers-reduced-motion`. |

### NFR-TEST — Testability

| ID | Requirement |
|---|---|
| NFR-TEST-1 | The backend test suite runs against a real PostgreSQL 16 instance via Testcontainers, so migrations and constraints are exercised for real. |
| NFR-TEST-2 | The backend suite covers every controller endpoint (success, validation failure, authorisation failure, not-found, conflict) with `@WebMvcTest`/MockMvc and repository behaviour with `@DataJpaTest`. |
| NFR-TEST-3 | The frontend suite covers form validation, submission success/failure states, wall rendering, and moderation actions with Vitest + React Testing Library, using a mocked API layer (MSW or equivalent). |
| NFR-TEST-4 | Automated tests run with a single command per project (`mvn verify`, `npm test`) and require no manual setup beyond Docker for the backend. |
| NFR-TEST-5 | Tests are deterministic — no reliance on wall-clock time, random ordering, or shared mutable state. |

### NFR-MAINT — Maintainability

| ID | Requirement |
|---|---|
| NFR-MAINT-1 | Frontend and backend are separate projects (`frontend/`, `backend/`) with independent build lifecycles. |
| NFR-MAINT-2 | Database schema changes are managed by versioned Flyway migrations; Hibernate schema auto-generation is disabled in all environments. |
| NTR-MAINT-3 | DTOs are separate from entities; entities are never returned directly by controllers. |
| NFR-MAINT-4 | Layered structure: `web` (controllers) → `service` (business rules) → `repository` (persistence) → `domain` (entities). |
| NFR-MAINT-5 | Destructive or schema-altering changes require a new migration; existing migrations are never edited after merge. |

### NFR-OPS — Operability & developer experience

| ID | Requirement |
|---|---|
| NFR-OPS-1 | `docker compose up` provisions PostgreSQL 16 with the app database and user pre-created. |
| NFR-OPS-2 | All configuration is externalised via environment variables with sane local defaults; no secrets are committed to the repository. |
| NFR-OPS-3 | The backend exposes `/actuator/health` for liveness/readiness checks. |
| NFR-OPS-4 | A seeded local admin account, a `local` profile with permissive dev settings, and a `prod` profile with tightened settings are provided. |
| NFR-OPS-5 | Structured JSON logging in the `prod` profile; human-readable logging locally. |
| NFR-OPS-6 | `README.md` documents prerequisites, how to run both halves, how to run tests, and the seeded admin credentials. |

### NFR-COMPAT — Compatibility

| ID | Requirement |
|---|---|
| NFR-COMPAT-1 | Supports the latest two stable versions of Chrome, Firefox, Safari and Edge, and current iOS Safari / Android Chrome. |
| NFR-COMPAT-2 | The frontend targets ES2020 and is transpiled by Vite; the backend targets Java 21 bytecode. |

---

## 4. API Surface

> As implemented. The full reference with examples is in [`docs/api.md`](./api.md).

### 4.1 Public — unauthenticated

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/testimonials` | Submit a testimonial |
| `GET` | `/api/testimonials?page=&size=&rating=&sort=` | Approved testimonials only (wall) |
| `GET` | `/api/testimonials/stats` | Aggregate public stats (count, average rating) |

### 4.2 Admin — requires session + admin role

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/admin/session` | Log in |
| `DELETE` | `/api/admin/session` | Log out |
| `GET` | `/api/admin/session` | Current admin identity |
| `GET` | `/api/admin/testimonials/stats` | Dashboard counts |
| `GET` | `/api/admin/testimonials?status=&q=&page=&size=` | Moderation inbox |
| `GET` | `/api/admin/testimonials/deleted?page=&size=` | Soft-deleted testimonials |
| `GET` | `/api/admin/testimonials/{id}` | One testimonial in its actual state |
| `GET` | `/api/admin/testimonials/{id}/audit` | The append-only moderation history |
| `POST` | `/api/admin/testimonials/{id}/approve` | Approve |
| `POST` | `/api/admin/testimonials/{id}/reject` | Reject (reason required) |
| `DELETE` | `/api/admin/testimonials/{id}` | Soft delete |
| `POST` | `/api/admin/testimonials/{id}/restore` | Undo delete |

### 4.3 Error envelope

```json
{
  "code": "VALIDATION_FAILED",
  "message": "The submitted testimonial is not valid.",
  "fieldErrors": { "rating": "Rating must be between 1 and 5." },
  "timestamp": "2026-11-03T10:15:30Z",
  "path": "/api/testimonials"
}
```

Status codes: `400` validation · `401` unauthenticated · `403` forbidden · `404` not found ·
`409` conflict (already deleted) · `429` rate limited · `500` unexpected.

---

## 5. Assumptions

These are decisions made where the brief was silent. **Each is a reversible choice, flagged here so it
can be overridden during review.**

| # | Assumption | Rationale / alternative |
|---|---|---|
| A-1 | "Role/company" is modelled as **two** separate optional fields (job title, company), not one combined field. | Cleaner display and filtering. A single free-text field is the alternative. |
| A-2 | **No email address is collected.** | The brief lists exactly which fields to collect and email is not among them. Also minimises personal data handling. Consequence: moderators cannot email the submitter, and there is no natural uniqueness key — duplicate detection uses name + text. |
| A-3 | Role separation is limited to a single seeded admin user with one role for v1; the schema still carries a `role` column so `MODERATOR` vs `ADMIN` can be introduced later. | Avoids building permissions the brief does not need. |
| A-4 | Moderation uses a **status field** (`PENDING` / `APPROVED` / `REJECTED`) plus a separate soft-delete flag, rather than a status value of `DELETED`. | Keeps "status" and "deleted" as orthogonal concepts and lets the dashboard count deleted records distinctly. Alternative is a hard delete; soft delete is chosen because it preserves an audit trail and allows restore. |
| A-5 | Every submission starts as `PENDING`. There is no auto-approval, no "publish immediately" toggle, and no trust-based bypass. | Matches the brief's approve/reject workflow. |
| A-6 | "Consent" is a required boolean plus a stored `consentTextVersion` recording which wording the visitor agreed to. | Lets us evidence what was consented to if wording changes. A full consent-policy table is out of scope. |
| A-7 | Ratings are stored as a 1–5 integer, no half-stars. | The brief says 1–5. |
| A-8 | Rate limiting is an in-process, per-instance bucket keyed on a salted hash of the client IP. | The brief does not name a rate limiter; a Redis-backed one would be needed for multi-instance deployment, which is out of scope. |
| A-9 | The wall shows **all** approved testimonials. There is no per-testimonial "featured"/"hidden" flag. | The brief mentions one wall with approved content only. |
| A-10 | The admin area is a separate route space (`/admin/*`) in the same SPA, not a separate deployable. | Simpler local development; the API is already separated by path prefix and role checks. |
| A-11 | The frontend uses **React 18.3.1 + Vite 6.4.5 + Tailwind CSS 3.4.19 + TypeScript 5.9.3 + Vitest 3.2.7**, pinned to the newest versions that support the installed Node 18.19 runtime. `.nvmrc` documents Node 20 as the recommended upgrade. | Node 18 is EOL and Vite 7 / Vitest 4 / Tailwind 4 / react-router 7 all require Node 20+. Confirmed by `engines` fields during Phase 0. On Node 20, newer releases become available and the remaining `npm audit` advisories (which have no Node 18 fix) clear. |
| A-12 | Backend uses Spring Boot 3.5.16 (Spring Framework 6), JPA + Hibernate, Flyway for migrations, and **no Lombok** — records for DTOs, explicit accessors for entities. Spring Security 6 was added in Phase 3, when authentication was implemented. | One fewer annotation processor in the build; a small readability cost. Keeping the security starter out of Phase 0 avoided shipping an unused dependency and an auth path with no `admin_user` table behind it. |
| A-13 | The frontend talks to the backend through a Vite dev proxy (`/api` → `localhost:8080`) in development and same-origin in production. | Avoids CORS entirely. A cross-origin deployment would need a CORS configuration, which is out of scope. |
| A-14 | End-to-end browser tests (Playwright) are **not** in v1 scope; accessibility is verified by automated axe checks in unit tests plus manual keyboard testing. | Keeps the deliverable focused on the brief's stated test requirements. |
| A-15 | Purging soft-deleted testimonials after a retention window is out of scope; the `deleted_at` column supports it later. | Not requested. |
| A-16 | There is no email notification, webhook, or export/import of testimonials in v1. | Not requested. |
| A-17 | The public wall's "average rating" and "count" are derived live from approved testimonials; there is no cached aggregate table. | Correctness over performance at this data scale. |
| A-18 | Timestamps are stored in UTC (`timestamptz`) and rendered in the browser's local timezone. | Standard practice. |

---

## 6. Out of Scope for v1

| # | Excluded | Note |
|---|---|---|
| OOS-1 | Multi-tenancy / multiple workspaces | Single workspace only. |
| OOS-2 | User registration, self-service sign-up, password reset, email verification | Admin is seeded and local-only. |
| OOS-3 | OAuth / social login (Google, GitHub) | Not required by the brief. |
| OOS-4 | Public testimonial collection via widget/embeddable JS snippet | Only a first-party hosted form is in scope. |
| OOS-5 | Email notifications to submitters or moderators on moderation events | Not requested. |
| OOS-6 | Import/export (CSV, JSON) and bulk moderation | Not requested. |
| OOS-7 | Image/logo upload on testimonials | Text-only testimonial content. |
| OOS-8 | Rich text / markdown formatting of testimonial text | Plain text only, rendered escaped. |
| OOS-9 | Recurring / scheduled testimonials, seasonal campaigns | Not requested. |
| OOS-10 | Dark mode / theming toggle | Single light theme; tokens are structured to allow a future theme. |
| OOS-11 | Full-text search engine (Elasticsearch/Meilisearch) | `ILIKE`-based search is sufficient at this scale. |
| OOS-12 | Redis / external cache for rate limiting or sessions | Single-instance in-process limits. |
| OOS-13 | CI/CD pipeline configuration, container image publishing | Local run is the target; scripts are provided instead. |
| OOS-14 | Playwright end-to-end suite | See A-14. |
| OOS-15 | GDPR export/erasure tooling and data-retention job | The data model supports it; tooling is not built. |
| OOS-16 | CDN, load balancer, and horizontal scaling configuration | Single-instance deployment. |

---

## 7. Acceptance Summary (product-level)

The feature set is considered delivered when an evaluator can:

1. Open the public form as an anonymous visitor, submit a valid testimonial with consent, and receive a
   confirmation — and see it appear in the admin inbox as pending.
2. Open the public wall as an anonymous visitor and see **only** approved testimonials.
3. Log into the admin area with the seeded credentials, see counts that match the underlying data, and
   filter the inbox by status.
4. Approve a pending testimonial and observe it appear on the public wall.
5. Reject a pending testimonial with a reason and observe it removed from the wall and pending filter.
6. Delete a testimonial and observe it removed from the wall and counts, with an audit entry recorded.
7. Attempt every admin endpoint while logged out and receive 401/403, never data or a mutation.
8. Submit an invalid testimonial (empty name, rating 0 and 6, too-short text, unticked consent) and see
   inline, field-linked validation errors.
9. Navigate every screen using only the keyboard, and pass an automated axe accessibility check.
10. Use the whole app on a 360 px-wide viewport with no horizontal scrolling.

---

## 8. Related Documents

- [`docs/data-model.md`](./data-model.md) — entities, fields, relationships, constraints, ER diagram.
- [`docs/api.md`](./api.md) — the full API reference with example requests and responses.
- [`docs/implementation-plan.md`](./implementation-plan.md) — phased build plan with acceptance criteria.
