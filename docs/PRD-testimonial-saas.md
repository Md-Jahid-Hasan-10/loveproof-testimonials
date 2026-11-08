# PRD: Testimonial Collection SaaS (MVP)

Audience: AI coding agent (Puku CLI). All requirements below are decided except those in Section 11, which are marked **[OPEN]**. Do not invent rules for open items. Use the stated default or ask.

---

## 1. Product Overview

A SaaS platform where users create testimonial collection pages ("Spaces"), share a public link, collect testimonials from customers who don't need an account, manage them in an inbox, and display selected ones on external websites via an embeddable widget.

Two plans: **Free** and **Premium** ($9.99/month, Stripe, monthly billing only, no trial).

**Stack:** Java + Spring Boot, React + Tailwind CSS, PostgreSQL, Flyway, Spring Security (email/password + Google login), Stripe. Modular monolith with clearly separated modules (auth, spaces, submissions, testimonials, embed, billing, analytics).

## 2. Goals and Non-Goals

**Goals**
- Create a Space and collect its first testimonial in minutes.
- Let owners curate and embed testimonials on any site.
- Monetize through backend-enforced plan limits.

**Non-Goals (MVP)**
- Video or image testimonials
- Workspaces, teams, roles/permissions. One user = one account.
- Drag-and-drop form builder, custom fields
- Custom domains, white-label, agency features
- Integrations (Zapier etc.), AI features, social import
- Notifications (email/Slack), beyond auth and Stripe-related emails
- Account deletion
- Social login providers other than Google
- Annual billing, free trial

## 3. User Roles

| Role | Description |
|---|---|
| Visitor | Unauthenticated. Can register, log in, view public Space pages, submit testimonials. |
| User (Free / Premium) | Authenticated owner of Spaces. Sees only their own data. |
| System | Stripe webhooks, scheduled jobs. |

## 4. Functional Requirements

### 4.1 Authentication
- Register with email + password, or sign in with Google.
- Log in; log out.
- Email verification via emailed link (email/password users). Google users are treated as verified.
- Forgot/reset password via emailed, expiring, single-use link (email/password accounts only).
- Authenticated users land on the dashboard.
- Signing in with Google using an email that already has an email/password account signs into that same account **[OPEN-A]**.

**Validation:** valid email format; unique email (case-insensitive); password min 8 chars.

**Acceptance criteria**
- Registering with a new email creates an unverified account and sends a verification email.
- Registering with an existing email returns a clear error and creates nothing.
- First-time Google sign-in creates a verified account and lands on the dashboard.
- Wrong credentials return a generic error (no hint about which field was wrong).
- A used or expired reset/verification link is rejected.
- Unauthenticated requests to protected endpoints return 401.
- Unverified users can log in but cannot create Spaces; the UI prompts them to verify.

### 4.2 Spaces Management
Logged-in users can create, view, edit, delete Spaces.

**Space fields:** title (required), subtitle/description, unique slug, form configuration, theme, rating enabled (boolean), created/updated timestamps.

**Creation flow (wizard):** title + subtitle → configure fields → choose theme → ratings on/off → save → success page with public URL and copy-link button.

**Acceptance criteria**
- Slug is unique across the platform; a duplicate is rejected with an error.
- Slug is auto-generated from the title, editable at creation, and locked once the Space receives its first submission.
- Creating a Space beyond the plan limit is rejected by the backend with a clear "limit reached" error and the UI prompts an upgrade.
- Users can only list/view/edit/delete their own Spaces; others return 404.
- Deleting a Space requires confirmation and permanently deletes it, its testimonials, and its public URL.
- Success page shows the public URL; copy-link copies it to the clipboard.

### 4.3 Form Configuration
- Fixed default fields: **Name** (required), **Email** (required). Cannot be disabled or made optional.
- Predefined optional fields: Company name, Social media links, Website URL, Job title.
- Per optional field: enabled/disabled, required/optional.
- Testimonial text is always present and required.
- Consent checkbox is always shown and **optional**. Its value is stored with the testimonial but does not restrict display or Wall of Love inclusion.
- Configurable before and after creation. No drag-and-drop or custom fields.

