-- 사용자 테이블
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '사용자 고유 ID',
    email VARCHAR(255) NOT NULL UNIQUE COMMENT '사용자 이메일 (로그인 ID)',
    password_hash VARCHAR(255) NOT NULL COMMENT '암호화된 비밀번호',
    nickname VARCHAR(50) NOT NULL UNIQUE COMMENT '사용자 닉네임 (커뮤니티 표시용)',
    profile_image_url VARCHAR(512) COMMENT '프로필 이미지 URL',
    status ENUM('ACTIVE', 'INACTIVE', 'BANNED') DEFAULT 'ACTIVE' COMMENT '계정 상태',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '계정 생성일',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '계정 정보 수정일',
    last_login_at DATETIME COMMENT '마지막 로그인 시각'
) COMMENT='회원 계정 기본 정보';

-- 역할 테이블
CREATE TABLE roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '역할 ID',
    name VARCHAR(50) NOT NULL UNIQUE COMMENT '역할 이름 (예: USER, ADMIN)',
    description VARCHAR(255) COMMENT '역할 설명'
) COMMENT='권한 역할 정의 테이블';

-- 사용자-역할 매핑 테이블
CREATE TABLE user_roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '사용자 역할 ID',
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    role_id BIGINT NOT NULL COMMENT '역할 ID',
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (role_id) REFERENCES roles(id)
) COMMENT='사용자와 역할 간 매핑 테이블 (다대다 관계)';

-- 로그인 이력 테이블
CREATE TABLE login_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '로그인 이력 ID',
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    login_ip VARCHAR(45) COMMENT '로그인 시도한 IP 주소',
    user_agent TEXT COMMENT '접속한 브라우저/OS 정보',
    success BOOLEAN COMMENT '로그인 성공 여부',
    reason VARCHAR(255) COMMENT '실패 사유 (예: wrong_password, locked_account)',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '로그인 시도 시각',
    FOREIGN KEY (user_id) REFERENCES users(id)
) COMMENT='로그인 성공 및 실패 이력 기록';

-- 비밀번호 변경 이력
CREATE TABLE password_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '비밀번호 이력 ID',
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    previous_password_hash VARCHAR(255) NOT NULL COMMENT '이전에 사용했던 암호화된 비밀번호',
    changed_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '비밀번호 변경 시각',
    FOREIGN KEY (user_id) REFERENCES users(id)
) COMMENT='비밀번호 변경 이력 (재사용 방지 목적)';

-- 계정 잠금 정보
CREATE TABLE account_locks (
    user_id BIGINT PRIMARY KEY COMMENT '사용자 ID',
    failed_attempts INT DEFAULT 0 COMMENT '실패 시도 횟수',
    locked_until DATETIME COMMENT '잠금 해제 예정 시각',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '최근 업데이트 시각',
    FOREIGN KEY (user_id) REFERENCES users(id)
) COMMENT='계정 잠금 상태 관리 (ex. 로그인 실패 누적)';

-- Refresh Token 저장 테이블
CREATE TABLE refresh_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '토큰 ID',
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    token VARCHAR(512) NOT NULL COMMENT 'JWT Refresh Token',
    expires_at DATETIME NOT NULL COMMENT '만료 시각',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
    revoked BOOLEAN DEFAULT FALSE COMMENT '토큰 사용 철회 여부',
    FOREIGN KEY (user_id) REFERENCES users(id)
) COMMENT='Refresh Token 저장소';

-- 이메일 인증 및 비밀번호 초기화 토큰
CREATE TABLE email_verification_tokens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '토큰 ID',
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    token VARCHAR(255) NOT NULL COMMENT '이메일 인증 또는 초기화용 토큰',
    type ENUM('EMAIL_VERIFICATION', 'PASSWORD_RESET') NOT NULL COMMENT '토큰 종류',
    expires_at DATETIME NOT NULL COMMENT '만료 시각',
    used BOOLEAN DEFAULT FALSE COMMENT '사용 여부',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
    FOREIGN KEY (user_id) REFERENCES users(id)
) COMMENT='이메일 인증 및 비밀번호 초기화 토큰 저장소';

-- 소셜 로그인 사용자 정보
CREATE TABLE oauth_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '소셜 연동 ID',
    user_id BIGINT NOT NULL COMMENT '사용자 ID',
    provider VARCHAR(50) NOT NULL COMMENT '소셜 제공자 (google, kakao 등)',
    provider_user_id VARCHAR(255) NOT NULL COMMENT '제공자에서의 사용자 고유 ID',
    linked_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '연동된 시각',
    FOREIGN KEY (user_id) REFERENCES users(id),
    UNIQUE(provider, provider_user_id)
) COMMENT='소셜 로그인 연동 정보';

CREATE INDEX idx_login_user_id ON login_history(user_id);
CREATE INDEX idx_refresh_token_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_verification_token ON email_verification_tokens(token);
