ALTER TABLE users DROP CONSTRAINT nickname;

ALTER TABLE users ADD CONSTRAINT uk_server_nickname UNIQUE (server_name, nickname);