ALTER TABLE users
    MODIFY COLUMN password_hash VARCHAR(255) NULL COMMENT '암호화된 비밀번호 (소셜 로그인 회원은 NULL)';
