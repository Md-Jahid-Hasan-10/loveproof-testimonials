---
name: verify-feature
description: Verify the current feature against requirements and run relevant tests.
---

# Verify a feature

Prove — do not assume — that a feature works. Report what actually ran.

## 1. Read the requirements

- `docs/requirements.md` and `docs/implementation-plan.md` are the primary sources.
- If either file is missing, say so plainly and fall back to the specs that do exist
  (`docs/PRD-testimonial-saas.md` is the current spec of record). Do not invent
  acceptance criteria, and do not treat a missing document as "no requirements".

## 2. Identify the feature's acceptance criteria

- Inspect the current changes (`git diff`, `git status`, `git log`) to work out what
  feature is under review and which files it touches.
- Extract the acceptance criteria for that feature from the requirements documents.
- Write the criteria out explicitly before testing, so the report can map each one
  to a result. Criteria that cannot be located in any document are marked
  "unverified — no stated criterion" rather than silently passed or failed.

## 3. Run the tests, using the commands the repository documents

Read `README.md` and `CLAUDE.md` for the real command lines. In this repository that
means, typically:

- Backend: `cd backend && mvn -o test` (JUnit 5, Spring Boot Test, Testcontainers
  for PostgreSQL 16).
- Frontend: `cd frontend && npm run lint`, `npx vitest run`, `npm run build`
  (the build enforces the bundle budget — 100 kB per chunk, 250 kB total).
- Targeted runs are fine when a full suite is slow: `-Dtest=SomeTest` on the
  backend, `npx vitest run path/to/File.test.tsx` on the frontend.

Run the narrowest command that actually exercises the feature, then widen to the
full suite so the report can state whether the change broke anything else. Note
that `-DskipTests` or a mock-only run does not count as verification.

## 4. Check the behaviour and the error cases

For each acceptance criterion, check both the happy path and the failure path —
the rejection, the boundary, the unauthorized case. For each, state what the code
does and what the PRD requires, and flag any divergence. Where behaviour is only
exercised by a test that pre-seeds the state under test (for example a test that
sets the Stripe subscription id on the account before the webhook arrives), note
that the untested path is the one that would fail in production.

## 5. Report

Report, in order:

1. **Commands executed** — the exact command lines, verbatim.
2. **Actual results** — the real output summary (tests run, failures, errors,
   skipped; build status; bundle size). Quote the numbers from the run, never
   from memory or from a previous session.
3. **Acceptance criteria** — each one, with a verdict: met / not met /
   partially met / unverified, plus where it was verified (file:line, test name).
4. **Untested behaviour** — what no test covers, and why it matters.
5. **Remaining defects** — with file paths and line numbers, ordered by severity.

## Rules

- Do not change production code automatically during review. If a fix is
  obviously needed, describe it and let the user decide.
- Do not report a test as passed unless it actually passed. If a suite fails,
  report the failure with the test name and the message — never summarise it as
  "mostly green" or omit it.
- If a command cannot be run (missing tool, no database, no network), say which
  one and why, and mark the affected criteria as unverified rather than passing.
- Distinguish real defects from stylistic deviations and from nitpicks, and
  label them separately so severity is not inflated.
