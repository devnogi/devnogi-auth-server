ALTER TABLE users DROP INDEX nickname;

ALTER TABLE users ADD CONSTRAINT uk_server_nickname UNIQUE (server_name, nickname);