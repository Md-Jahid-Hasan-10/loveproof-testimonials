-- SaaS schema: users, spaces, and the per-space testimonial collections.
--
-- The application grows from a single-tenant wall into a multi-tenant SaaS:
-- a signed-in user owns Spaces, each Space has its own public URL, its own
-- form configuration, and its own collection of testimonials.
--
-- The existing `testimonial` table is the single-tenant wall's table: one
-- shared collection with a moderation status, a soft delete and an audit
-- trail. The SaaS product has a different shape -- a testimonial belongs to
-- exactly one Space, is visible by default, and has no approval step -- so
-- it gets its own table rather than being bent into the old one.
--
-- Nothing here removes the old tables. The single-tenant surface and the SaaS
-- surface coexist; the old one is retired once the SaaS surface is complete.

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ---------------------------------------------------------------------------
-- app_user
-- ---------------------------------------------------------------------------
-- The account. Replaces `admin_user` for the SaaS surface, but is a separate
-- table: the roles mean different things. `admin_user` is a single seeded
-- operator of the whole installation; `app_user` is a customer of the
-- service who owns their own Spaces.
CREATE TABLE app_user (
    id                uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    email             varchar(254) NOT NULL,
    -- Nullable: a Google-only account has no password. The hash is stored
    -- even so, so a later password can be set without a second migration.
    password_hash     varchar(100) NULL,
    display_name      varchar(100) NOT NULL,
    -- Google users are treated as verified (PRD 4.1); email/password users
    -- must click the emailed link first.
    email_verified    boolean      NOT NULL DEFAULT FALSE,
    -- How the account signs in. Both may be set: a user who registers with a
    -- password and later signs in with Google keeps one account (OPEN-A).
    auth_provider     varchar(16)  NOT NULL DEFAULT 'PASSWORD',
    -- The Google subject identifier, when the account has signed in with
    -- Google. Unique, so one Google identity maps to exactly one account.
    google_subject    varchar(255) NULL,
    -- The plan. Stripe webhooks are the only thing that changes it from
    -- FREE; nothing else may upgrade an account.
    plan              varchar(16)  NOT NULL DEFAULT 'FREE',
    -- Stripe's customer and subscription identifiers, needed to open the
    -- billing portal and to cancel a subscription.
    stripe_customer_id     varchar(255) NULL,
    stripe_subscription_id varchar(255) NULL,
    -- Set when the user has cancelled but the paid period has not ended yet.
    -- The plan stays PREMIUM until `current_period_end` passes.
    cancel_at_period_end   boolean     NOT NULL DEFAULT FALSE,
    current_period_end     timestamptz NULL,
    -- Set when a renewal failed but Stripe is still retrying. Premium access
    -- continues while this is true.
    subscription_past_due  boolean     NOT NULL DEFAULT FALSE,
    enabled          boolean      NOT NULL DEFAULT TRUE,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    last_login_at    timestamptz  NULL,

    CONSTRAINT ck_app_user_plan CHECK (plan IN ('FREE', 'PREMIUM')),
    CONSTRAINT ck_app_user_auth_provider CHECK (auth_provider IN ('PASSWORD', 'GOOGLE')),
    -- A Google-only account has no password; an email/password account must
    -- have one. Google-only is expressed by the provider, not by a null
    -- hash on a PASSWORD account.
    CONSTRAINT ck_app_user_password_present
        CHECK (auth_provider = 'GOOGLE' OR password_hash IS NOT NULL),
    CONSTRAINT ck_app_user_password_hash CHECK (password_hash IS NULL OR btrim(password_hash) <> ''),
    CONSTRAINT ck_app_user_email_length CHECK (char_length(btrim(email)) BETWEEN 3 AND 254),
    CONSTRAINT ck_app_user_display_name_length CHECK (char_length(btrim(display_name)) BETWEEN 1 AND 100)
);

