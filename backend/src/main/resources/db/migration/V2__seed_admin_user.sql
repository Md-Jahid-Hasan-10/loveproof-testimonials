-- Seed the local administrator.
--
-- The password hash is injected from configuration
-- (`loveproof.admin.password-hash` -> Flyway placeholder `admin_password_hash`),
-- never hard-coded here. The INSERT is idempotent, so re-running the migration
-- is a no-op and an existing row is never overwritten.
--
-- A BCrypt hash contains '$' characters, which Flyway reads as the start of a
-- placeholder. The hash is therefore quoted with ${dollar}, which expands to a
-- literal '$' and is escaped by Spring before the statement reaches the database.
--
-- Placeholders used:
--   admin_username        - lower-cased username
--   admin_display_name    - name shown in the admin header
--   admin_role            - ADMIN or MODERATOR
--   admin_password_hash   - BCrypt hash, cost >= 10
--   dollar                - a literal '$'

INSERT INTO admin_user (username, password_hash, display_name, role, enabled)
VALUES (
    '${admin_username}',
    '${admin_password_hash}',
    '${admin_display_name}',
    '${admin_role}',
    TRUE
)
ON CONFLICT (lower(username)) DO NOTHING;
