-- ── Auth schema ────────────────────────────────────────────────────────────────
-- Replaces the old email_whitelist table. New rows start at status=PRE_USER; an admin
-- manually flips status to USER (directly in the DB) to grant access.
CREATE TABLE IF NOT EXISTS users (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    uuid       VARCHAR(36)  NOT NULL UNIQUE,
    email      VARCHAR(255) NOT NULL UNIQUE,
    status     VARCHAR(20)  NOT NULL DEFAULT 'PRE_USER',  -- PRE_USER | USER
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ── Conversation history schema ───────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS conversations (
    id             VARCHAR(36)  PRIMARY KEY,          -- UUID
    user_uuid      VARCHAR(36),                        -- nullable; populated when auth is enabled
    archived       BOOLEAN      NOT NULL DEFAULT FALSE,
    selected_model VARCHAR(100),
    org_id         VARCHAR(100),                        -- NULL = personal mode; non-null = org-scoped (team mode)
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_conv_user_uuid (user_uuid)
);

-- ── Knowledge source index ────────────────────────────────────────────────────
-- Tracks every source that has been ingested into Weaviate.

CREATE TABLE IF NOT EXISTS knowledge_sources (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    source      VARCHAR(512) NOT NULL UNIQUE,   -- the "source" metadata value
    label       VARCHAR(512),                   -- human-friendly name (filename / URL title)
    category    VARCHAR(128),
    chunk_count INT          NOT NULL DEFAULT 0,
    owner_uuid  VARCHAR(36),                    -- uploader; NULL when auth is disabled
    org_id      VARCHAR(100),                   -- NULL = personal mode; non-null = org-scoped (team mode)
    status      VARCHAR(20)  NOT NULL DEFAULT 'APPROVED',  -- PENDING | APPROVED | REJECTED
    ingested_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_ks_source (source),
    INDEX idx_ks_owner  (owner_uuid)
);

-- Tracks which additional users a knowledge source has been shared with.
CREATE TABLE IF NOT EXISTS knowledge_source_shares (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_id    BIGINT       NOT NULL,
    shared_uuid  VARCHAR(36)  NOT NULL,          -- only registered users can be resolved onto this list
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_source_uuid (source_id, shared_uuid),
    CONSTRAINT fk_kss_source FOREIGN KEY (source_id)
        REFERENCES knowledge_sources(id) ON DELETE CASCADE
);

-- ── Conversation share links ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS conversation_shares (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(36)  NOT NULL,
    token           VARCHAR(36)  NOT NULL,
    owner_uuid      VARCHAR(36)  NOT NULL,
    share_mode      VARCHAR(20)  NOT NULL DEFAULT 'READ_ONLY',  -- READ_ONLY | INTERACTIVE
    access_type     VARCHAR(20)  NOT NULL DEFAULT 'EVERYONE',   -- EVERYONE | WHITELIST
    expires_at      DATETIME,                          -- NULL = never expires
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_share_token (token),
    INDEX idx_share_conv (conversation_id),
    CONSTRAINT fk_share_conv FOREIGN KEY (conversation_id)
        REFERENCES conversations(id) ON DELETE CASCADE
);

-- Whitelist of allowed uuids when access_type = WHITELIST
CREATE TABLE IF NOT EXISTS conversation_share_whitelist (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    share_id BIGINT       NOT NULL,
    uuid     VARCHAR(36)  NOT NULL,            -- only registered users can be resolved onto this list
    UNIQUE KEY uq_csw_share_uuid (share_id, uuid),
    CONSTRAINT fk_csw_share FOREIGN KEY (share_id)
        REFERENCES conversation_shares(id) ON DELETE CASCADE
);

-- ── Web-fetch domain whitelist ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_preferences (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_uuid        VARCHAR(36)  NOT NULL,
    timezone         VARCHAR(64)  NOT NULL DEFAULT 'UTC',
    selected_model   VARCHAR(100),
    default_currency VARCHAR(10)  NOT NULL DEFAULT 'USD',
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_up_user_uuid (user_uuid)
);

CREATE TABLE IF NOT EXISTS web_fetch_whitelist (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    domain        VARCHAR(253) NOT NULL,
    added_by_uuid VARCHAR(36),                  -- nullable when auth is disabled
    org_id        VARCHAR(100),                   -- NULL = personal mode; non-null = org-scoped (team mode)
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_wfw_domain_user (domain, added_by_uuid)
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
    owner_uuid     VARCHAR(36),               -- nullable when auth is disabled
    agent_pattern  VARCHAR(20)   NOT NULL,   -- ORCHESTRATOR | TEAM | GRAPH
    team_exec_mode VARCHAR(20),               -- PARALLEL | SEQUENTIAL (TEAM only)
    selected_model VARCHAR(100),
    org_id         VARCHAR(100),              -- NULL = personal mode; non-null = org-scoped (team mode)
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_wf_owner (owner_uuid)
);

CREATE TABLE IF NOT EXISTS workflow_agents (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id    VARCHAR(36)   NOT NULL,
    role           VARCHAR(20)   NOT NULL,   -- MAIN | SUB | PEER
    node_kind      VARCHAR(20)   NOT NULL DEFAULT 'AGENT',  -- AGENT | CONDITION | END (GRAPH pattern only)
    condition_expr TEXT,                     -- branch-selection instructions, CONDITION nodes only
    output_schema_json TEXT,                 -- optional JSON Schema (subset) the agent's final answer must satisfy
    name           VARCHAR(255)  NOT NULL,
    system_prompt  TEXT,
    tools_json     TEXT,                     -- JSON array of enabled tool names
    skill_ids_json TEXT,                    -- JSON array of attached skill IDs
    order_index    INT           NOT NULL DEFAULT 0,
    pos_x          DOUBLE        NOT NULL DEFAULT 0,
    pos_y          DOUBLE        NOT NULL DEFAULT 0,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_wa_workflow (workflow_id),
    CONSTRAINT fk_wa_workflow FOREIGN KEY (workflow_id)
        REFERENCES workflows(id) ON DELETE CASCADE
);

-- Explicit node-to-node connections for the GRAPH pattern. branch_label is set
-- only on edges leaving a CONDITION node (matches the label the run engine's
-- classifier is asked to choose between); NULL on plain agent → agent edges.
CREATE TABLE IF NOT EXISTS workflow_edges (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id     VARCHAR(36)   NOT NULL,
    source_node_id  BIGINT        NOT NULL,
    target_node_id  BIGINT        NOT NULL,
    branch_label    VARCHAR(100),
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_we_workflow (workflow_id),
    INDEX idx_we_source (source_node_id),
    CONSTRAINT fk_we_workflow FOREIGN KEY (workflow_id)
        REFERENCES workflows(id) ON DELETE CASCADE,
    CONSTRAINT fk_we_source FOREIGN KEY (source_node_id)
        REFERENCES workflow_agents(id) ON DELETE CASCADE,
    CONSTRAINT fk_we_target FOREIGN KEY (target_node_id)
        REFERENCES workflow_agents(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS workflow_runs (
    id                VARCHAR(36)  PRIMARY KEY,
    workflow_id       VARCHAR(36)  NOT NULL,
    owner_uuid        VARCHAR(36),                          -- nullable when auth is disabled
    org_id            VARCHAR(100),                          -- NULL = personal mode; non-null = org-scoped (team mode)
    user_input        TEXT         NOT NULL,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING | RUNNING | DONE | FAILED
    sandbox_container VARCHAR(128),
    final_output      LONGTEXT,
    workflow_version  INT,                                   -- workflow_versions.version_number active at run start; NULL if never saved
    started_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at       TIMESTAMP,
    INDEX idx_wr_workflow (workflow_id),
    CONSTRAINT fk_wr_workflow FOREIGN KEY (workflow_id)
        REFERENCES workflows(id) ON DELETE CASCADE
);

-- Named snapshots of a workflow's full config (pattern + agents + edges), saved
-- explicitly by the user. Restoring an old version creates a new version on top
-- rather than deleting history.
CREATE TABLE IF NOT EXISTS workflow_versions (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id    VARCHAR(36)  NOT NULL,
    version_number INT          NOT NULL,
    label          VARCHAR(255),
    snapshot_json  LONGTEXT     NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_wv_workflow (workflow_id),
    UNIQUE KEY uq_wv_workflow_version (workflow_id, version_number),
    CONSTRAINT fk_wv_workflow FOREIGN KEY (workflow_id)
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
-- Pure identity/ownership record — file content and per-upload metadata live
-- in skill_versions (content lives in object storage).
CREATE TABLE IF NOT EXISTS skills (
    id          VARCHAR(36)   PRIMARY KEY,
    owner_uuid  VARCHAR(36),                    -- nullable when auth is disabled
    name        VARCHAR(255)  NOT NULL,
    org_id      VARCHAR(100),                   -- NULL = personal mode; non-null = org-scoped (team mode)
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_skill_owner (owner_uuid)
);

-- Every upload (create or replace) is a new immutable version. "Latest" =
-- highest version_number; "active" (served to workflows) = highest
-- version_number with status='APPROVED'. Personal-mode versions are always
-- auto-APPROVED, so the two coincide there.
CREATE TABLE IF NOT EXISTS skill_versions (
    id               VARCHAR(36)  PRIMARY KEY,
    skill_id         VARCHAR(36)  NOT NULL,
    version_number   INT          NOT NULL,
    object_id        VARCHAR(36)  NOT NULL,   -- id returned by agent-system-storage-inner
    file_name        VARCHAR(255),
    file_type        VARCHAR(16),
    size_bytes       BIGINT       NOT NULL DEFAULT 0,
    status           VARCHAR(20)  NOT NULL DEFAULT 'APPROVED',  -- PENDING | APPROVED | REJECTED
    created_by_uuid  VARCHAR(36),                                -- nullable when auth is disabled
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_skill_versions_skill (skill_id, version_number),
    CONSTRAINT fk_skill_versions_skill FOREIGN KEY (skill_id)
        REFERENCES skills(id) ON DELETE CASCADE
);

-- ── WebAuthn / Passkey ───────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS passkey_credentials (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_uuid      VARCHAR(36)  NOT NULL,
    credential_id  VARCHAR(512) NOT NULL UNIQUE,
    public_key_cose TEXT        NOT NULL,
    sign_count     BIGINT       NOT NULL DEFAULT 0,
    user_handle    VARCHAR(512) NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pk_user_uuid   (user_uuid),
    INDEX idx_pk_user_handle (user_handle)
);

-- ── External connector OAuth tokens ─────────────────────────────────────────
CREATE TABLE IF NOT EXISTS connector_tokens (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    owner_uuid    VARCHAR(36)  NOT NULL,
    provider      VARCHAR(50)  NOT NULL,
    org_id        VARCHAR(100),                   -- NULL = personal mode; non-null = org-scoped (team mode)
    access_token  TEXT         NOT NULL,
    refresh_token TEXT,
    token_type    VARCHAR(50)  NOT NULL DEFAULT 'Bearer',
    scope         TEXT,
    expires_at    DATETIME,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_ct_uuid_provider (owner_uuid, provider)
);

CREATE TABLE IF NOT EXISTS connector_oauth_states (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    state       VARCHAR(64)  NOT NULL,
    owner_uuid  VARCHAR(36),                    -- nullable when auth is disabled
    provider    VARCHAR(50)  NOT NULL,
    org_id      VARCHAR(100),                   -- NULL = personal mode; non-null = org-scoped (team mode)
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
    user_uuid VARCHAR(36)  NOT NULL,
    email     VARCHAR(255),                    -- denormalized display copy; not part of the key
    role      VARCHAR(20)  NOT NULL DEFAULT 'MEMBER',  -- OWNER | MEMBER
    joined_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (org_id, user_uuid),
    CONSTRAINT fk_om_org FOREIGN KEY (org_id) REFERENCES organizations(org_id) ON DELETE CASCADE,
    INDEX idx_om_email (email)
);

-- ── CLI public keys ──────────────────────────────────────────────────────────
-- Stores one Ed25519 public key per user, registered by agent-cli at login.
-- Used by CliSignatureFilter to verify X-Cli-Signature on every CLI request.
CREATE TABLE IF NOT EXISTS cli_public_keys (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_uuid        VARCHAR(36)  NOT NULL UNIQUE,
    public_key_base64 VARCHAR(64) NOT NULL,           -- Base64-encoded raw Ed25519 public key (44 chars)
    fingerprint      VARCHAR(8)   NOT NULL,            -- first 8 chars of Base64, shown in `auth status`
    registered_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at     TIMESTAMP    NULL
);

-- ── Financial portfolio tables ────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS financial_cash_deposits (
    id              VARCHAR(36)    PRIMARY KEY,
    owner_uuid      VARCHAR(36)    NOT NULL,
    platform        VARCHAR(255)   NOT NULL,
    platform_type   VARCHAR(100)   NOT NULL,
    country_region  VARCHAR(100),
    deposit_type    VARCHAR(10)    NOT NULL,   -- FIXED | FLEX
    currency        VARCHAR(10)    NOT NULL,
    amount          DECIMAL(19,4)  NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_fin_dep_owner (owner_uuid)
);

CREATE TABLE IF NOT EXISTS financial_stocks (
    id              VARCHAR(36)    PRIMARY KEY,
    owner_uuid      VARCHAR(36)    NOT NULL,
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
    INDEX idx_fin_stk_owner (owner_uuid)
);

CREATE TABLE IF NOT EXISTS financial_crypto (
    id              VARCHAR(36)    PRIMARY KEY,
    owner_uuid      VARCHAR(36)    NOT NULL,
    name            VARCHAR(255)   NOT NULL,
    symbol          VARCHAR(30)    NOT NULL,
    amount          DECIMAL(28,8)  NOT NULL,
    invest_amount   DECIMAL(19,4)  NOT NULL,
    currency        VARCHAR(10)    NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_fin_cry_owner (owner_uuid)
);

CREATE TABLE IF NOT EXISTS financial_cards (
    id              VARCHAR(36)    PRIMARY KEY,
    owner_uuid      VARCHAR(36)    NOT NULL,
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
    INDEX idx_fin_card_owner (owner_uuid)
);

CREATE TABLE IF NOT EXISTS salary_usage_records (
    id                           VARCHAR(36)   PRIMARY KEY,
    owner_uuid                   VARCHAR(36)   NOT NULL,
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
    INDEX idx_sal_owner (owner_uuid),
    UNIQUE KEY uq_sal_owner_ym (owner_uuid, year, month)
);

-- ── Travel records ────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS travel_records (
    id            VARCHAR(36)   PRIMARY KEY,
    owner_uuid    VARCHAR(36)   NOT NULL,
    title         VARCHAR(255)  NOT NULL,
    start_date    VARCHAR(10)   NOT NULL,   -- YYYY-MM-DD
    end_date      VARCHAR(10)   NOT NULL,   -- YYYY-MM-DD
    stops_json    TEXT,                     -- JSON array of stops
    expenses_json TEXT,                     -- JSON array of expenses
    notes         TEXT,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_travel_owner (owner_uuid)
);

-- ── Scheduled messages (managed by Go scheduler service via Asynq + Redis) ────
CREATE TABLE IF NOT EXISTS scheduled_messages (
    id                 VARCHAR(36)  NOT NULL PRIMARY KEY,  -- UUID
    conversation_id    VARCHAR(36)  NULL,                  -- NULL when workflow_id is set instead
    workflow_id        VARCHAR(36)  NULL,
    workflow_input     TEXT         NULL,
    owner_uuid         VARCHAR(36)  NOT NULL,
    message            TEXT         NOT NULL,
    cron_expr          VARCHAR(100) NOT NULL,              -- e.g. "0 8 * * 1"
    timezone           VARCHAR(100) NOT NULL DEFAULT 'UTC',
    top_k              INT          NOT NULL DEFAULT 5,
    use_knowledge_base BOOLEAN      NOT NULL DEFAULT TRUE,
    use_web_fetch      BOOLEAN      NOT NULL DEFAULT TRUE,
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sched_conv     (conversation_id),
    INDEX idx_sched_owner    (owner_uuid),
    INDEX idx_sched_workflow (workflow_id),
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

-- ── Investment alert rules (managed by Go investment-alert-task service) ──────
-- Price/token alerts (crypto or stock) — fetched via Pyth Hermes (crypto and
-- equity feed IDs share the same API shape, e.g. "BTC/USD" vs "Equity.US.QQQ/USD").
CREATE TABLE IF NOT EXISTS alert_rule_token_config (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY,  -- UUID
    owner_uuid       VARCHAR(36)  NOT NULL,
    org_id           VARCHAR(36)  NULL,
    symbol           VARCHAR(64)  NOT NULL,
    price_feed_id    VARCHAR(128) NOT NULL,
    asset_type       ENUM('CRYPTO','STOCK') NOT NULL DEFAULT 'CRYPTO',
    threshold        DOUBLE       NOT NULL,
    direction        VARCHAR(8)   NOT NULL,              -- >=, >, =, <=, <
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    frequency        JSON         NULL,                  -- {"number":..,"unit":"DAY|HOUR|ONCE|NEVER"}
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_alert_token_owner (owner_uuid)
);

-- DeFi protocol alerts (Aave/Morpho/Kamino/Pendle/Hyperliquid TVL/APY/etc.)
CREATE TABLE IF NOT EXISTS alert_rule_defi_config (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY,  -- UUID
    owner_uuid       VARCHAR(36)  NOT NULL,
    org_id           VARCHAR(36)  NULL,
    protocol         VARCHAR(64)  NOT NULL,
    version          VARCHAR(32)  NOT NULL,
    chain_id         VARCHAR(32)  NOT NULL,
    params           JSON         NULL,                  -- protocol-specific fields (see config.DeFiAlertRuleParams)
    field            VARCHAR(64)  NOT NULL,               -- TVL, APY, UTILIZATION, LIQUIDITY
    threshold        DOUBLE       NOT NULL,
    direction        VARCHAR(8)   NOT NULL,
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    frequency        JSON         NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_alert_defi_owner (owner_uuid)
);

-- Prediction market alerts (e.g. Polymarket CLOB midpoint)
CREATE TABLE IF NOT EXISTS alert_rule_predict_market_config (
    id               VARCHAR(36)  NOT NULL PRIMARY KEY,  -- UUID
    owner_uuid       VARCHAR(36)  NOT NULL,
    org_id           VARCHAR(36)  NULL,
    predict_market   VARCHAR(64)  NOT NULL,
    params           JSON         NULL,                  -- negRisk, question_id, condition_id, outcome, token_id
    field            VARCHAR(64)  NOT NULL,               -- MIDPOINT
    threshold        DOUBLE       NOT NULL,
    direction        VARCHAR(8)   NOT NULL,
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    frequency        JSON         NULL,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_alert_predict_owner (owner_uuid)
);
