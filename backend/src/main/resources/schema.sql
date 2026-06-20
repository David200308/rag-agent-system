-- ── Auth schema ────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS email_whitelist (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ── Conversation history schema ───────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS conversations (
    id         VARCHAR(36)  PRIMARY KEY,          -- UUID
    user_email VARCHAR(255),                       -- nullable; populated when auth is enabled
    archived   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_conv_email (user_email)
);

-- ── Knowledge source index ────────────────────────────────────────────────────
-- Tracks every source that has been ingested into Weaviate.

CREATE TABLE IF NOT EXISTS knowledge_sources (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    source      VARCHAR(512) NOT NULL UNIQUE,   -- the "source" metadata value
    label       VARCHAR(512),                   -- human-friendly name (filename / URL title)
    category    VARCHAR(128),
    chunk_count INT          NOT NULL DEFAULT 0,
    owner_email VARCHAR(255),                   -- uploader; NULL when auth is disabled
    ingested_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ks_source (source),
    INDEX idx_ks_owner  (owner_email)
);

-- Tracks which additional users a knowledge source has been shared with.
CREATE TABLE IF NOT EXISTS knowledge_source_shares (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_id    BIGINT       NOT NULL,
    shared_email VARCHAR(255) NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_source_email (source_id, shared_email),
    CONSTRAINT fk_kss_source FOREIGN KEY (source_id)
        REFERENCES knowledge_sources(id) ON DELETE CASCADE
);

-- ── Conversation share links ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS conversation_shares (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(36)  NOT NULL,
    token           VARCHAR(36)  NOT NULL,
    owner_email     VARCHAR(255) NOT NULL,
    expires_at      DATETIME,                          -- NULL = never expires
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_share_token (token),
    INDEX idx_share_conv (conversation_id),
    CONSTRAINT fk_share_conv FOREIGN KEY (conversation_id)
        REFERENCES conversations(id) ON DELETE CASCADE
);

-- ── Web-fetch domain whitelist ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_preferences (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    timezone   VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_up_email (email)
);

CREATE TABLE IF NOT EXISTS web_fetch_whitelist (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    domain     VARCHAR(253) NOT NULL,
    added_by   VARCHAR(255),
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_wfw_domain_user (domain, added_by)
);

CREATE TABLE IF NOT EXISTS conversation_messages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    role            VARCHAR(16) NOT NULL,           -- user | assistant
    content         TEXT        NOT NULL,
    run_id          VARCHAR(36),                    -- links to AgentResponse.RunMetadata.runId
    created_at      TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conv_msg_conv (conversation_id),
    CONSTRAINT fk_conv_msg_conv FOREIGN KEY (conversation_id)
        REFERENCES conversations(id) ON DELETE CASCADE
);

-- ── Workflow engine ──────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS workflows (
    id             VARCHAR(36)   PRIMARY KEY,
    name           VARCHAR(255)  NOT NULL,
    description    VARCHAR(1000),
    owner_email    VARCHAR(255),
    agent_pattern  VARCHAR(20)   NOT NULL,   -- ORCHESTRATOR | TEAM
    team_exec_mode VARCHAR(20),               -- PARALLEL | SEQUENTIAL (TEAM only)
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_wf_owner (owner_email)
);

