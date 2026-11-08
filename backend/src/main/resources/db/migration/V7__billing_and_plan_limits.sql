-- Billing: the subscription status, and the webhook ledger.
--
-- The plan is not stored as a fact about the world: it is derived from the
-- subscription status. `app_user.plan` already exists and stays, but the
-- status is what the webhook writes and what the plan is computed from, so
-- that "cancelling keeps Premium until the period ends" and "a failed
-- renewal keeps Premium" both fall out of one rule rather than two.
--
-- The ledger is what makes the webhook idempotent. Stripe delivers events
-- more than once, and a delivery can arrive out of order. The primary key on
-- `stripe_event.event_id` is the guard: a replayed event cannot be inserted,
-- so its transaction rolls back and no effect is applied twice.

-- ---------------------------------------------------------------------------
-- app_user.subscription_status
-- ---------------------------------------------------------------------------
-- The subscription status, using Stripe's own vocabulary so the webhook
-- mapping is a lookup rather than an interpretation. NONE means the account
-- has never subscribed.
ALTER TABLE app_user
    ADD COLUMN subscription_status varchar(32) NOT NULL DEFAULT 'NONE';

ALTER TABLE app_user
    ADD CONSTRAINT ck_app_user_subscription_status
        CHECK (subscription_status IN (
            'NONE', 'active', 'trialing', 'past_due', 'canceled',
            'unpaid', 'incomplete', 'incomplete_expired', 'paused'));

-- A Stripe subscription belongs to exactly one account. The uniqueness is
-- what stops a second account from claiming the same subscription and
-- taking over its plan.
CREATE UNIQUE INDEX uq_app_user_stripe_subscription
    ON app_user (stripe_subscription_id)
    WHERE stripe_subscription_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- stripe_event
-- ---------------------------------------------------------------------------
-- The webhook ledger.
--
-- One row per delivered event. The `outcome` column records what the event
-- did -- APPLIED, IGNORED or NO_ACCOUNT -- so the ledger explains itself
-- without anybody having to correlate it with the Stripe dashboard.
CREATE TABLE stripe_event (
    -- Stripe's event identifier. The primary key is the idempotency guard.
    event_id     varchar(255) PRIMARY KEY,
    event_type   varchar(64)  NOT NULL,
    -- The account the event was applied to, when there was one. Null when
    -- the event named no known account.
    account_id   uuid         NULL,
    outcome      varchar(32)  NOT NULL,
    received_at  timestamptz  NOT NULL DEFAULT now(),

    CONSTRAINT fk_stripe_event_account FOREIGN KEY (account_id)
        REFERENCES app_user (id) ON DELETE SET NULL,
    CONSTRAINT ck_stripe_event_outcome
        CHECK (outcome IN ('APPLIED', 'IGNORED', 'NO_ACCOUNT'))
);

-- The reconciliation job reads the ledger's recent history, and the table
-- is otherwise append-only, so the index serves both.
CREATE INDEX idx_stripe_event_received_at ON stripe_event (received_at);