**Acceptance criteria**
- Disabled fields don't appear on the public form and are rejected if submitted.
- Fields marked required block submission when empty (frontend and backend).
- Editing the form doesn't alter existing testimonials.
- A submission with the consent box unchecked is accepted.

### 4.4 Themes
- Predefined themes with preview in create/edit.
- Basic themes available on Free; premium themes require Premium.

**Acceptance criteria**
- Selecting a premium theme on Free is rejected by the backend.
- The public form renders with the selected theme.
- After a downgrade, Spaces already using a premium theme keep rendering it; selecting a premium theme again is blocked.

### 4.5 Public Testimonial Submission
Public URL per Space (`/s/{slug}`, final path chosen by implementation). No authentication.

**Page shows:** Space title and subtitle, configured fields, testimonial text input, rating control (if enabled), consent checkbox.

**After success:** save the testimonial, show a thank-you page with a funny GIF picked at random from a small set of bundled static assets.

**Validation:** required fields enforced on frontend and backend; email format; URL format for website/social links; rating is an integer 1–5; testimonial text max 2,000 characters.

**Abuse protection (required):** rate limiting per IP, honeypot field, input sanitization (XSS-safe output everywhere), Cloudflare Turnstile CAPTCHA.

**Duplicates:** multiple submissions from the same email to the same Space are allowed.

**Acceptance criteria**
- A visitor submits without logging in and sees the thank-you page.
- Missing required fields return field-level errors.
- Submitting to a nonexistent or deleted slug returns 404.
- When the Space has reached its testimonial limit, or is read-only (see 4.9), the submission is rejected and the submitter sees a neutral "not accepting responses" message. The owner sees a banner on the Space explaining why.
- Submitted HTML/script content is stored and rendered as inert text.
- Rapid repeated submissions from one IP are throttled (429).
- A failed CAPTCHA blocks the submission.

### 4.6 Dashboard
**Shows:** total Spaces, total testimonials, total unique submitters, testimonials per day (time-series chart), recent submissions.

- Free users see an upgrade notice.
- Unique submitters = distinct email across the user's testimonials.
- Daily buckets use UTC.

**Acceptance criteria**
- Numbers reflect only the user's own data.
- Chart shows daily counts for a selectable range (default last 30 days); days with zero show 0.
- Deleted testimonials are excluded from counts.
- Premium users don't see the upgrade notice.

### 4.7 Testimonial Inbox (per Space)
**Columns:** name, email, text, rating, submission date, favorite status, Wall of Love status, visibility status.

**Actions:** favorite/unfavorite, add/remove Wall of Love, edit, delete, hide/show.

**List behavior:** favorites first, then newest first; search (name, email, text); filters (favorite, Wall of Love, hidden, rating); pagination.

**Defaults on submission:** visible = yes, Wall of Love = no, favorite = no. There is no approval step.

**Acceptance criteria**
- Favorited items always sort above non-favorited.
- Hidden testimonials stay in the inbox (marked hidden) but never appear publicly.
- Deleting a testimonial requires confirmation and removes it permanently.
- Editing updates text and is reflected in embeds on next load.
- Only the Space owner can view or act on its testimonials.
- Adding to the Wall of Love is always an explicit owner action, regardless of consent value.
- In a read-only Space (see 4.9), the inbox is viewable but all mutating actions are disabled and rejected by the backend.

### 4.8 Wall of Love and Embedding
**Embed configuration page** (one configuration per Space):
- Layout: Masonry (Free), Carousel (Premium)
- Light/dark mode (Free)
- Background color (Premium)
- Animation on/off (Premium)
- Live preview that updates on every change
- Generated JavaScript snippet with copy button

