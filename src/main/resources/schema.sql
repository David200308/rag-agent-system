-- ── Auth schema ────────────────────────────────────────────────────────────────
-- JWT is used for sessions (stateless) so only whitelist + OTP need storage.
-- Idempotent: safe to run on every startup.

CREATE TABLE IF NOT EXISTS email_whitelist (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    email      VARCHAR(255) NOT NULL UNIQUE,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS otp_codes (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    code       VARCHAR(6)   NOT NULL,
    expires_at DATETIME     NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_otp_email (email)
);

-- ── Conversation history schema ───────────────────────────────────────────────
-- Persists multi-turn conversation sessions and their messages.

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
    UNIQUE KEY uq_wfw_domain (domain)
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
    final_output     TEXT,
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
    content     TEXT          NOT NULL,
    created_at  TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_wrl_run (run_id),
    CONSTRAINT fk_wrl_run FOREIGN KEY (run_id)
        REFERENCES workflow_runs(id) ON DELETE CASCADE
);

-- ── Scheduled messages (managed by Go scheduler service) ─────────────────────
CREATE TABLE IF NOT EXISTS scheduled_messages (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id    VARCHAR(36)  NOT NULL,
    owner_email        VARCHAR(255) NOT NULL,
    message            TEXT         NOT NULL,
    cron_expr          VARCHAR(100) NOT NULL,        -- e.g. "0 8 * * 1"
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
