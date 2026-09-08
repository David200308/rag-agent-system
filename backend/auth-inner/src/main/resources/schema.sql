-- ── CLI public keys ──────────────────────────────────────────────────────────
-- Stores one Ed25519 public key per user, registered by agent-cli at login.
-- Verified via /internal/cli-signature/verify, called by agent-system-rest's
-- CliSignatureFilter on every CLI request.
CREATE TABLE IF NOT EXISTS cli_public_keys (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_uuid        VARCHAR(36)  NOT NULL UNIQUE,
    public_key_base64 VARCHAR(64) NOT NULL,           -- Base64-encoded raw Ed25519 public key (44 chars)
    fingerprint      VARCHAR(8)   NOT NULL,            -- first 8 chars of Base64, shown in `auth status`
    registered_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at     TIMESTAMP    NULL
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

-- NOTE: this module also reads/writes the `users`, `organizations`, and `org_members`
-- tables (via AuthUser/OrgOrganization/OrgMember), but does not create them — those
-- remain owned by agent-system-rest's schema.sql, which already runs first in the
-- shared database. No CREATE TABLE for them here.