**Embed behavior**
- Works on any external website without the React app.
- Shows only testimonials that are on the Wall of Love **and** visible.
- Reflects hides, deletes, and removals without the owner regenerating the snippet.
- Embed output is XSS-safe and never exposes email. Publicly exposed fields: name, text, rating, and enabled public profile fields (company, job title, website, social links if enabled for the Space).

**Acceptance criteria**
- Pasting the snippet into a plain HTML page renders the testimonials.
- Hiding or removing a testimonial removes it from the embed.
- Embed for a deleted Space renders nothing and doesn't error the host page.
- Premium-only options (carousel, background color, animation) are rejected by the backend for Free users, and the UI shows them locked with an upgrade prompt.
- After a downgrade, an existing embed keeps rendering its saved premium settings; changing to a premium option is blocked.
- The public embed data endpoint is read-only, unauthenticated, and cacheable.

### 4.9 Billing and Subscriptions
**Billing page shows:** current plan, available plans with features and limits, upgrade option, subscription status (including cancel-at-period-end and past due).

**Upgrade flow**
1. Free user clicks Upgrade.
2. Backend creates a Stripe Checkout Session; the user is redirected to Stripe-hosted checkout.
3. Stripe sends webhook events.
4. Backend verifies the signature and processes the event idempotently.
5. Subscription is updated only after the confirmed webhook.
6. User returns to the app and sees the updated status.

**Cancellation:** takes effect at the end of the paid period. Premium access continues until then. The Billing page shows the end date and allows resuming before it passes.

**Payment failure:** Stripe retries per its configured schedule. Status becomes **past due** and a banner is shown. Premium access continues while past due. On final failure the account is downgraded to Free.

**Downgrade with data over Free limits** (after cancellation end or final payment failure):
- No data is deleted.
- Creating new Spaces is blocked while the user has more Spaces than the Free limit.
- A Space with more testimonials than the Free limit accepts no new submissions.
- Spaces beyond the Free Space limit are **read-only** until the user is back within limits or re-upgrades. Which Spaces are read-only: **[OPEN-B]**.
- Read-only Space: viewable, but no edits and no new submissions. Existing embeds keep rendering.
- Re-upgrading restores full access immediately after the confirmed webhook.

**Acceptance criteria**
- The success redirect alone never upgrades the account. Only a verified webhook does.
- Webhooks with invalid signatures are rejected (400) and not processed.
- Replaying the same webhook event doesn't duplicate effects.
- If the webhook is delayed after checkout, the UI shows a "processing" state and refreshes until confirmed.
- Cancelling keeps Premium until period end, then downgrades.
- A failed renewal sets "past due" without removing Premium; final failure downgrades.
- A downgraded user with 10 Spaces keeps all 10, cannot create an 11th, and cannot mutate read-only Spaces (verified by direct API calls).
- Plan limits are enforced on the backend on every create/submit request.

### 4.10 Account Settings
- Update name and password (email/password accounts); view email verification status.

## 5. User Flows

1. **Owner onboarding:** Register (email or Google) → verify email if needed → dashboard → Create Space wizard → success page → copy link.
2. **Customer submission:** Open link → fill form → consent (optional) → CAPTCHA → submit → thank-you page + GIF.
3. **Curation:** Inbox → favorite / add to Wall of Love / hide / edit / delete.
4. **Embedding:** Open Space embed page → choose layout and options → watch preview → copy snippet → paste on external site.
5. **Upgrade:** Billing → Upgrade → Stripe Checkout → return → webhook confirms → Premium status shown.
6. **Cancel:** Billing → Cancel → Premium until period end → auto-downgrade.

## 6. Business Rules

