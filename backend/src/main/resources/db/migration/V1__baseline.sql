-- LoveProof baseline schema.
--
-- Flyway owns the schema; Hibernate runs with ddl-auto=validate and may only
-- verify it. Every integrity rule is expressed here as well as in the API
-- layer, so a bug in any single writer cannot corrupt the data.
--
-- Conventions:
--   * snake_case columns, camelCase in the JSON API.
--   * timestamptz timestamps, stored and compared in UTC.
--   * status and deleted_at are orthogonal: a soft delete never rewrites the
--     moderation status.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------------
-- admin_user
-- ---------------------------------------------------------------------------
CREATE TABLE admin_user (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    username      varchar(50)  NOT NULL,
    password_hash varchar(100) NOT NULL,
    display_name  varchar(100) NOT NULL,
    role          varchar(16)  NOT NULL DEFAULT 'ADMIN',
    enabled       boolean      NOT NULL DEFAULT TRUE,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    last_login_at timestamptz  NULL,

    CONSTRAINT ck_admin_user_role CHECK (role IN ('ADMIN', 'MODERATOR')),
    CONSTRAINT ck_admin_user_password_hash CHECK (btrim(password_hash) <> ''),
    CONSTRAINT ck_admin_user_username_length CHECK (char_length(btrim(username)) BETWEEN 1 AND 50)
);

-- Usernames are stored lower-cased, so the unique index is case-insensitive.
CREATE UNIQUE INDEX uq_admin_user_username ON admin_user (lower(username));

-- ---------------------------------------------------------------------------
-- testimonial
-- ---------------------------------------------------------------------------
CREATE TABLE testimonial (
    id                    uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                  varchar(100) NOT NULL,
    role                  varchar(120) NULL,
    company               varchar(120) NULL,
    rating                smallint     NOT NULL,
    content               text         NOT NULL,
    status                varchar(16)  NOT NULL DEFAULT 'PENDING',
    consent_given         boolean      NOT NULL,
    consent_text_version  varchar(32)  NOT NULL,
    submitted_at          timestamptz  NOT NULL DEFAULT now(),
    moderated_at          timestamptz  NULL,
    moderated_by          uuid         NULL,
    rejection_reason      varchar(255) NULL,
    deleted_at            timestamptz  NULL,
    deleted_by            uuid         NULL,
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    content_fingerprint   char(64)     NOT NULL,
    submitter_ip_hash     char(64)     NULL,

    CONSTRAINT fk_testimonial_moderated_by FOREIGN KEY (moderated_by) REFERENCES admin_user (id) ON DELETE SET NULL,
    CONSTRAINT fk_testimonial_deleted_by   FOREIGN KEY (deleted_by)   REFERENCES admin_user (id) ON DELETE SET NULL,

    CONSTRAINT ck_testimonial_rating_range   CHECK (rating >= 1 AND rating <= 5),
    CONSTRAINT ck_testimonial_status         CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_testimonial_name_length    CHECK (char_length(btrim(name)) BETWEEN 2 AND 100),
    CONSTRAINT ck_testimonial_content_length CHECK (char_length(btrim(content)) BETWEEN 20 AND 2000),
    CONSTRAINT ck_testimonial_role_length    CHECK (role IS NULL OR char_length(btrim(role)) <= 120),
    CONSTRAINT ck_testimonial_company_length CHECK (company IS NULL OR char_length(btrim(company)) <= 120),
    -- Consent can never be withdrawn by editing the row to false.
    CONSTRAINT ck_testimonial_consent_required CHECK (consent_given = TRUE),
    -- A rejection always carries a reason.
    CONSTRAINT ck_testimonial_rejection_reason
        CHECK ((status = 'REJECTED' AND rejection_reason IS NOT NULL) OR (status <> 'REJECTED')),
    -- Pending means untouched.
    CONSTRAINT ck_testimonial_moderation_fields
        CHECK ((status = 'PENDING' AND moderated_at IS NULL) OR (status <> 'PENDING' AND moderated_at IS NOT NULL)),
    CONSTRAINT ck_testimonial_fingerprint_length CHECK (char_length(content_fingerprint) = 64)
);

-- Moderation inbox ordering and per-status counts.
CREATE INDEX idx_testimonial_status_submitted ON testimonial (status, submitted_at DESC);

-- Public wall: partial, so the index stays small regardless of queue size.
CREATE INDEX idx_testimonial_live_approved
    ON testimonial (submitted_at DESC)
    WHERE status = 'APPROVED' AND deleted_at IS NULL;

-- Wall filter by rating.
CREATE INDEX idx_testimonial_rating_approved
    ON testimonial (rating)
    WHERE status = 'APPROVED' AND deleted_at IS NULL;

-- Duplicate detection.
CREATE INDEX idx_testimonial_fingerprint ON testimonial (content_fingerprint);

-- Per-IP rate-limit bookkeeping and abuse review.
CREATE INDEX idx_testimonial_submitter ON testimonial (submitter_ip_hash, submitted_at DESC);

-- Fast exclusion of soft-deleted rows.
CREATE INDEX idx_testimonial_deleted_at ON testimonial (deleted_at);

-- ---------------------------------------------------------------------------
-- moderation_action
-- ---------------------------------------------------------------------------
-- Append-only audit log. Rows are inserted, never updated, never hard-deleted.
CREATE TABLE moderation_action (
    id              bigserial    PRIMARY KEY,
    testimonial_id  uuid         NOT NULL,
    admin_user_id   uuid         NULL,
    admin_username  varchar(50)  NOT NULL,
    action          varchar(16)  NOT NULL,
    previous_status varchar(16)  NULL,
    new_status      varchar(16)  NULL,
    reason          varchar(255) NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_moderation_action_testimonial FOREIGN KEY (testimonial_id) REFERENCES testimonial (id) ON DELETE CASCADE,
    -- Nulled if the admin row is removed; the denormalised username survives.
    CONSTRAINT fk_moderation_action_admin      FOREIGN KEY (admin_user_id)  REFERENCES admin_user (id) ON DELETE SET NULL,

    CONSTRAINT ck_moderation_action_action CHECK (action IN ('SUBMIT', 'APPROVE', 'REJECT', 'DELETE', 'RESTORE')),
    CONSTRAINT ck_moderation_action_status CHECK (
        (previous_status IS NULL OR previous_status IN ('PENDING', 'APPROVED', 'REJECTED'))
        AND (new_status IS NULL OR new_status IN ('PENDING', 'APPROVED', 'REJECTED'))
    )
);

CREATE INDEX idx_moderation_action_testimonial ON moderation_action (testimonial_id, created_at DESC);
CREATE INDEX idx_moderation_action_created_at ON moderation_action (created_at DESC);
