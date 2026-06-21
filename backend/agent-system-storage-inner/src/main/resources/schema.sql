CREATE TABLE IF NOT EXISTS stored_objects (
    id           VARCHAR(36)  PRIMARY KEY,          -- UUID
    object_key   VARCHAR(512) NOT NULL,              -- Garage bucket key
    owner_email  VARCHAR(255) NOT NULL,
    entity_type  VARCHAR(64)  NOT NULL,               -- e.g. TRAVEL_RECORD, AVATAR, SKILL, GENERIC
    entity_id    VARCHAR(64),
    content_type VARCHAR(128) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_stored_objects_entity (entity_type, entity_id, owner_email)
);
