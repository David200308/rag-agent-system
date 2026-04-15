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
