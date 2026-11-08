-- Resynchronise the seeded administrator from configuration.
--
-- V2 inserted the administrator with `ON CONFLICT DO NOTHING`, so an existing
-- row was never touched. That is right for the operator's own accounts, but
-- it left the seeded account stuck with whatever hash was in the database the
-- first time it ran. If that first hash was malformed -- as it was when the
-- Flyway placeholder chain resolved to the literal text '${dollar}2a${dollar}'
-- instead of a real BCrypt hash -- no password could ever match it, and
-- re-running the migration with a corrected configuration changed nothing.
--
-- The hash is configuration, not data: it comes from
-- `loveproof.admin.password-hash` and is expected to track it. This migration
-- therefore updates the seeded row whenever the stored hash differs from the
-- configured one, so a corrected configuration takes effect on the next start.
--
-- Only the seeded account is touched. Accounts the operator created by hand
-- are not affected, because this statement is keyed on the configured
-- username.
--
-- Placeholders used:
--   admin_username      - lower-cased username
--   admin_password_hash - BCrypt hash, cost >= 10
--   admin_display_name  - name shown in the admin header
--   admin_role          - ADMIN or MODERATOR

UPDATE admin_user
SET password_hash = '${admin_password_hash}',
    display_name  = '${admin_display_name}',
    role          = '${admin_role}'
WHERE lower(username) = lower('${admin_username}')
  AND password_hash IS DISTINCT FROM '${admin_password_hash}';
