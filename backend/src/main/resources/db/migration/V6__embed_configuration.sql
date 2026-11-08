-- ---------------------------------------------------------------------------
-- space_embed_config
-- ---------------------------------------------------------------------------
-- One embed configuration per Space.
--
-- The embed is what the owner pastes onto their own website. It is a small
-- self-contained script that fetches the testimonials from the public embed
-- endpoint and renders them, so the configuration has to live on the server
-- rather than inside the snippet: changing it must not require the owner to
-- paste a new snippet (PRD 4.8).
CREATE TABLE space_embed_config (
    space_id          uuid         PRIMARY KEY,
    -- The layout the embed renders in. 'masonry' is available on Free;
    -- 'carousel' needs Premium.
    layout            varchar(32)  NOT NULL DEFAULT 'masonry',
    -- Whether the embed renders in light or dark.
    theme_mode        varchar(16)  NOT NULL DEFAULT 'light',
    -- The background colour, as a hex colour. Premium only. NULL means the
    -- embed uses its own default.
    background_color  varchar(7)   NULL,
    -- Whether the testimonials animate in. Premium only.
    animation         boolean      NOT NULL DEFAULT FALSE,
    -- The public token the embed script uses to fetch its data.
    --
    -- The token is not derived from the slug: an embed is a public URL that
    -- ends up in the page source of somebody else's website, so it is
    -- unguessable and can be rotated without changing the Space's address.
    embed_token       varchar(64)  NOT NULL,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_space_embed_config_space FOREIGN KEY (space_id) REFERENCES space (id) ON DELETE CASCADE,
    CONSTRAINT ck_space_embed_config_layout CHECK (layout IN ('masonry', 'carousel')),
    CONSTRAINT ck_space_embed_config_theme_mode CHECK (theme_mode IN ('light', 'dark')),
    -- A background colour, when there is one, is a hex colour.
    CONSTRAINT ck_space_embed_config_background CHECK (
        background_color IS NULL OR background_color ~ '^#[0-9a-fA-F]{6}$'
    )
);

-- The embed token is looked up on every page load, so it is indexed and
-- unique: two Spaces can never share one token.
CREATE UNIQUE INDEX uq_space_embed_config_token ON space_embed_config (embed_token);