-- Emails are stored lower-cased, so the unique index is case-insensitive.
CREATE UNIQUE INDEX uq_app_user_email ON app_user (lower(email));

-- One Google identity maps to exactly one account.
CREATE UNIQUE INDEX uq_app_user_google_subject ON app_user (google_subject) WHERE google_subject IS NOT NULL;

-- The Stripe customer, when there is one.
CREATE UNIQUE INDEX uq_app_user_stripe_customer ON app_user (stripe_customer_id) WHERE stripe_customer_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- auth_token
-- ---------------------------------------------------------------------------
-- Single-use, expiring tokens for email verification and password reset.
--
-- Both flows need the same properties -- one use, a deadline, and a link the
-- user cannot guess -- so they share one table and differ by `purpose`.
CREATE TABLE auth_token (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid         NOT NULL,
    purpose     varchar(16)  NOT NULL,
    -- The token itself, stored as a SHA-256 hash. The plaintext only ever
    -- exists in the emailed link, so a database leak does not hand over
    -- working reset links.
    token_hash  char(64)     NOT NULL,
    expires_at  timestamptz  NOT NULL,
    used_at     timestamptz  NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_auth_token_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_auth_token_purpose CHECK (purpose IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    CONSTRAINT ck_auth_token_hash_length CHECK (char_length(token_hash) = 64)
);

-- Lookup by hash; the index also serves the "is this token still valid"
-- question without scanning.
CREATE UNIQUE INDEX uq_auth_token_hash ON auth_token (token_hash);

-- Expired-and-unused tokens for one user and purpose.
CREATE INDEX idx_auth_token_user_purpose ON auth_token (user_id, purpose);

-- ---------------------------------------------------------------------------
-- space
-- ---------------------------------------------------------------------------
-- A collection page. One user owns many; each has its own public URL and its
-- own form configuration.
CREATE TABLE space (
    id                uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id          uuid         NOT NULL,
    title             varchar(120) NOT NULL,
    subtitle          varchar(255) NULL,
    -- The public URL segment: /s/{slug}. Unique across the platform, so a
    -- link can never be ambiguous.
    slug              varchar(80)  NOT NULL,
    -- The form configuration. The fixed fields (name, email) are always on
    -- and always required, so they are not stored -- only the optional ones
    -- and their settings are.
    field_company     boolean      NOT NULL DEFAULT FALSE,
    field_company_required boolean NOT NULL DEFAULT FALSE,
    field_social      boolean      NOT NULL DEFAULT FALSE,
    field_social_required  boolean NOT NULL DEFAULT FALSE,
    field_website     boolean      NOT NULL DEFAULT FALSE,
    field_website_required  boolean NOT NULL DEFAULT FALSE,
    field_job_title   boolean      NOT NULL DEFAULT FALSE,
    field_job_title_required boolean NOT NULL DEFAULT FALSE,
    -- Whether the public form shows a rating control.
    rating_enabled    boolean      NOT NULL DEFAULT FALSE,
    -- The theme key. Basic themes are available on Free; the premium ones
    -- require Premium, and the backend rejects a Free user selecting one.
    theme             varchar(32)  NOT NULL DEFAULT 'basic',
    -- Set once the Space receives its first submission, after which the slug
    -- can no longer change (PRD 4.2).
    slug_locked       boolean      NOT NULL DEFAULT FALSE,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_space_owner FOREIGN KEY (owner_id) REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT ck_space_title_length CHECK (char_length(btrim(title)) BETWEEN 1 AND 120),
    CONSTRAINT ck_space_subtitle_length CHECK (subtitle IS NULL OR char_length(btrim(subtitle)) <= 255),
    -- The slug is lowercase, hyphen-separated and never empty.
    CONSTRAINT ck_space_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    CONSTRAINT ck_space_slug_length CHECK (char_length(slug) BETWEEN 1 AND 80),
    -- A field that is required must also be enabled: a required field that
    -- is not collected could never be answered.
    CONSTRAINT ck_space_optional_field_required
        CHECK (
            (NOT field_company_required OR field_company)
          AND (NOT field_social_required OR field_social)
          AND (NOT field_website_required OR field_website)
          AND (NOT field_job_title_required OR field_job_title)
        )
);

-- The slug is unique across the platform, not per owner.
CREATE UNIQUE INDEX uq_space_slug ON space (slug);

-- The owner's Spaces, newest first.
CREATE INDEX idx_space_owner_created ON space (owner_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- space_testimonial
-- ---------------------------------------------------------------------------
-- A testimonial collected by one Space.
--
-- There is no approval step (PRD 4.7): a submission is visible by default,
-- not on the Wall of Love, and not a favorite. The owner curates it.
CREATE TABLE space_testimonial (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    space_id       uuid         NOT NULL,
    name           varchar(100) NOT NULL,
    -- The submitter's email. Never exposed publicly -- not in the embed
    -- output, and not in the owner's own inbox after collection, because
    -- the owner's inbox shows what they need to curate.
    email          varchar(254) NOT NULL,
    content        text         NOT NULL,
    -- Nullable, because a Space can have ratings turned off.
    rating         smallint     NULL,
    company        varchar(120) NULL,
    -- Social links are a single URL. The field is "social media links" in
    -- the PRD; one URL per submission keeps the embed output simple.
    social_link    varchar(500) NULL,
    website        varchar(500) NULL,
    job_title      varchar(120) NULL,
    -- Consent is optional and never gates display (PRD 4.3, rule 9).
    consent_given  boolean      NOT NULL DEFAULT FALSE,
    favorite       boolean      NOT NULL DEFAULT FALSE,
    wall_of_love   boolean      NOT NULL DEFAULT FALSE,
    visible        boolean      NOT NULL DEFAULT TRUE,
    submitted_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_space_testimonial_space FOREIGN KEY (space_id) REFERENCES space (id) ON DELETE CASCADE,
    CONSTRAINT ck_space_testimonial_name_length CHECK (char_length(btrim(name)) BETWEEN 1 AND 100),
    CONSTRAINT ck_space_testimonial_content_length CHECK (char_length(btrim(content)) BETWEEN 1 AND 2000),
    CONSTRAINT ck_space_testimonial_email_length CHECK (char_length(btrim(email)) BETWEEN 3 AND 254),
    -- A rating is either absent (the Space has ratings off) or 1 to 5.
    CONSTRAINT ck_space_testimonial_rating CHECK (rating IS NULL OR (rating >= 1 AND rating <= 5)),
    CONSTRAINT ck_space_testimonial_company_length CHECK (company IS NULL OR char_length(btrim(company)) <= 120),
    CONSTRAINT ck_space_testimonial_social_length CHECK (social_link IS NULL OR char_length(btrim(social_link)) <= 500),
    CONSTRAINT ck_space_testimonial_website_length CHECK (website IS NULL OR char_length(btrim(website)) <= 500),
    CONSTRAINT ck_space_testimonial_job_title_length CHECK (job_title IS NULL OR char_length(btrim(job_title)) <= 120)
);

-- The owner's inbox: favorites first, then newest.
CREATE INDEX idx_space_testimonial_inbox ON space_testimonial (space_id, favorite DESC, submitted_at DESC);

-- The embed query: on the Wall of Love and visible.
CREATE INDEX idx_space_testimonial_embed
    ON space_testimonial (space_id, submitted_at DESC)
    WHERE wall_of_love = TRUE AND visible = TRUE;

-- The owner's dashboard: unique submitters, by email.
CREATE INDEX idx_space_testimonial_space_email ON space_testimonial (space_id, lower(email));

-- The dashboard chart: submissions per day.
CREATE INDEX idx_space_testimonial_submitted ON space_testimonial (submitted_at DESC);
