CREATE TABLE oauth_exchange_flows (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  code_hash CHAR(64) NOT NULL,
  proof_hash CHAR(64) NULL,
  binding_hash CHAR(64) NOT NULL,
  flow_type VARCHAR(16) NOT NULL,
  provider VARCHAR(32) NULL,
  provider_user_id VARCHAR(255) NULL,
  email VARCHAR(255) NULL,
  code_expires_at_ms BIGINT NOT NULL,
  code_consumed_at_ms BIGINT NULL,
  proof_expires_at_ms BIGINT NULL,
  proof_consumed_at_ms BIGINT NULL,
  created_at_ms BIGINT NOT NULL,
  CONSTRAINT uq_oauth_exchange_code UNIQUE (code_hash),
  CONSTRAINT uq_oauth_exchange_proof UNIQUE (proof_hash),
  INDEX idx_oauth_exchange_expiry (code_expires_at_ms, proof_expires_at_ms)
);
