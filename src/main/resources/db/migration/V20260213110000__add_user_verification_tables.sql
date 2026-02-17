ALTER TABLE users
    ADD COLUMN server_name VARCHAR(20) NULL COMMENT '게임 서버명',
    ADD COLUMN is_verified BOOLEAN NOT NULL DEFAULT FALSE COMMENT '사용자 인증 상태',
    ADD COLUMN verified_at DATETIME NULL COMMENT '최근 인증 성공 시각';

CREATE TABLE user_verification_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '인증 토큰 ID',
    user_id BIGINT NOT NULL COMMENT '회원 ID',
    issued_at DATETIME NOT NULL COMMENT '발급 시각',
    expires_at DATETIME NOT NULL COMMENT '만료 시각',
    revoked BOOLEAN NOT NULL DEFAULT FALSE COMMENT '폐기 여부',
    revoked_at DATETIME NULL COMMENT '폐기 시각',
    token_value VARCHAR(64) NOT NULL UNIQUE COMMENT '발급값(메모노기_ 접두 제외)',
    verified BOOLEAN NOT NULL DEFAULT FALSE COMMENT '인증 성공 여부',
    verified_at DATETIME NULL COMMENT '인증 성공 시각',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
    CONSTRAINT fk_user_verification_token_user_id FOREIGN KEY (user_id) REFERENCES users(id)
) COMMENT='회원 인증 토큰 정보';

CREATE TABLE user_verification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '회원 인증 정보 ID',
    user_id BIGINT NOT NULL UNIQUE COMMENT '회원 ID',
    server_name VARCHAR(20) NULL COMMENT '서버명',
    character_name VARCHAR(100) NULL COMMENT '캐릭터명',
    last_verified_at DATETIME NULL COMMENT '최근 인증 일시',
    verification_count INT NOT NULL DEFAULT 0 COMMENT '누적 인증 횟수',
    latest_token_id BIGINT NULL COMMENT '최근 인증 토큰 ID',
    is_verified BOOLEAN NOT NULL DEFAULT FALSE COMMENT '현재 유효 인증 여부',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',
    CONSTRAINT fk_user_verification_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_verification_latest_token_id FOREIGN KEY (latest_token_id) REFERENCES user_verification_token(id)
) COMMENT='회원 인증 요약 정보';

CREATE TABLE user_verification_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '회원 인증 이력 ID',
    user_id BIGINT NULL COMMENT '회원 ID',
    server_name VARCHAR(20) NULL COMMENT '서버명',
    character_name VARCHAR(100) NULL COMMENT '캐릭터명',
    verified_at DATETIME NOT NULL COMMENT '검증 처리 시각',
    verification_success BOOLEAN NOT NULL COMMENT '인증 성공 여부',
    failure_reason VARCHAR(100) NULL COMMENT '실패 사유',
    token_id BIGINT NULL COMMENT '토큰 ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
    CONSTRAINT fk_user_verification_history_user_id FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_verification_history_token_id FOREIGN KEY (token_id) REFERENCES user_verification_token(id)
) COMMENT='회원 인증 시도 이력';

CREATE INDEX idx_user_verification_token_user_id ON user_verification_token(user_id);
CREATE INDEX idx_user_verification_token_expires_at ON user_verification_token(expires_at);
CREATE INDEX idx_user_verification_server_character_verified ON user_verification(server_name, character_name, is_verified);
CREATE INDEX idx_user_verification_history_user_id_verified_at ON user_verification_history(user_id, verified_at DESC);
