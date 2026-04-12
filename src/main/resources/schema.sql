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
