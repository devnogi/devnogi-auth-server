
ALTER TABLE users
    MODIFY COLUMN status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE' COMMENT '계정 상태',
    ADD COLUMN inactivated_at DATETIME COMMENT '계정 비활성화 일시',
    ADD COLUMN inactivated_type VARCHAR(255) COMMENT '계정 비활성화 타입 (탈퇴, 장기미접속, 불특정IP접속)';
