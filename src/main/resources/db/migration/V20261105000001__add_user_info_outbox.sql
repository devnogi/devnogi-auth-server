CREATE TABLE user_info_outbox (
    user_id BIGINT NOT NULL PRIMARY KEY,
    revision BIGINT NOT NULL,
    published_revision BIGINT NOT NULL DEFAULT 0,
    nickname VARCHAR(50) NOT NULL,
    profile_image_url VARCHAR(512) NULL,
    server_name VARCHAR(20) NULL,
    verified BOOLEAN NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    claim_token VARCHAR(36) NULL,
    claim_until DATETIME(6) NULL,
    pending_since DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_user_info_outbox_user
        FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_user_info_outbox_pending
    ON user_info_outbox (next_attempt_at, claim_until, published_revision, revision);
