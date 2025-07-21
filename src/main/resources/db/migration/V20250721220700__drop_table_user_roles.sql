DROP TABLE user_roles;

ALTER TABLE users
    ADD COLUMN role_id BIGINT NULL COMMENT '역할 ID',
    ADD CONSTRAINT
        FOREIGN KEY (role_id) REFERENCES roles (id);