CREATE TABLE IF NOT EXISTS workflow_agents (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id   VARCHAR(36)   NOT NULL,
    role          VARCHAR(20)   NOT NULL,   -- MAIN | SUB | PEER
    name          VARCHAR(255)  NOT NULL,
    system_prompt TEXT,
    tools_json    TEXT,                     -- JSON array of enabled tool names
    skill_ids_json TEXT,                    -- JSON array of attached skill IDs
    order_index   INT           NOT NULL DEFAULT 0,
    pos_x         DOUBLE        NOT NULL DEFAULT 0,
    pos_y         DOUBLE        NOT NULL DEFAULT 0,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_wa_workflow (workflow_id),
    CONSTRAINT fk_wa_workflow FOREIGN KEY (workflow_id)
        REFERENCES workflows(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS workflow_runs (
    id               VARCHAR(36)  PRIMARY KEY,
    workflow_id      VARCHAR(36)  NOT NULL,
    owner_email      VARCHAR(255),
    user_input       TEXT         NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | RUNNING | DONE | FAILED
    sandbox_container VARCHAR(128),
    final_output     LONGTEXT,
    started_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at      TIMESTAMP,
    INDEX idx_wr_workflow (workflow_id),
    CONSTRAINT fk_wr_workflow FOREIGN KEY (workflow_id)
        REFERENCES workflows(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS workflow_run_logs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id      VARCHAR(36)   NOT NULL,
    agent_id    BIGINT,
    agent_name  VARCHAR(255),
    log_type    VARCHAR(30)   NOT NULL,  -- TOOL_CALL | TOOL_RESULT | LLM_RESPONSE | DELEGATION | ERROR | SYSTEM
    content     MEDIUMTEXT    NOT NULL,
    created_at  TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_wrl_run (run_id),
    CONSTRAINT fk_wrl_run FOREIGN KEY (run_id)
        REFERENCES workflow_runs(id) ON DELETE CASCADE
);

-- ── Skills (agent context documents) ────────────────────────────────────────
CREATE TABLE IF NOT EXISTS skills (
    id          VARCHAR(36)   PRIMARY KEY,
    owner_email VARCHAR(255),
    name        VARCHAR(255)  NOT NULL,
    file_name   VARCHAR(255),
    file_type   VARCHAR(16),
    size        BIGINT        NOT NULL DEFAULT 0,
    content     LONGTEXT      NOT NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_skill_owner (owner_email)
);

-- ── WebAuthn / Passkey ───────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS passkey_credentials (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    credential_id  VARCHAR(512) NOT NULL UNIQUE,
    public_key_cose TEXT        NOT NULL,
    sign_count     BIGINT       NOT NULL DEFAULT 0,
    user_handle    VARCHAR(512) NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pk_email       (email),
    INDEX idx_pk_user_handle (user_handle)
);

-- ── External connector OAuth tokens ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS connector_tokens (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_email   VARCHAR(255) NOT NULL,
    provider      VARCHAR(50)  NOT NULL,
    access_token  TEXT         NOT NULL,
    refresh_token TEXT,
    token_type    VARCHAR(50)  NOT NULL DEFAULT 'Bearer',
    scope         TEXT,
    expires_at    DATETIME,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_ct_email_provider (owner_email, provider)
);

CREATE TABLE IF NOT EXISTS connector_oauth_states (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    state       VARCHAR(64)  NOT NULL,
    owner_email VARCHAR(255),
    provider    VARCHAR(50)  NOT NULL,
    expires_at  DATETIME     NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_cos_state (state),
    INDEX idx_cos_state (state)
);

-- ── Model configurations ─────────────────────────────────────────────────────
-- Admin-managed list of selectable LLM models.
-- display_name is the primary key and user-facing identifier.
-- platform maps to a provider in LlmProperties (openai|anthropic|openrouter|local|deepseek).
-- model_id is the actual model string passed to the provider API.
CREATE TABLE IF NOT EXISTS model_configs (
    display_name VARCHAR(100) PRIMARY KEY,
    platform     VARCHAR(20)  NOT NULL,
    model_id     VARCHAR(200) NOT NULL,
    enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ── Schema migration: user model selection ────────────────────────────────────
ALTER TABLE user_preferences ADD COLUMN selected_model VARCHAR(100);

-- ── Schema migration: per-conversation and per-workflow model selection ────────
ALTER TABLE conversations ADD COLUMN selected_model VARCHAR(100);
ALTER TABLE workflows ADD COLUMN selected_model VARCHAR(100);

-- ── Schema migration: web_fetch_whitelist per-user isolation ─────────────────
-- Existing databases: drops the old global unique constraint and adds the per-user
-- one. On fresh installs these statements fail silently (continue-on-error=true).
ALTER TABLE web_fetch_whitelist DROP INDEX uq_wfw_domain;
ALTER TABLE web_fetch_whitelist ADD UNIQUE KEY uq_wfw_domain_user (domain, added_by);

-- ── Schema migration: conversation share modes & access control ───────────────
ALTER TABLE conversation_shares ADD COLUMN share_mode  VARCHAR(20) NOT NULL DEFAULT 'READ_ONLY';
ALTER TABLE conversation_shares ADD COLUMN access_type VARCHAR(20) NOT NULL DEFAULT 'EVERYONE';

-- Whitelist of allowed emails when access_type = WHITELIST
CREATE TABLE IF NOT EXISTS conversation_share_whitelist (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    share_id BIGINT       NOT NULL,
    email    VARCHAR(255) NOT NULL,
    UNIQUE KEY uq_csw_share_email (share_id, email),
    CONSTRAINT fk_csw_share FOREIGN KEY (share_id)
        REFERENCES conversation_shares(id) ON DELETE CASCADE
);

-- ── Scheduled messages (managed by Go scheduler service via Asynq + Redis) ────
CREATE TABLE IF NOT EXISTS scheduled_messages (
    id                 VARCHAR(36)  NOT NULL PRIMARY KEY,  -- UUID
    conversation_id    VARCHAR(36)  NOT NULL,
    owner_email        VARCHAR(255) NOT NULL,
    message            TEXT         NOT NULL,
    cron_expr          VARCHAR(100) NOT NULL,              -- e.g. "0 8 * * 1"
    timezone           VARCHAR(100) NOT NULL DEFAULT 'UTC',
    top_k              INT          NOT NULL DEFAULT 5,
    use_knowledge_base BOOLEAN      NOT NULL DEFAULT TRUE,
    use_web_fetch      BOOLEAN      NOT NULL DEFAULT TRUE,
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sched_conv  (conversation_id),
    INDEX idx_sched_email (owner_email),
    CONSTRAINT fk_sched_conv FOREIGN KEY (conversation_id)
        REFERENCES conversations(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS schedule_runs (
    id           VARCHAR(36)  NOT NULL PRIMARY KEY,  -- run UUID
    schedule_id  VARCHAR(36)  NOT NULL,
    status       VARCHAR(20)  NOT NULL,              -- RUNNING, COMPLETED, FAILED
    start_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    close_time   TIMESTAMP    NULL,
    INDEX idx_run_sched (schedule_id),
    CONSTRAINT fk_run_sched FOREIGN KEY (schedule_id)
        REFERENCES scheduled_messages(id) ON DELETE CASCADE
);

-- ── Schema migration: workflow scheduling support ─────────────────────────────
ALTER TABLE scheduled_messages MODIFY COLUMN conversation_id VARCHAR(36) NULL;
ALTER TABLE scheduled_messages ADD COLUMN workflow_id    VARCHAR(36) NULL;
ALTER TABLE scheduled_messages ADD COLUMN workflow_input TEXT        NULL;
ALTER TABLE scheduled_messages ADD INDEX  idx_sched_workflow (workflow_id);

-- ── Organizations & team members ─────────────────────────────────────────────
-- org_id is a human-readable slug (e.g. "google", "skyproton", "xxx-family").
-- Pre-created by admins; no auto-creation at login time.
CREATE TABLE IF NOT EXISTS organizations (
    org_id     VARCHAR(100) PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS org_members (
    org_id    VARCHAR(100) NOT NULL,
    email     VARCHAR(255) NOT NULL,
    role      VARCHAR(20)  NOT NULL DEFAULT 'MEMBER',  -- OWNER | MEMBER
    joined_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (org_id, email),
    CONSTRAINT fk_om_org FOREIGN KEY (org_id) REFERENCES organizations(org_id) ON DELETE CASCADE,
    INDEX idx_om_email (email)
);

-- ── Schema migration: team mode org_id on shared resources ────────────────────
-- NULL = personal mode; non-null = org-scoped (team mode).
ALTER TABLE knowledge_sources   ADD COLUMN org_id VARCHAR(100) NULL;
ALTER TABLE workflows           ADD COLUMN org_id VARCHAR(100) NULL;
ALTER TABLE web_fetch_whitelist ADD COLUMN org_id VARCHAR(100) NULL;
ALTER TABLE skills              ADD COLUMN org_id VARCHAR(100) NULL;

-- ── Schema migration: team approval workflow ───────────────────────────────────
-- PENDING = awaiting owner approval; APPROVED = active; REJECTED = denied.
-- Personal-mode items default to APPROVED (no approval needed).
ALTER TABLE knowledge_sources ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE skills             ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';

-- ── Schema migration: team mode org_id on private-in-team resources ───────────
-- Conversations, runs, and connector tokens are per-user but scoped to a context
-- (personal = NULL, team = org slug) so that the same email has separate data
-- in each mode.
ALTER TABLE conversations    ADD COLUMN org_id VARCHAR(100) NULL;
ALTER TABLE workflow_runs    ADD COLUMN org_id VARCHAR(100) NULL;
ALTER TABLE connector_tokens ADD COLUMN org_id VARCHAR(100) NULL;

-- ── Schema migration: connector OAuth state org scoping ─────────────────────
ALTER TABLE connector_oauth_states ADD COLUMN org_id VARCHAR(100) NULL;

-- ── CLI public keys ──────────────────────────────────────────────────────────
-- Stores one Ed25519 public key per user, registered by agent-cli at login.
-- Used by CliSignatureFilter to verify X-Cli-Signature on every CLI request.
CREATE TABLE IF NOT EXISTS cli_public_keys (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_email       VARCHAR(255) NOT NULL UNIQUE,
    public_key_base64 VARCHAR(64) NOT NULL,           -- Base64-encoded raw Ed25519 public key (44 chars)
    fingerprint      VARCHAR(8)   NOT NULL,            -- first 8 chars of Base64, shown in `auth status`
    registered_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at     TIMESTAMP    NULL,
    INDEX idx_cpk_email (user_email)
);

-- ── Schema migration: financial default currency preference ───────────────────
ALTER TABLE user_preferences ADD COLUMN default_currency VARCHAR(10) NOT NULL DEFAULT 'USD';

-- ── Financial portfolio tables ────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS financial_cash_deposits (
    id              VARCHAR(36)    PRIMARY KEY,
    owner_email     VARCHAR(255)   NOT NULL,
    platform        VARCHAR(255)   NOT NULL,
    platform_type   VARCHAR(100)   NOT NULL,
    country_region  VARCHAR(100),
    deposit_type    VARCHAR(10)    NOT NULL,   -- FIXED | FLEX
    currency        VARCHAR(10)    NOT NULL,
    amount          DECIMAL(19,4)  NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_fin_dep_owner (owner_email)
);

CREATE TABLE IF NOT EXISTS financial_stocks (
    id              VARCHAR(36)    PRIMARY KEY,
    owner_email     VARCHAR(255)   NOT NULL,
    broker          VARCHAR(255)   NOT NULL,
    stock_type      VARCHAR(20)    NOT NULL,   -- US_STOCK | HK_STOCK | CN_STOCK | SG_STOCK | OTHER
    symbol          VARCHAR(20)    NOT NULL,
    name            VARCHAR(255)   NOT NULL,
    stock_amount    DECIMAL(19,4)  NOT NULL,
    invest_amount   DECIMAL(19,4)  NOT NULL,
    currency        VARCHAR(10)    NOT NULL,
    fee             DECIMAL(19,4)  NOT NULL DEFAULT 0,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_fin_stk_owner (owner_email)
);

CREATE TABLE IF NOT EXISTS financial_crypto (
    id              VARCHAR(36)    PRIMARY KEY,
    owner_email     VARCHAR(255)   NOT NULL,
    name            VARCHAR(255)   NOT NULL,
    symbol          VARCHAR(30)    NOT NULL,
    amount          DECIMAL(28,8)  NOT NULL,
    invest_amount   DECIMAL(19,4)  NOT NULL,
    currency        VARCHAR(10)    NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_fin_cry_owner (owner_email)
);

CREATE TABLE IF NOT EXISTS financial_cards (
    id              VARCHAR(36)    PRIMARY KEY,
    owner_email     VARCHAR(255)   NOT NULL,
    bank            VARCHAR(255)   NOT NULL,
    country_region  VARCHAR(100),
    types           VARCHAR(50)    NOT NULL,   -- comma-separated: Credit,Debit,ATM
    card_name       VARCHAR(255)   NOT NULL,
    network         VARCHAR(20)    NOT NULL,   -- Mastercard | Visa | UnionPay | JCB | AMEX
    expire_date     VARCHAR(7),                -- YYYY-MM format, nullable
    credit_limit             DECIMAL(19,2),  -- nullable
    credit_limit_currency    VARCHAR(10),    -- nullable; currency of the credit limit
    shared_credit            TINYINT(1),    -- NULL=unknown, 1=shared pool, 0=dedicated
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_fin_card_owner (owner_email)
);

CREATE TABLE IF NOT EXISTS salary_usage_records (
    id                           VARCHAR(36)   PRIMARY KEY,
    owner_email                  VARCHAR(255)  NOT NULL,
    year                         INT           NOT NULL,
    month                        INT           NOT NULL,
    region                       VARCHAR(100)  NOT NULL,
    currency                     VARCHAR(10)   NOT NULL,
    salary                       DECIMAL(19,2) NOT NULL DEFAULT 0,
    bonus                        DECIMAL(19,2) NOT NULL DEFAULT 0,
    retirement_saving_employee   DECIMAL(19,2) NOT NULL DEFAULT 0,
    retirement_saving_employer   DECIMAL(19,2) NOT NULL DEFAULT 0,
    tax                          DECIMAL(19,2) NOT NULL DEFAULT 0,
    house_rent                   DECIMAL(19,2) NOT NULL DEFAULT 0,
    living_expense               DECIMAL(19,2) NOT NULL DEFAULT 0,
    other_expense                DECIMAL(19,2) NOT NULL DEFAULT 0,
    total_expense                DECIMAL(19,2) NOT NULL DEFAULT 0,
    created_at                   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sal_owner (owner_email),
    UNIQUE KEY uq_sal_owner_ym (owner_email, year, month)
);

-- ── Travel records ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS travel_records (
    id          VARCHAR(36)   PRIMARY KEY,
    owner_email VARCHAR(255)  NOT NULL,
    title       VARCHAR(255)  NOT NULL,
    start_date  VARCHAR(10)   NOT NULL,   -- YYYY-MM-DD
    end_date    VARCHAR(10)   NOT NULL,   -- YYYY-MM-DD
    stops_json  TEXT,                     -- JSON array of stops
    notes       TEXT,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_travel_owner (owner_email)
);

