ALTER TABLE users ADD COLUMN grade VARCHAR(32) NULL;
ALTER TABLE user_info_outbox ADD COLUMN activity_grade VARCHAR(32) NULL;
ALTER TABLE user_verification_history
    ADD COLUMN experience_valid_from DATETIME(6) NULL,
    ADD COLUMN experience_baseline BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN valid_until DATETIME(6) NULL;
CREATE INDEX idx_verification_experience_interval
    ON user_verification_history(user_id, experience_valid_from, valid_until);

-- Baseline only: historical verification loss cannot be reconstructed safely.
INSERT INTO user_verification_history
    (user_id, server_name, character_name, verified_at, verification_success,
     created_at, experience_valid_from, experience_baseline)
SELECT id, server_name, nickname, UTC_TIMESTAMP(6), TRUE,
       UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), TRUE
FROM users WHERE is_verified = TRUE AND status = 'ACTIVE';
UPDATE users SET grade = 'APPRENTICE' WHERE is_verified = TRUE;
UPDATE user_info_outbox o JOIN users u ON u.id = o.user_id
SET o.activity_grade = u.grade, o.revision = o.revision + 1,
    o.next_attempt_at = UTC_TIMESTAMP(6), o.pending_since = UTC_TIMESTAMP(6);

CREATE TABLE experience_activity (
    code VARCHAR(32) NOT NULL,
    policy_version INT NOT NULL,
    name VARCHAR(80) NOT NULL,
    xp BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    rollout_at DATETIME(6) NULL,
    PRIMARY KEY (code, policy_version),
    CONSTRAINT chk_experience_policy_xp CHECK (xp >= 0)
);
INSERT INTO experience_activity(code, policy_version, name, xp) VALUES
 ('POST_PUBLISHED', 1, '게시글 작성', 20),
 ('COMMENT_CREATED', 1, '댓글 작성', 5),
 ('OUTFIT_PUBLISHED', 1, '의장 게시글 작성', 20),
 ('GUILD_POST_PUBLISHED', 1, '길드 게시글 작성', 20),
 ('GUILD_REGISTERED', 1, '길드 등록', 100),
 ('LIKE_GIVEN', 1, '좋아요 누르기', 1),
 ('LIKE_RECEIVED', 1, '좋아요 받기', 2);

CREATE TABLE user_experience (
    user_id BIGINT NOT NULL PRIMARY KEY,
    total_xp BIGINT NOT NULL DEFAULT 0,
    backfill_eligible BOOLEAN NOT NULL DEFAULT FALSE,
    last_backfill_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_experience_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT chk_experience_total CHECK (total_xp >= 0)
);
CREATE TABLE user_experience_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    activity_code VARCHAR(32) NOT NULL,
    policy_version INT NOT NULL,
    policy_xp BIGINT NOT NULL,
    activity_key VARCHAR(160) NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    source_revision BIGINT NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    root_target_type VARCHAR(32) NOT NULL,
    root_target_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL,
    delta_xp BIGINT NOT NULL,
    activity_balance BIGINT NOT NULL,
    total_xp BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_experience_history_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_experience_history_policy FOREIGN KEY (activity_code, policy_version)
        REFERENCES experience_activity(code, policy_version),
    UNIQUE KEY uk_experience_event (event_id, user_id, activity_code),
    UNIQUE KEY uk_experience_revision (user_id, activity_key, activity_code, source_revision),
    KEY idx_experience_history_cursor (user_id, id),
    CONSTRAINT chk_experience_balance CHECK (activity_balance >= 0 AND total_xp >= 0)
);