1. A user can create multiple Spaces, within plan limits.
2. Each Space belongs to exactly one user; each testimonial to exactly one Space.
3. Each Space has its own public URL and its own form configuration.
4. Customers never authenticate to submit.
5. Rating is optional and set per Space.
6. Required fields are validated on frontend and backend.
7. Only the owner can manage their Spaces and testimonials; every user sees only their own data.
8. New testimonials are visible by default and not on the Wall of Love.
9. Consent is optional and never gates display or Wall of Love.
10. Only testimonials that are on the Wall of Love and visible appear in embeds.
11. Hidden or deleted testimonials never appear publicly.
12. Plan limits are enforced on the backend, not just the UI.
13. Downgrades never delete data.
14. Stripe webhooks are signature-verified and idempotent.
15. Subscription state changes only from verified Stripe events.

## 7. Subscription and Feature Limits

| | Free | Premium ($9.99/month) |
|---|---|---|
| Spaces | 3 | 25 |
| Testimonials per Space | 100 | 1,000 |
| Themes | Basic | Basic + premium |
| Embed layouts | Masonry | Masonry + Carousel |
| Embed light/dark mode | Yes | Yes |
| Embed background color | No | Yes |
| Embed animation | No | Yes |

Limits are checked on Space creation, public submission, and embed configuration saves.

## 8. Non-Functional Requirements

- **Security:** Spring Security; BCrypt password hashing; Google OAuth2 login; CSRF/CORS configured correctly; all public inputs sanitized; Stripe and OAuth secrets via environment config; HTTPS only.
- **Data isolation:** every owner-scoped query filters by the authenticated user; cross-user access returns 404.
- **Performance:** dashboard and inbox pages load under 2s at expected MVP volumes; embed endpoint cacheable and lightweight.
- **Reliability:** webhook processing is idempotent and retry-safe; Flyway manages all schema changes.
- **Responsive UI:** usable on mobile, tablet, desktop. Public submission page is mobile-first.
- **Embed independence:** snippet is a self-contained script with no dependency on the React bundle; styles are scoped so host-site CSS doesn't break it.
- **Maintainability:** modules have clear boundaries and communicate through defined service interfaces, not direct cross-module data access.

## 9. MVP Scope

**Build order**
1. Authentication (email/password + Google)
2. Dashboard shell and Spaces management
3. Space creation and form configuration
4. Public testimonial submission
5. Testimonial inbox and management
6. Wall of Love and embed generator
7. Stripe billing and subscription enforcement
8. Dashboard analytics

**UI:** sidebar navigation; Dashboard; Spaces; Create Space wizard; Inbox; Wall of Love / Embed configuration; Billing; Account Settings. Modern, minimal, Tailwind CSS.

**Out of scope:** everything in Section 2 Non-Goals.

## 10. Acceptance Criteria Summary (End-to-End)

- [ ] A new user can register by email or Google and reach the dashboard.
- [ ] A user can create a Space and gets a working public URL.
- [ ] An anonymous visitor can submit a testimonial (consent unchecked is fine) and sees the thank-you page.
- [ ] The testimonial appears in the owner's inbox as visible, not on the Wall of Love.
- [ ] Favorite, hide, edit, delete, and Wall of Love actions all work and persist.
- [ ] Only Wall of Love + visible testimonials appear in the embed on an external page.
- [ ] A Free user cannot exceed 3 Spaces or 100 testimonials per Space, and cannot use carousel, background color, or animation (verified by direct API calls).
- [ ] Upgrade through Stripe Checkout changes the plan only after a verified webhook.
- [ ] Cancellation keeps Premium until period end; final payment failure downgrades.
- [ ] A downgraded user keeps all data but cannot create new Spaces or mutate read-only Spaces.
- [ ] User A can never read or modify User B's Spaces or testimonials.
- [ ] Dashboard numbers and chart match actual data.

## 11. Open Questions

| ID | Question | Suggested default |
|---|---|---|
| OPEN-A | Google sign-in with an email that already has a password account: sign into the same account, or reject? | *Same account (Google verifies the email)* |
| OPEN-B | When a downgraded user has more Spaces than the Free limit, which ones are read-only? | *The oldest 3 by creation date stay fully editable; the rest are read-only* |
